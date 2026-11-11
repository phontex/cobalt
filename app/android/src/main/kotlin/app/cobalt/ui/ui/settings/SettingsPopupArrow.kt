package app.cobalt.ui.settings
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun SettingsPopupArrow(
    tint: Color = COUITheme.colorScheme.onSurfaceVariantActions,
) {
    Canvas(modifier = Modifier.size(width = 14.dp, height = 24.dp)) {
        val scaleX = size.width / 14f
        val scaleY = size.height / 24f
        val stroke = Stroke(
            width = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val upper = Path().apply {
            moveTo(4.199f * scaleX, 10f * scaleY)
            lineTo(8.328f * scaleX, 5.413f * scaleY)
            cubicTo(
                8.526f * scaleX,
                5.192f * scaleY,
                8.872f * scaleX,
                5.192f * scaleY,
                9.071f * scaleX,
                5.413f * scaleY,
            )
            lineTo(13.199f * scaleX, 10f * scaleY)
        }
        val lower = Path().apply {
            moveTo(13.199f * scaleX, 14f * scaleY)
            lineTo(9.071f * scaleX, 18.587f * scaleY)
            cubicTo(
                8.872f * scaleX,
                18.808f * scaleY,
                8.526f * scaleX,
                18.808f * scaleY,
                8.328f * scaleX,
                18.587f * scaleY,
            )
            lineTo(4.199f * scaleX, 14f * scaleY)
        }
        drawPath(upper, color = tint, style = stroke)
        drawPath(lower, color = tint, style = stroke)
    }
}
