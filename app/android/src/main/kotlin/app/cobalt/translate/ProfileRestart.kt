package app.cobalt.translate

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import app.cobalt.translate.R
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.icon.COUIIcons
import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import app.cobalt.ui.ui.common.ColorOsTopBarButton
import app.cobalt.ui.ui.common.CouiConfirmDialog
import app.cobalt.ui.ui.layout.LocalChromeNativeBlurEnabled
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

@Composable fun ProfileRestartAction(profile: SupportedProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember(profile) { mutableStateOf(false) }
    var reboot by remember(profile) { mutableStateOf(false) }
    var confirmReboot by remember(profile) { mutableStateOf(false) }
    var busy by remember(profile) { mutableStateOf(false) }
    CompositionLocalProvider(LocalChromeNativeBlurEnabled provides false) {
        Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { ColorOsTopBarButton(onClick = { if (!busy) { reboot = false; show = true } },
            modifier = Modifier.semantics { contentDescription = "Остановить ${profile.label}" }) {
            val tint = LocalDesign.current.text
            Canvas(Modifier.size(24.dp)) { drawRoundRect(tint, topLeft = androidx.compose.ui.geometry.Offset(size.width*.23f,size.height*.23f), size = androidx.compose.ui.geometry.Size(size.width*.54f,size.height*.54f), cornerRadius = CornerRadius(2.dp.toPx())) }
        } }
    }
    CouiConfirmDialog(show = show, title = if (reboot) "Перезагрузить устройство?" else "Остановить приложение?",
        summary = if (reboot) "Устройство будет перезагружено. Несохранённые данные могут быть потеряны. Требуется root-доступ." else "Для применения некоторых изменений может потребоваться остановить Theme Store и открыть его снова. Для остановки требуется root-доступ.",
        negativeText = "Отмена", positiveText = if (reboot) "Продолжить" else "Остановить",
        onDismissRequest = { show = false }, onPositive = {
            show = false
            if (reboot) confirmReboot = true else {
                val command = runCatching { RestartCommands.forceStop(profile.packageName) }.getOrNull()
                when {
                    command == null -> Toast.makeText(context, "Для этого приложения требуется особый способ остановки", Toast.LENGTH_LONG).show()
                    !installed(context, profile.packageName) -> Toast.makeText(context, "Приложение не найдено", Toast.LENGTH_LONG).show()
                    else -> {
                        busy = true
                        scope.launch {
                            val error = requestRoot(command)
                            busy = false
                            Toast.makeText(context, error ?: "Команда остановки отправлена. Откройте ${profile.label} заново.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }, content = {
            Column(Modifier.fillMaxWidth()) {
                Text("${profile.label} (${profile.packageName})", color = LocalDesign.current.text, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = reboot, role = Role.Checkbox, interactionSource = null, indication = null,
                    onValueChange = { reboot = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    val checkColor by animateColorAsState(if (reboot) LocalDesign.current.accent else Color.Transparent, tween(180), label="checkColor")
                    val checkBorder by animateColorAsState(if (reboot) LocalDesign.current.accent else LocalDesign.current.muted, tween(180), label="checkBorder")
                    val checkScale by animateFloatAsState(if (reboot) 1f else .94f, spring(dampingRatio=.52f,stiffness=420f), label="checkScale")
                    val tickProgress by animateFloatAsState(if (reboot) 1f else 0f, tween(if(reboot) 220 else 120), label="tickProgress")
                    Box(Modifier.size(22.dp).graphicsLayer { scaleX=checkScale;scaleY=checkScale }.border(1.5.dp, checkBorder, RoundedCornerShape(5.dp)).background(checkColor, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(14.dp)) {
                            val full=androidx.compose.ui.graphics.Path().apply { moveTo(size.width*.12f,size.height*.52f);lineTo(size.width*.40f,size.height*.78f);lineTo(size.width*.90f,size.height*.20f) }
                            val measure=PathMeasure().apply { setPath(full,false) }
                            val part=androidx.compose.ui.graphics.Path()
                            measure.getSegment(0f,measure.length*tickProgress.coerceIn(0f,1f),part,true)
                            drawPath(part, Color.White, style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Перезагрузить устройство", color = LocalDesign.current.muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        })
    CouiConfirmDialog(show = confirmReboot, title = "Перезагрузить устройство?",
        summary = "Все приложения будут закрыты. Несохранённые данные могут быть потеряны. Требуется root-доступ.",
        negativeText = "Отмена", positiveText = "Перезагрузить", onDismissRequest = { confirmReboot = false },
        onPositive = {
            confirmReboot = false; busy = true
            scope.launch {
                val error = requestRoot(RestartCommands.reboot())
                busy = false
                Toast.makeText(context, error ?: "Запрос на перезагрузку отправлен", Toast.LENGTH_LONG).show()
            }
        })
}

/** Shared header for supported application profiles. */
@Composable fun ProfileHeader(profile: SupportedProfile, centered: Boolean, showIcon: Boolean, title: String = profile.label, showAction: Boolean = true, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalDesign.current
    val icon = remember(profile.packageName, showIcon) {
        if (!showIcon) null else runCatching { context.packageManager.getApplicationIcon(profile.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CobaltBackButton(onBack) }
        Row(Modifier.weight(1f).padding(horizontal = 6.dp),
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (showIcon) {
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                else Icon(ImageVector.vectorResource(R.drawable.ic_phosphor_translate), contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
        }
        if (showAction) ProfileRestartAction(profile) else Spacer(Modifier.size(48.dp))
    }
}
