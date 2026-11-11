package com.mi.onextbox.ui
import androidx.compose.runtime.staticCompositionLocalOf
internal val LocalBottomBarContentOpacity = staticCompositionLocalOf<() -> Float> { { 1f } }
