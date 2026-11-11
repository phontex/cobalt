package ru.phontex.themeru
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
// Static layered gradients: no blur, timers or continuous animation.
fun Modifier.cobaltHeroBackground(dark: Boolean, pastel: Boolean = false, wallpaperAccent: Color? = null) = drawWithCache {
 val w = size.width
 val h = size.height
 val base = Brush.linearGradient(
  colors = if(wallpaperAccent!=null) listOf(lerp(wallpaperAccent,Color(0xff273354),.35f),lerp(wallpaperAccent,Color(0xff1b2541),.55f),lerp(wallpaperAccent,Color(0xff101a30),.72f)) else if (pastel && dark) listOf(Color(0xff664f8f),Color(0xff544273),Color(0xff352d57)) else if(pastel) listOf(Color(0xffa697ea),Color(0xff8f79d8),Color(0xff7464b7)) else if (dark) listOf(Color(0xff234479), Color(0xff142b60), Color(0xff142247))
           else listOf(Color(0xff548fdb), Color(0xff315cb1), Color(0xff354480)),
  start = Offset.Zero, end = Offset(w, h))
 val glow = Brush.radialGradient(
  colors = listOf((if(pastel) Color(0xffffd3e7) else Color(0xff8dcfff)).copy(alpha=if(dark) .24f else .42f), Color.Transparent),
  center = Offset(w * .12f, h * .02f), radius = max(w,h) * .85f)
 val violet = Brush.radialGradient(
  colors = listOf((if(pastel) Color(0xffffb9db) else Color(0xff9b88ea)).copy(alpha=if(dark) .16f else .22f), Color.Transparent),
  center = Offset(w * .95f, h * .92f), radius = max(w,h) * .72f)
 val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha=.20f), Color.Transparent, Color.Black.copy(alpha=.13f)), start=Offset.Zero, end=Offset(w*.8f,h))
 onDrawBehind { drawRect(base); drawRect(glow); drawRect(violet); drawRect(sheen) }
}
