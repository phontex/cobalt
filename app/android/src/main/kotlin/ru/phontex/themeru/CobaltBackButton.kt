package ru.phontex.themeru

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.layout.LocalChromeNativeBlurEnabled
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back

// Use the same motion, material and spotlight as ONextBox's detail toolbar.
@Composable fun CobaltBackButton(onClick: () -> Unit) {
    val colors = LocalDesign.current
    CompositionLocalProvider(LocalChromeNativeBlurEnabled provides false) {
        ColorOsTopBarButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = "Назад" }) {
            Icon(COUIIcons.Back, contentDescription = null,
                tint = colors.text, modifier = Modifier.size(24.dp))
        }
    }
}
