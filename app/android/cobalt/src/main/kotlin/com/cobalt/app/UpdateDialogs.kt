package com.cobalt.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobalt.app.ui.common.CouiConfirmDialog

/** One update confirmation surface used by both app and translation updates. */
@Composable
internal fun UpdateInfoDialog(
    show: Boolean,
    title: String,
    subtitle: String,
    mainGroups: List<ChangeGroup>,
    details: List<String>,
    primaryText: String,
    onDismiss: () -> Unit,
    onPrimary: () -> Unit,
) {
    val c = LocalDesign.current
    CouiConfirmDialog(
        show = show,
        title = title,
        summary = subtitle,
        negativeText = "Отмена",
        positiveText = primaryText,
        onDismissRequest = onDismiss,
        onPositive = onPrimary,
        content = {
            Column(Modifier.fillMaxWidth()) {
                if (mainGroups.isNotEmpty()) {
                    Text("Основные изменения", style = CobaltType.bodyStrong, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    mainGroups.take(5).forEach { group ->
                        Text(group.title, style = CobaltType.caption, color = c.text, modifier = Modifier.padding(top = 5.dp))
                        group.items.take(5).forEach { item ->
                            Text("• $item", style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Дополнительно", style = CobaltType.bodyStrong, color = c.text)
                    details.take(8).forEach { item ->
                        Text("• $item", style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        },
    )
}

internal fun AppUpdate.toChangeGroups(): List<ChangeGroup> = listOf(
    ChangeGroup("Новая версия Cobalt · $version", mainChanges.ifEmpty { listOf(title) }),
)

internal fun TranslationChangeLog.toChangeGroups(): List<ChangeGroup> =
    mainGroups.ifEmpty { listOf(ChangeGroup("Обновление переводов", listOf("Изменения опубликованы в каталоге Cobalt"))) }
