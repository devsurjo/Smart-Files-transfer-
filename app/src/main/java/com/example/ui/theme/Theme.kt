package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberCyan,
    onPrimary = SlateBlack,
    secondary = MutedTeal,
    onSecondary = PureWhite,
    tertiary = NeonRose,
    background = SlateBlack,
    onBackground = SoftGrey,
    surface = Charcoal,
    onSurface = PureWhite,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = SoftGrey
  )

private val LightColorScheme = DarkColorScheme // Always preserve futuristic dark theme for Smart LAN Share

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
