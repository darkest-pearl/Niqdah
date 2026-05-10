package com.musab.niqdah.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = LedgerGreen,
    onPrimary = SoftPaper,
    primaryContainer = MintSurface,
    onPrimaryContainer = LedgerGreenDark,
    secondary = Brass,
    onSecondary = SoftPaper,
    secondaryContainer = Color(0xFFFFE5B9),
    onSecondaryContainer = Color(0xFF2E1B00),
    tertiary = ClearBlue,
    tertiaryContainer = Color(0xFFD5EEF7),
    onTertiaryContainer = Color(0xFF062B3A),
    background = SoftPaper,
    onBackground = Ink,
    surface = ColorTokens.LightSurface,
    onSurface = Ink,
    surfaceVariant = MintSurface,
    onSurfaceVariant = ColorTokens.MutedInk,
    error = ColorTokens.Error,
    errorContainer = ColorTokens.ErrorContainer,
    onErrorContainer = ColorTokens.OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = LedgerGreenDark,
    primaryContainer = LedgerGreenDark,
    onPrimaryContainer = MintSurface,
    secondary = NightSecondary,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF4E3514),
    onSecondaryContainer = Color(0xFFFFDFA8),
    tertiary = NightTertiary,
    tertiaryContainer = Color(0xFF173A48),
    onTertiaryContainer = Color(0xFFC7EEFA),
    background = NightBackground,
    onBackground = ColorTokens.NightText,
    surface = NightSurface,
    onSurface = ColorTokens.NightText,
    surfaceVariant = ColorTokens.NightSurfaceVariant,
    onSurfaceVariant = ColorTokens.NightMutedText,
    error = ColorTokens.NightError,
    errorContainer = ColorTokens.NightErrorContainer,
    onErrorContainer = ColorTokens.NightOnErrorContainer
)

@Composable
fun NiqdahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = NiqdahShapes,
        content = content
    )
}
