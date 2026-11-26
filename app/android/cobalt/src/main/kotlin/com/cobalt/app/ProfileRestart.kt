package com.cobalt.app

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cobalt.app.ui.common.CouiConfirmDialog
import com.cobalt.app.ui.layout.LocalChromeNativeBlurEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Pass only an explicitly supported profile, never an arbitrary intent or user-supplied command. */
data class SupportedProfile(val label: String, val packageName: String)
val ThemeStoreProfile = SupportedProfile("Theme Store", "com.heytap.themestore")

internal suspend fun requestRoot(command: String): String? = withContext(Dispatchers.IO) {
    var process: Process? = null
    try {
        process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroy()
            "Время ожидания root-доступа истекло. Проверьте запрос и повторите попытку."
        } else if (process.exitValue() != 0) {
            "Команда отклонена или завершилась ошибкой. Проверьте root-доступ."
        } else null
    } catch (_: Exception) {
        "Не удалось выполнить команду. Проверьте доступность root."
    } finally { process?.destroy() }
}

private fun installed(context: Context, name: String): Boolean = try {
    @Suppress("DEPRECATION") context.packageManager.getApplicationInfo(name, 0); true
} catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }

private fun toast(context: Context, text: String) =
    android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()

/**
 * Stop / reboot control for a supported profile. Rendered as a soft outlined square in the
 * page header; the dialogs and root commands are unchanged.
 */
@Composable
fun ProfileRestartAction(profile: SupportedProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = LocalDesign.current
    var show by remember(profile) { mutableStateOf(false) }
    var reboot by remember(profile) { mutableStateOf(false) }
    var confirmReboot by remember(profile) { mutableStateOf(false) }
    var busy by remember(profile) { mutableStateOf(false) }
    CompositionLocalProvider(LocalChromeNativeBlurEnabled provides false) {
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by androidx.compose.animation.core.animateFloatAsState(
            if (pressed) .92f else 1f,
            androidx.compose.animation.core.tween(110),
            label = "stopScale",
        )
        val fill by androidx.compose.animation.animateColorAsState(
            if (pressed) c.well else androidx.compose.ui.graphics.Color.Transparent,
            androidx.compose.animation.core.tween(120),
            label = "stopBg",
        )
        Box(
            Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(SoftShape.control).background(fill).border(1.dp, c.line, SoftShape.control)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !busy,
                    role = Role.Button,
                ) { reboot = false; show = true }
                .semantics { contentDescription = "Остановить ${profile.label}" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(13.dp)) {
                drawRoundRect(
                    c.text,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
    CouiConfirmDialog(
        show = show,
        title = if (reboot) "Перезагрузить устройство?" else "Остановить приложение?",
        summary = if (reboot) "Устройство будет перезагружено. Несохранённые данные могут быть потеряны. Требуется root-доступ."
        else "Изменения применяются после перезапуска. Для остановки ${profile.label} требуется root-доступ.",
        negativeText = "Отмена",
        positiveText = if (reboot) "Продолжить" else "Остановить",
        onDismissRequest = { show = false },
        onPositive = {
            show = false
            if (reboot) confirmReboot = true else {
                val command = runCatching { RestartCommands.forceStop(profile.packageName) }.getOrNull()
                when {
                    command == null -> toast(context, "Для этого приложения требуется особый способ остановки")
                    !installed(context, profile.packageName) -> toast(context, "Приложение не установлено")
                    else -> {
                        busy = true
                        scope.launch {
                            val error = requestRoot(command)
                            busy = false
                            toast(context, error ?: "Команда отправлена. Откройте ${profile.label} заново.")
                        }
                    }
                }
            }
        },
        content = {
            Column(Modifier.fillMaxWidth()) {
                Text("${profile.label} · ${profile.packageName}", color = c.text, fontSize = 13.sp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = reboot, role = Role.Checkbox, interactionSource = null, indication = null,
                        onValueChange = { reboot = it },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(20.dp).border(
                            if (reboot) 6.dp else 1.5.dp,
                            if (reboot) c.accent else c.muted,
                            RoundedCornerShape(10.dp),
                        ).background(if (reboot) c.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Перезагрузить устройство", color = c.muted, fontSize = 13.sp)
                }
            }
        },
    )
    CouiConfirmDialog(
        show = confirmReboot,
        title = "Перезагрузить устройство?",
        summary = "Все приложения будут закрыты. Несохранённые данные могут быть потеряны. Требуется root-доступ.",
        negativeText = "Отмена",
        positiveText = "Перезагрузить",
        onDismissRequest = { confirmReboot = false },
        onPositive = {
            confirmReboot = false
            busy = true
            scope.launch {
                val error = requestRoot(RestartCommands.reboot())
                busy = false
                toast(context, error ?: "Запрос на перезагрузку отправлен")
            }
        },
    )
}
