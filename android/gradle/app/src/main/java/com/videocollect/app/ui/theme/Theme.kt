package com.videocollect.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = TextInverse,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue700,
    secondary = Cyan500,
    onSecondary = TextInverse,
    secondaryContainer = Cyan50,
    onSecondaryContainer = Cyan500,
    background = CoolBg,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFCBD5E1),
    error = StatusRed,
    onError = TextInverse,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue400,
    onPrimary = Color(0xFF003258),
    primaryContainer = Blue700,
    onPrimaryContainer = Blue100,
    secondary = Cyan400,
    onSecondary = Color(0xFF003545),
    secondaryContainer = Color(0xFF004D61),
    onSecondaryContainer = Cyan50,
    background = CoolBgDark,
    onBackground = Color(0xFFE2E8F0),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    error = Color(0xFFF87171),
    onError = Color(0xFF601410),
)

@Composable
fun VideoCollectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
