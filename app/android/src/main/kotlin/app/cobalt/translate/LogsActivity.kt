package app.cobalt.translate

import androidx.core.content.edit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cobalt.ui.common.ColorOs17Button
import app.cobalt.translate.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

private const val LOG_ROOT = "/data/adb/cobalt-platform"

class LogsActivity : ComponentActivity() {
    private val diagnostics by lazy { getSharedPreferences("diagnostics", MODE_PRIVATE) }
    private var logText by mutableStateOf("Загрузка журнала…")
    private var loading by mutableStateOf(true)
    private var saveRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appearance = remember { getSharedPreferences("appearance", 0) }
            DesignTheme(this, appearance.getString("theme_mode", "system") ?: "system", appearance.getString("ui_size", "normal") ?: "normal") { LogsScreen() }
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

    private fun runRoot(command: String): String? {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); "Время ожидания root-доступа истекло." }
            else if (process.exitValue() != 0) output.ifBlank { "Команда завершилась с ошибкой." } else null
        }.getOrElse { "Root-доступ недоступен." }
    }

    private fun syncSettings() {
        val keep = diagnostics.getBoolean("keep_logs", true)
        val days = when (diagnostics.getString("retention", "7d")) { "3d" -> 3; "30d" -> 30; "forever" -> 0; else -> 7 }
        val auto = diagnostics.getBoolean("auto_backup", false)
        val content = "keep_logs=$keep\nretention_days=$days\nauto_backup=$auto\n"
        val encoded = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
        val error = runRoot("mkdir -p $LOG_ROOT; echo $encoded | base64 -d > $LOG_ROOT/config.conf")
        if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
    }

    private fun shareLogs() {
        val report = "Cobalt Platform — журнал\nДата: ${DateFormat.getDateTimeInstance().format(Date())}\n\n$logText"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Cobalt Platform — журнал"); putExtra(Intent.EXTRA_TEXT, report) }, "Поделиться журналом"))
    }

    private fun saveLogs() {
        saveRequest = true
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").putExtra(Intent.EXTRA_TITLE, "cobalt-platform-log.txt"), 42)
    }

    @Deprecated("Legacy result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK && data?.data != null) runCatching {
            contentResolver.openOutputStream(data.data!!, "wt")!!.use { it.write(logText.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "Журнал сохранён", Toast.LENGTH_LONG).show()
        }.onFailure { Toast.makeText(this, "Не удалось сохранить журнал", Toast.LENGTH_LONG).show() }
    }

    @Composable private fun LogsScreen() {
        val c = LocalDesign.current
        val retention = remember { mutableStateOf(diagnostics.getString("retention", "7d") ?: "7d") }
        val keepLogs = remember { mutableStateOf(diagnostics.getBoolean("keep_logs", true)) }
        val autoBackup = remember { mutableStateOf(diagnostics.getBoolean("auto_backup", false)) }
        Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
            PageHeader("Журналы Cobalt Platform") { finish() }
            Spacer(Modifier.height(14.dp))
            Text("Диагностика", color = c.text, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            Text("Здесь отображаются события системного компонента. Логи не содержат пользовательский словарь.", color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 7.dp, bottom = 14.dp))
            SurfaceCard {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (loading) "Обновление…" else "Журнал Cobalt Platform", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("Обновить", color = c.accent, fontSize = 14.sp, modifier = Modifier.clickable { refreshLogs() }.padding(8.dp))
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(c.muted.copy(alpha = if (c.dark) .14f else .07f)).padding(14.dp)) {
                    Text(logText, color = c.text, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorOs17Button("Поделиться", false, onClick = { shareLogs() }, modifier = Modifier.weight(1f), darkTheme = c.dark, minimumHeight = 44.dp)
                    ColorOs17Button("Сохранить", false, onClick = { saveLogs() }, modifier = Modifier.weight(1f), darkTheme = c.dark, minimumHeight = 44.dp)
                }
            }
            Section("ХРАНЕНИЕ ЖУРНАЛОВ")
            SurfaceCard {
                SettingLine("Сохранять журналы", if (keepLogs.value) "Включено" else "Выключено", keepLogs.value) {
                    keepLogs.value = !keepLogs.value; diagnostics.edit { putBoolean("keep_logs", keepLogs.value) }; syncSettings()
                }
                Line()
                Text("Срок хранения", color = c.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("3d" to "3 дня", "7d" to "7 дней", "30d" to "30 дней", "forever" to "Без удаления").forEach { (key, label) ->
                        Text(label, color = if (retention.value == key) c.accent else c.muted, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (retention.value == key) c.accent.copy(alpha = .12f) else c.muted.copy(alpha = .07f)).clickable { retention.value = key; diagnostics.edit { putString("retention", key) }; syncSettings() }.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
                Line()
                SettingLine("Автоматическая резервная копия", if (autoBackup.value) "Включена" else "Выключена", autoBackup.value) { autoBackup.value = !autoBackup.value; diagnostics.edit { putBoolean("auto_backup", autoBackup.value) }; syncSettings() }
            }
            Text("По умолчанию хранится ограниченный журнал. Резервная копия создаётся только по выбору пользователя. Общая папка Download не используется автоматически.", color = c.muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            SurfaceCard { SettingLine("Очистить журнал", "Удалить сохранённые события Cobalt Platform", false) { val error = runRoot("rm -f $LOG_ROOT/logs/platform.log"); if (error == null) { logText = "Журнал очищен." } else Toast.makeText(this@LogsActivity, error, Toast.LENGTH_LONG).show() } }
            Spacer(Modifier.height(40.dp))
        }
    }

    @Composable private fun PageHeader(title: String, back: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { CobaltBackButton(back); Text(title, color = LocalDesign.current.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp)) } }
    @Composable private fun Section(title: String) { Text(title, color = LocalDesign.current.text.copy(alpha = .62f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp, modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp)) }
    @Composable private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) { val c = LocalDesign.current; Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.surface).padding(vertical = 2.dp), content = content) }
    @Composable private fun SettingLine(title: String, summary: String, checked: Boolean, onClick: () -> Unit) { val c = LocalDesign.current; Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, color = c.text, fontSize = 15.sp); Text(summary, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }; Text(if (checked) "Вкл." else "Выкл.", color = if (checked) c.accent else c.muted, fontSize = 13.sp) } }
    @Composable private fun Line() { Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(.33.dp).background(LocalDesign.current.line)) }
}
