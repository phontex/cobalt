@file:Suppress("DEPRECATION")
package com.cobalt.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val LOG_ROOT = "/data/adb/cobalt-platform"

class LogsActivity : ComponentActivity() {
    private val diagnostics by lazy { getSharedPreferences("diagnostics", MODE_PRIVATE) }
    private var logText by mutableStateOf("Чтение журнала…")
    private var loading by mutableStateOf(true)
    private var clearing by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance = remember { getSharedPreferences("appearance", 0) }
            DesignTheme(
                activity = this,
                mode = appearance.getString("theme_mode", "system") ?: "system",
                size = appearance.getString("ui_size", "normal") ?: "normal",
                systemAmoled = appearance.getBoolean("system_amoled", false),
                colorMode = appearance.getString("color_mode", "app") ?: "app",
            ) { LogsScreen() }
        }
        refreshLogs()
    }

    private fun refreshLogs() {
        loading = true
        lifecycleScope.launch {
            logText = readRootLogs()
            loading = false
        }
    }

    private suspend fun readRootLogs(): String = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", "cat $LOG_ROOT/logs/platform.log 2>/dev/null || true")
                .redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            if (text.isBlank()) "Журнал пока пуст. Cobalt Platform ещё не записал событий." else text.takeLast(200_000)
        }.getOrElse { "Не удалось получить журнал Cobalt Platform. Проверьте root-доступ." }
    }

    private fun runRoot(command: String): String? = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            "Время ожидания root-доступа истекло."
        } else if (process.exitValue() != 0) output.ifBlank { "Команда завершилась с ошибкой." } else null
    }.getOrElse { "Root-доступ недоступен." }

    private fun syncSettings() {
        val keep = diagnostics.getBoolean("keep_logs", true)
        val days = when (diagnostics.getString("retention", "7d")) {
            "3d" -> 3
            "30d" -> 30
            "forever" -> 0
            else -> 7
        }
        val auto = diagnostics.getBoolean("auto_backup", false)
        val content = "keep_logs=$keep\nretention_days=$days\nauto_backup=$auto\n"
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runRoot("mkdir -p $LOG_ROOT; echo $encoded | base64 -d > $LOG_ROOT/config.conf")
            }
            if (error != null) Toast.makeText(this@LogsActivity, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun shareLogs() {
        val report = "Cobalt Platform — журнал\nДата: ${DateFormat.getDateTimeInstance().format(Date())}\n\n$logText"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Cobalt Platform — журнал")
                    putExtra(Intent.EXTRA_TEXT, report)
                },
                "Поделиться журналом",
            ),
        )
    }

    private fun saveLogs() {
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "cobalt-platform-log.txt"),
            42,
        )
    }

    @Deprecated("Legacy result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK && data?.data != null) runCatching {
            contentResolver.openOutputStream(data.data!!, "wt")!!.use { it.write(logText.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "Журнал сохранён", Toast.LENGTH_LONG).show()
        }.onFailure { Toast.makeText(this, "Не удалось сохранить журнал", Toast.LENGTH_LONG).show() }
    }

    private fun clearLog() {
        if (clearing) return
        clearing = true
        notice = null
        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) { runRoot("rm -f $LOG_ROOT/logs/platform.log") }
            clearing = false
            if (error == null) {
                logText = "Журнал очищен."
                notice = "Журнал очищен"
                Toast.makeText(this@LogsActivity, "Журнал очищен", Toast.LENGTH_SHORT).show()
            } else {
                notice = error
                Toast.makeText(this@LogsActivity, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    @Composable
    private fun LogsScreen() {
        val c = LocalDesign.current
        val keepLogs = remember { mutableStateOf(diagnostics.getBoolean("keep_logs", true)) }
        val retention = remember { mutableStateOf(diagnostics.getString("retention", "7d") ?: "7d") }
        val autoBackup = remember { mutableStateOf(diagnostics.getBoolean("auto_backup", false)) }

        Box(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BookSheet(modifier = Modifier.fillMaxSize()) {
                BookRunningHeader(
                    chapter = "Диагностика · Cobalt Platform",
                    pageNumber = 1,
                    totalPages = 1,
                    onBack = { finish(); overridePendingTransition(R.anim.cobalt_enter_left, R.anim.cobalt_exit_right) },
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 24.dp),
                ) {
                    SoftPageTitle(
                        title = "Диагностика",
                        subtitle = "События Cobalt Platform",
                    )

            SoftSectionTitle("Журнал")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (loading) c.faint else c.success))
                    AnimatedContent(
                        targetState = when {
                            clearing -> "Очистка журнала…"
                            loading -> "Чтение…"
                            else -> "platform.log"
                        },
                        label = "logStatus",
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    ) { label ->
                        Text(label, style = CobaltType.bodyStrong, color = c.text, maxLines = 1)
                    }
                    SoftTextAction(if (loading) "Чтение…" else "Обновить", enabled = !loading && !clearing) { refreshLogs() }
                }
                AnimatedVisibility(
                    visible = notice != null,
                    enter = fadeIn(tween(160)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top),
                ) {
                    val ok = notice == "Журнал очищен"
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(SoftShape.inner)
                            .background((if (ok) c.success else c.danger).copy(alpha = if (c.dark) .16f else .10f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (ok) c.success else c.danger))
                        Text(
                            notice ?: "",
                            style = CobaltType.micro,
                            color = if (ok) c.success else c.danger,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 132.dp, max = 280.dp)
                        .clip(SoftShape.inner).background(c.well),
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(
                            logText,
                            style = CobaltType.mono,
                            color = if (loading || logText.startsWith("Журнал") || logText.startsWith("Не удалось")) c.muted else c.text,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftAction("Поделиться", primary = true, modifier = Modifier.weight(1f)) { shareLogs() }
                    SoftAction("Сохранить", primary = false, modifier = Modifier.weight(1f)) { saveLogs() }
                }
            }

            SoftSectionTitle("Хранение")
            SoftGroup {
                SoftToggleRow("Сохранять журналы", "Ведение журнала Cobalt Platform", keepLogs.value) {
                    keepLogs.value = it
                    diagnostics.edit { putBoolean("keep_logs", it) }
                    syncSettings()
                }
                SoftDivider(start = 0.dp, end = 0.dp)
                Column(Modifier.padding(SoftSpace.card)) {
                    Text("Срок хранения", style = CobaltType.bodyStrong, color = c.text)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3d" to "3 дня", "7d" to "7 дней", "30d" to "30 дней", "forever" to "Всегда").forEach { (key, label) ->
                            SoftChip(label, retention.value == key, Modifier.weight(1f)) {
                                retention.value = key
                                diagnostics.edit { putString("retention", key) }
                                syncSettings()
                            }
                        }
                    }
                }
                SoftDivider(start = 0.dp, end = 0.dp)
                SoftToggleRow("Резервная копия", "Создавать копию журнала", autoBackup.value) {
                    autoBackup.value = it
                    diagnostics.edit { putBoolean("auto_backup", it) }
                    syncSettings()
                }
            }
            SoftNote("Копия создаётся только по вашему действию. Общая папка Download не используется автоматически.")

            SoftSectionTitle("Очистка")
            SoftGroup {
                SoftRow("Очистить журнал", "Удалить platform.log", showChevron = false, onClick = { clearLog() })
            }
                }
            }
        }
    }
}
