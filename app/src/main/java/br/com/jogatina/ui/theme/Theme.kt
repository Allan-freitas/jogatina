package br.com.jogatina.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = JogatinaMagenta,
    onPrimary = JogatinaWhite,
    secondary = JogatinaGold,
    tertiary = JogatinaMagentaGlow,
    background = JogatinaNavyBottom,
    surface = JogatinaNavySurface,
    onBackground = JogatinaWhite,
    onSurface = JogatinaWhite
)

private val LightColorScheme = lightColorScheme(
    primary = JogatinaMagentaDeep,
    onPrimary = JogatinaWhite,
    secondary = JogatinaGold,
    tertiary = JogatinaMagenta,
    background = JogatinaNavyBottom,
    surface = JogatinaNavySurface,
    onBackground = JogatinaWhite,
    onSurface = JogatinaWhite

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun JogatinaTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Tela de boas-vindas usa identidade fixa (navy + magenta),
    // por isso dynamicColor fica desligado por padrão.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}