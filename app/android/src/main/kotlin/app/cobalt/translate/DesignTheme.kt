package app.cobalt.translate

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

data class DesignColors(val background:Color,val surface:Color,val text:Color,val muted:Color,val line:Color,val accent:Color,val accentSurface:Color,val dark:Boolean)
val LocalWallpaper = staticCompositionLocalOf { false }
val LocalPastel = staticCompositionLocalOf { false }
val LocalDesign = staticCompositionLocalOf { DesignColors(Color(0xff0c0e12),Color(0xff1b1e24),Color.White,Color(0xffa8adb7),Color(0xff353a44),Color(0xffa8baff),Color(0xff2d3d60),true) }

@Composable fun DesignTheme(activity:Activity, mode:String, size:String, systemAmoled:Boolean = activity.getSharedPreferences("appearance",0).getBoolean("system_amoled",false), colorMode:String = activity.getSharedPreferences("appearance",0).getString("color_mode", if(activity.getSharedPreferences("appearance",0).getBoolean("pastel_experiment",false)) "pastel" else "app") ?: "app", content:@Composable ()->Unit) {
 val pastel=colorMode=="pastel"
 val wallpaper=colorMode=="wallpaper" && android.os.Build.VERSION.SDK_INT>=31
 val systemDark=isSystemInDarkTheme()
 val dark=when(mode){"dark","amoled"->true;"light"->false;else->systemDark}
 val amoled=dark && (mode=="amoled" || (mode=="system" && systemAmoled))
 val base=if(pastel && amoled) DesignColors(Color.Black,Color(0xff100d14),Color(0xfff7f2ff),Color(0xffc5b9d2),Color(0xff382f42),Color(0xffc5aaff),Color(0xff21172d),true)
 else if(pastel && dark) DesignColors(Color(0xff19131f),Color(0xff292131),Color(0xfffaf3ff),Color(0xffc7b8cf),Color(0xff493c51),Color(0xffc6aaff),Color(0xff453459),true)
 else if(pastel) DesignColors(Color(0xfffff4f2),Color.White,Color(0xff291f34),Color(0xff897c90),Color(0xffefe3ed),Color(0xff7652ce),Color(0xffeee5ff),false)
 else if(amoled) DesignColors(Color.Black,Color(0xff10141b),Color(0xfff4f7ff),Color(0xffa5afc1),Color(0xff252d3c),Color(0xff75aeff),Color(0xff101c32),true)
 else if(dark) DesignColors(Color(0xff0d1421),Color(0xff192538),Color(0xfff3f6ff),Color(0xffa8b7cd),Color(0xff30425c),Color(0xff76b0ff),Color(0xff233c60),true)
 else DesignColors(Color(0xfff3f4f6),Color.White,Color(0xff17181a),Color(0xff85878b),Color(0xffe5e6e8),Color(0xff2775d8),Color(0xffe1edff),false)
 val dyn=if(wallpaper) { if(dark) androidx.compose.material3.dynamicDarkColorScheme(activity) else androidx.compose.material3.dynamicLightColorScheme(activity) } else null
 val c=if(dyn!=null) DesignColors(if(amoled) Color.Black else dyn.background,if(amoled) Color(0xff10141b) else dyn.surface, dyn.onSurface,dyn.onSurfaceVariant,dyn.outlineVariant,dyn.primary,dyn.primaryContainer,dark) else base
 val current=LocalDensity.current
 val multiplier=when(size){"compact"->0.90f;"large"->1.10f;else->1f}
 SideEffect {
  activity.window.statusBarColor=android.graphics.Color.TRANSPARENT
  activity.window.navigationBarColor=android.graphics.Color.TRANSPARENT
  if(android.os.Build.VERSION.SDK_INT>=29) activity.window.isNavigationBarContrastEnforced=false
  WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply {
   isAppearanceLightStatusBars=!dark
   isAppearanceLightNavigationBars=!dark
  }
 }
 CompositionLocalProvider(LocalWallpaper provides wallpaper,LocalPastel provides pastel,LocalDesign provides c,LocalDensity provides Density(current.density,current.fontScale*multiplier)) { COUITheme(colors=if(dark) darkColorScheme(background=c.background,surface=c.surface,surfaceContainerHighest=c.surface,primary=c.accent) else lightColorScheme(background=c.background,surface=c.surface,primary=c.accent)) { androidx.compose.material3.MaterialTheme(colorScheme=if(dark) androidx.compose.material3.darkColorScheme(primary=c.accent,background=c.background,surface=c.surface,onSurface=c.text,onBackground=c.text) else androidx.compose.material3.lightColorScheme(primary=c.accent,background=c.background,surface=c.surface,onSurface=c.text,onBackground=c.text)) { content() } } }
}
