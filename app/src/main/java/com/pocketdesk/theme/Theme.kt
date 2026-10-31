package com.pocketdesk.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AdwaitaBlue,
    onPrimary = TextPrimary,
    secondary = AdwaitaAccent,
    onSecondary = AdwaitaDarkBg,
    tertiary = AdwaitaBlueDark,
    background = AdwaitaDarkBg,
    onBackground = TextPrimary,
    surface = AdwaitaCardBg,
    onSurface = TextPrimary,
    surfaceVariant = AdwaitaCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = AdwaitaDestructive
)

@Composable
fun PocketDeskTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = AdwaitaDarkBg.toArgb()
                window.navigationBarColor = AdwaitaDarkBg.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
