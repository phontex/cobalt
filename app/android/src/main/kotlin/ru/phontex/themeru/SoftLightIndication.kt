@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package ru.phontex.themeru

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Gentle bounded light, drawn directly rather than using the platform's noisy ripple shader. */
@Suppress("DEPRECATION")
class SoftLightIndication(private val color: Color) : Indication {
 @Composable
 override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
  val light = remember(color) { LightInstance(color) }
  LaunchedEffect(interactionSource, light) {
   var expand: Job? = null
   var fade: Job? = null
   var active: PressInteraction.Press? = null
   interactionSource.interactions.collect { event ->
    when (event) {
     is PressInteraction.Press -> {
      active = event
      expand?.cancel(); fade?.cancel()
      light.origin = event.pressPosition
      light.radius.snapTo(0.04f); light.opacity.snapTo(0.13f)
      expand = launch { light.radius.animateTo(1f, tween(420)) }
     }
     is PressInteraction.Release, is PressInteraction.Cancel -> {
      val released = if (event is PressInteraction.Release) event.press else (event as PressInteraction.Cancel).press
      if (active == released) {
       active = null
       fade?.cancel()
       fade = launch { light.opacity.animateTo(0f, tween(260)) }
      }
     }
    }
   }
  }
  return light
 }
 private class LightInstance(val color: Color) : IndicationInstance {
  var origin by mutableStateOf(Offset.Zero)
  val radius = Animatable(0f)
  val opacity = Animatable(0f)
  override fun ContentDrawScope.drawIndication() {
   drawContent()
   val r = hypot(size.width, size.height) * radius.value
   if (r > 0f && opacity.value > 0f) clipRect {
    drawCircle(brush=Brush.radialGradient(listOf(color.copy(alpha=opacity.value), Color.Transparent), center=origin, radius=r), radius=r, center=origin)
   }
  }
 }
}
