package com.smartclock.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inversePrimary = InversePrimaryLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    surfaceTint = SurfaceTintLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003EA8),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFFBCC7DE),
    onSecondary = Color(0xFF111C2D),
    secondaryContainer = Color(0xFF3C475A),
    onSecondaryContainer = Color(0xFFD8E3FB),
    tertiary = Color(0xFFB7C8E1),
    onTertiary = Color(0xFF0B1C30),
    tertiaryContainer = Color(0xFF38485D),
    onTertiaryContainer = Color(0xFFD3E4FE),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = Error,
    onError = OnError,
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inversePrimary = InversePrimaryDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    surfaceTint = SurfaceTintDark
)

@Composable
fun SmartClockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
