package com.cobalt.app

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme

data class DesignColors(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentSurface: Color,
    val dark: Boolean,
    val well: Color = surface,
    val onAccent: Color = Color.White,
    val success: Color = Color(0xFF35704F),
    val warning: Color = Color(0xFF8A5A20),
    val danger: Color = Color(0xFFA8443A),
    val faint: Color = muted,
)

val LocalWallpaper = staticCompositionLocalOf { false }
val LocalPastel = staticCompositionLocalOf { false }
val LocalDesign = staticCompositionLocalOf {
    DesignColors(
        background = Color(0xFFECE6DA),
        surface = Color(0xFFFAF7F0),
        text = Color(0xFF1A1712),
        muted = Color(0xFF5E584D),
        line = Color(0xFFDFD7C7),
        accent = Color(0xFF2340C9),
        accentSurface = Color(0xFFE8ECFA),
        dark = false,
        well = Color(0xFFF1ECE1),
        faint = Color(0xFF756E5E),
    )
}

object SoftSpace {
    val gutter = 20.dp
    val section = 24.dp
    val group = 14.dp
    val card = 16.dp
    val barGap = 20.dp
}

object CobaltType {
    private fun font(weight: Int) = Font(
        resId = R.font.cobalt_sans,
        weight = FontWeight(weight),
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    val family = FontFamily(font(400), font(500), font(600), font(700))

    val display = TextStyle(fontFamily = family, fontWeight = FontWeight.W600, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp)
    val title = TextStyle(fontFamily = family, fontWeight = FontWeight.W600, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp)
    val headline = TextStyle(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.15).sp)
    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 15.sp, lineHeight = 22.sp)
    val caption = TextStyle(fontFamily = family, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 19.sp)
    val label = TextStyle(fontFamily = family, fontWeight = FontWeight.W600, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.9.sp)
    val micro = TextStyle(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp)
    val button = TextStyle(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 15.sp, lineHeight = 20.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 17.sp)
    val monoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.W400, fontSize = 11.sp, lineHeight = 15.sp)
    val numeric = TextStyle(fontFamily = family, fontWeight = FontWeight.W600, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
}

