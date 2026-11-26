@file:Suppress("DEPRECATION")
package com.cobalt.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.cobalt.app.ui.common.CouiConfirmDialog
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("local", 0) }
    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private var stopBusy by mutableStateOf(false)
    private var refresh by mutableIntStateOf(0)
    private var pendingImport by mutableStateOf<JSONObject?>(null)
    private var pendingImportCount by mutableIntStateOf(0)
    private var selectionMode by mutableStateOf(false)
    private var initialOriginal by mutableStateOf("")
    private var initialTranslation by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val original = intent?.getStringExtra("original")
        if (original != null) {
            selectionMode = true
            initialOriginal = original
            initialTranslation = intent?.getStringExtra("translated") ?: ""
        }
        setContent {
            val appearance = remember { getSharedPreferences("appearance", 0) }
            DesignTheme(
                this,
                appearance.getString("theme_mode", "system") ?: "system",
                appearance.getString("ui_size", "normal") ?: "normal",
            ) { Editor() }
        }
    }

    @Composable
    private fun Editor() {
        val c = LocalDesign.current
        val context = this
        var original by rememberSaveable { mutableStateOf(initialOriginal) }
        var translation by rememberSaveable { mutableStateOf(initialTranslation) }
        var picker by remember { mutableStateOf(prefs.getBoolean("picker", false)) }
        var autoHide by remember { mutableStateOf(prefs.getBoolean("picker_hide", false)) }
        var showList by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<String?>(null) }
        val count = remember(refresh) { DictionaryStore.count(context) }

        Column(
            Modifier.fillMaxSize().background(c.background).safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SoftSpace.gutter)
                .padding(top = 12.dp, bottom = 32.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SoftIconButton(onClick = { finish(); overridePendingTransition(R.anim.cobalt_enter_left, R.anim.cobalt_exit_right) })
            }
            Spacer(Modifier.height(18.dp))
            SoftPageTitle(
                title = if (selectionMode) "Перевод надписи" else "Словарь",
                subtitle = if (selectionMode) "Измените запись и сохраните" else "Личный словарь переводов",
            )

            SoftSectionTitle(if (selectionMode) "Надпись" else "Новый перевод")
            SoftCard {
                SoftField(original, { original = it }, "Оригинал", 2)
                Spacer(Modifier.height(12.dp))
                SoftField(translation, { translation = it }, "Перевод", 2)
                Spacer(Modifier.height(14.dp))
                SoftAction(
                    text = if (stopBusy) "Остановка Theme Store…" else "Сохранить",
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !stopBusy,
                    onClick = {
                        when {
                            original.isBlank() || translation.isBlank() -> message("Заполните оба поля")
                            original.length > DictionaryStore.MAX_FIELD || translation.length > DictionaryStore.MAX_FIELD ->
                                message("Не более ${DictionaryStore.MAX_FIELD} символов в каждом поле")
                            DictionaryStore.put(context, original, translation) -> {
                                refresh++
                                if (!selectionMode) { original = ""; translation = "" }
                                stopAfterSave()
                            }
                            else -> message("Не удалось сохранить: словарь переполнен")
                        }
                    },
                )
            }
            SoftNote("При сохранении Cobalt попытается остановить Theme Store через root. После остановки откройте его снова.")

            if (!selectionMode) {
                SoftSectionTitle("Мои переводы")
                SoftGroup {
                    SoftRow("Открыть список", "Записей: $count", onClick = { showList = true })
                    SoftDivider()
                    SoftRow("Импорт JSON", "Совпадающие записи заменяются", onClick = { pickImport() })
                    SoftDivider()
                    SoftRow("Экспорт JSON", "Сохранить резервную копию", onClick = { pickExport() })
                }

                SoftSectionTitle("Выбор надписей")
                SoftGroup {
                    SoftToggleRow("Кнопка в Theme Store", "Показывать кнопку выбора надписи", picker) {
                        picker = it
                        prefs.edit { putBoolean("picker", it) }
                        message("Вернитесь в Theme Store, чтобы применить изменение")
                    }
                    if (picker) {
                        SoftDivider()
                        SoftToggleRow("Скрывать у края", "Сворачивать кнопку после бездействия", autoHide) {
                            autoHide = it
                            prefs.edit { putBoolean("picker_hide", it) }
                            message("Вернитесь в Theme Store, чтобы применить изменение")
                        }
                    }
                }
                if (picker) {
                    val seen = prefs.getLong("picker_last_seen", 0L)
                    SoftNote(
                        if (seen > 0L)
                            "Последний раз кнопка работала ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(seen))} в ${prefs.getString("picker_runtime_version", "неизвестной версии")}. Экран: ${prefs.getString("picker_last_activity", "неизвестно")}."
                        else
                            "Кнопка ещё не обнаружена в Theme Store. Проверьте область действия модуля в LSPosed.",
                    )
                }

                SoftNote("Пользовательские переводы применяются при включённом русском шаблоне и имеют приоритет над встроенными.")
            }
        }

        pendingImport?.let { pending ->
            CouiConfirmDialog(
                show = true,
                title = "Импорт словаря",
                summary = "Записей в файле: $pendingImportCount. Совпадающие записи будут заменены.",
                negativeText = "Отмена",
                positiveText = "Импортировать",
                onDismissRequest = { pendingImport = null },
                onPositive = {
                    if (DictionaryStore.save(context, pending)) {
                        refresh++
                        message("Импортировано: $pendingImportCount")
                    } else message("Не удалось сохранить словарь")
                    pendingImport = null
                },
            )
        }

        if (showList) {
            val entries = remember(refresh) { DictionaryStore.entries(context) }
            CouiConfirmDialog(
                show = true,
                title = "Записей: ${entries.size}",
                summary = null,
                negativeText = "",
                positiveText = "Закрыть",
                onDismissRequest = { showList = false },
                onPositive = { showList = false },
                content = {
                    if (entries.isEmpty()) Text("Словарь пуст", color = c.muted)
                    else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(entries, key = { it.first }) { entry ->
                            Column(
                                Modifier.fillMaxWidth().clickable { selected = entry.first; showList = false }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(entry.first, color = c.text)
                                Text(entry.second, color = c.muted, fontSize = 13.sp)
                            }
                        }
                    }
                },
            )
        }

        selected?.let { key ->
            CouiConfirmDialog(
                show = true,
                title = key,
                summary = DictionaryStore.read(context).optString(key),
                negativeText = "Удалить",
                positiveText = "Изменить",
                onDismissRequest = { selected = null },
                onPositive = {
                    original = key
                    translation = DictionaryStore.read(context).optString(key)
                    selected = null
                },
                onNegative = {
                    if (DictionaryStore.remove(context, key)) { refresh++; message("Удалено") }
                    else message("Ошибка сохранения")
                    selected = null
                },
            )
        }
    }

    private fun pickImport() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), 11,
        )
    }

    private fun pickExport() {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, "cobalt-dictionary.json"), 12,
        )
    }

    private fun stopAfterSave() {
        stopBusy = true
        lifecycleScope.launch {
            val command = runCatching { RestartCommands.forceStop(ThemeStoreProfile.packageName) }.getOrNull()
            val error = if (command == null) "Остановка недоступна" else requestRoot(command)
            stopBusy = false
            if (error == null) message("Сохранено. Theme Store остановлен, откройте его снова.")
            else message("Сохранено, но остановить Theme Store не удалось. Остановите приложение вручную и откройте снова.")
            if (selectionMode) { finish(); overridePendingTransition(R.anim.cobalt_enter_left, R.anim.cobalt_exit_right) }
        }
    }

    @Deprecated("Legacy result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        val uri = result?.data ?: return
        if (resultCode != Activity.RESULT_OK) return
        try {
            when (requestCode) {
                12 -> {
                    contentResolver.openOutputStream(uri, "wt")!!.use {
                        it.write(DictionaryStore.export(this).toByteArray(Charsets.UTF_8))
                    }
                    message("Словарь сохранён в файл")
                }
                11 -> {
                    val bytes = DictionaryStore.readAll(contentResolver.openInputStream(uri)!!)
                    DictionaryStore.merge(this, bytes)
                        .onSuccess { pair ->
                            pendingImportCount = pair.second
                            pendingImport = pair.first
                        }
                        .onFailure { message("Ошибка файла: ${it.message}") }
                }
            }
        } catch (e: Exception) {
            message("Ошибка файла: ${e.message}")
        }
    }
}
