package com.example.paymentmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode(
    val storageValue: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    fun next(): ThemeMode = when (this) {
        SYSTEM -> LIGHT
        LIGHT -> DARK
        DARK -> SYSTEM
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF4C5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF101A66),
    secondary = Color(0xFF007E87),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F2F5),
    onSecondaryContainer = Color(0xFF00363A),
    tertiary = Color(0xFF7756C8),
    tertiaryContainer = Color(0xFFEADDFF),
    background = Color(0xFFF4F6FF),
    onBackground = Color(0xFF171A2A),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF171A2A),
    surfaceVariant = Color(0xFFE7E8F3),
    onSurfaceVariant = Color(0xFF494A59),
    outline = Color(0xFF777887),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C2FF),
    onPrimary = Color(0xFF17216F),
    primaryContainer = Color(0xFF303A88),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF77DCE4),
    onSecondary = Color(0xFF00363B),
    secondaryContainer = Color(0xFF005056),
    onSecondaryContainer = Color(0xFF9EF1F6),
    tertiary = Color(0xFFD1BCFF),
    tertiaryContainer = Color(0xFF5E429C),
    background = Color(0xFF090B18),
    onBackground = Color(0xFFE8E9F4),
    surface = Color(0xFF111423),
    onSurface = Color(0xFFE8E9F4),
    surfaceVariant = Color(0xFF424452),
    onSurfaceVariant = Color(0xFFC7C6D1),
    outline = Color(0xFF90909D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

@Composable
fun PaymentMonitorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.typography
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = base.copy(
            displaySmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                lineHeight = 27.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 23.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        ),
        content = content,
    )
}
