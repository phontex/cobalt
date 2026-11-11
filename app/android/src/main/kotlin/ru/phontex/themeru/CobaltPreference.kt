package ru.phontex.themeru

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults

// Adapted from ONextBox SettingsComponents.kt (GPL-3.0): SettingsCardRow / SettingsJumpArrow.
// Uses the same COUI BasicComponent, 16/10 dp row margins, type styles and arrow geometry.
@Composable
fun CobaltPreferenceRow(title: String, summary: String = "", leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
 val c=LocalDesign.current
 BasicComponent(title=title, summary=summary.takeIf { it.isNotBlank() }, startAction=leading,
  titleColor=BasicComponentDefaults.titleColor(color=c.text), summaryColor=BasicComponentDefaults.summaryColor(color=c.muted),
  insideMargin=PaddingValues(horizontal=16.dp,vertical=10.dp), endActions={ CobaltJumpArrow(c.muted) }, onClick=onClick)
}

@Composable
private fun CobaltJumpArrow(tint:Color) {
 val direction=LocalLayoutDirection.current
 Canvas(modifier=Modifier.size(width=12.dp,height=24.dp).graphicsLayer(scaleX=if(direction==LayoutDirection.Rtl) -1f else 1f)) {
  val sx=size.width/12f; val sy=size.height/24f
  val path=Path().apply { moveTo(5f*sx,6f*sy);lineTo(10.646f*sx,11.646f*sy);cubicTo(10.842f*sx,11.842f*sy,10.842f*sx,12.158f*sy,10.646f*sx,12.354f*sy);lineTo(5f*sx,18f*sy) }
  drawPath(path=path,color=tint,style=Stroke(width=1.4.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
 }
}