@Composable
fun DesignTheme(
    activity: Activity,
    mode: String,
    size: String,
    systemAmoled: Boolean = activity.getSharedPreferences("appearance", 0).getBoolean("system_amoled", false),
    colorMode: String = activity.getSharedPreferences("appearance", 0).getString("color_mode", "app") ?: "app",
    content: @Composable () -> Unit,
) {
    val pastel = colorMode == "pastel"
    val wallpaper = colorMode == "wallpaper" && android.os.Build.VERSION.SDK_INT >= 31
    val dark = when (mode) {
        "dark", "amoled" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val amoled = dark && (mode == "amoled" || (mode == "system" && systemAmoled))
    val base = when {
        pastel && amoled -> DesignColors(
            background = Color.Black, surface = Color(0xFF141217), text = Color(0xFFF5EFF7),
            muted = Color(0xFFA79CAE), line = Color(0xFF2A2630), accent = Color(0xFFC1A5EC),
            accentSurface = Color(0xFF231C31), dark = true, well = Color(0xFF1B1720), faint = Color(0xFF9A90A2),
            onAccent = Color(0xFF1B1426), success = Color(0xFF74BC92), warning = Color(0xFFD5A469), danger = Color(0xFFDB8279),
        )
        pastel && dark -> DesignColors(
            background = Color(0xFF141117), surface = Color(0xFF1E1A22), text = Color(0xFFF5EFF7),
            muted = Color(0xFFA79CAE), line = Color(0xFF302B36), accent = Color(0xFFC1A5EC),
            accentSurface = Color(0xFF2B2338), dark = true, well = Color(0xFF26212E), faint = Color(0xFF9A90A2),
            onAccent = Color(0xFF1B1426), success = Color(0xFF74BC92), warning = Color(0xFFD5A469), danger = Color(0xFFDB8279),
        )
        pastel -> DesignColors(
            background = Color(0xFFEDE4EA), surface = Color(0xFFFCF8FA), text = Color(0xFF221D27),
            muted = Color(0xFF6B6372), line = Color(0xFFE2D8E0), accent = Color(0xFF6E4F9E),
            accentSurface = Color(0xFFF0E8F8), dark = false, well = Color(0xFFF3EBF1), faint = Color(0xFF706875),
        )
        amoled -> DesignColors(
            background = Color.Black, surface = Color(0xFF0E0E0D), text = Color(0xFFEDE9E1),
            muted = Color(0xFF948D80), line = Color(0xFF232220), accent = Color(0xFF9AACFF),
            accentSurface = Color(0xFF1A2240), dark = true, well = Color(0xFF171614), faint = Color(0xFF867F70),
            onAccent = Color(0xFF0B1024), success = Color(0xFF74BC92), warning = Color(0xFFD5A469), danger = Color(0xFFDB8279),
        )
        dark -> DesignColors(
            background = Color(0xFF11100F), surface = Color(0xFF1A1917), text = Color(0xFFF3EFE7),
            muted = Color(0xFFA29A8C), line = Color(0xFF2C2A26), accent = Color(0xFF93A7FF),
            accentSurface = Color(0xFF202844), dark = true, well = Color(0xFF22201E), faint = Color(0xFF928B7B),
            onAccent = Color(0xFF0B1024), success = Color(0xFF74BC92), warning = Color(0xFFD5A469), danger = Color(0xFFDB8279),
        )
        else -> DesignColors(
            background = Color(0xFFECE6DA), surface = Color(0xFFFAF7F0), text = Color(0xFF1A1712),
            muted = Color(0xFF5E584D), line = Color(0xFFDFD7C7), accent = Color(0xFF2340C9),
            accentSurface = Color(0xFFE8ECFA), dark = false, well = Color(0xFFF1ECE1), faint = Color(0xFF756E5E),
            success = Color(0xFF35704F), warning = Color(0xFF8A5A20), danger = Color(0xFFA8443A),
        )
    }
    val dyn = if (wallpaper) {
        if (dark) androidx.compose.material3.dynamicDarkColorScheme(activity) else androidx.compose.material3.dynamicLightColorScheme(activity)
    } else null
    val c = if (dyn != null) {
        DesignColors(
            background = if (amoled) Color.Black else dyn.background,
            surface = if (amoled) Color(0xFF0F0F0E) else dyn.surface,
            text = dyn.onSurface,
            muted = dyn.onSurfaceVariant,
            line = dyn.outlineVariant,
            accent = dyn.primary,
            accentSurface = dyn.primaryContainer,
            dark = dark,
            well = dyn.surfaceVariant,
            faint = dyn.onSurfaceVariant.copy(alpha = .78f),
            onAccent = dyn.onPrimary,
        )
    } else base

    val current = LocalDensity.current
    val multiplier = when (size) {
        "compact" -> 0.92f
        "large" -> 1.08f
        else -> 1f
    }

    SideEffect {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            val window = activity.window
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalWallpaper provides wallpaper,
        LocalPastel provides pastel,
        LocalDesign provides c,
        LocalDensity provides Density(current.density, current.fontScale * multiplier),
    ) {
        COUITheme(
            colors = if (dark) darkColorScheme(background = c.background, surface = c.surface, surfaceContainerHighest = c.surface, primary = c.accent)
            else lightColorScheme(background = c.background, surface = c.surface, primary = c.accent),
        ) {
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (dark) androidx.compose.material3.darkColorScheme(primary = c.accent, background = c.background, surface = c.surface, onSurface = c.text, onBackground = c.text)
                else androidx.compose.material3.lightColorScheme(primary = c.accent, background = c.background, surface = c.surface, onSurface = c.text, onBackground = c.text),
            ) { content() }
        }
    }
}
