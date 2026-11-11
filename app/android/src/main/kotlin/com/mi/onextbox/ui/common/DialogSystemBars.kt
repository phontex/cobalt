package com.mi.onextbox.ui.common
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
@Composable
internal fun ColorOsDialogSystemBars(darkTheme: Boolean, dimProgress: Float) {
    var ancestor = LocalView.current.parent
    while (ancestor != null && ancestor !is DialogWindowProvider) ancestor = ancestor.parent
    val window = (ancestor as? DialogWindowProvider)?.window ?: return
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    DisposableEffect(window) {
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        val previousDim = window.attributes.dimAmount
        val previousDimBehind = window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
            window.setDimAmount(previousDim)
            if (!previousDimBehind) window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(.2f * dimProgress.coerceIn(0f, 1f))
    }
}
