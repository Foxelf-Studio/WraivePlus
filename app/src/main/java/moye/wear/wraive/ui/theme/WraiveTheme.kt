package moye.wear.wraive.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import androidx.wear.compose.material3.dynamicColorScheme
import moye.wear.wraive.ui.LocalUiLocaleTag

val LocalScreenRound = staticCompositionLocalOf { true }

private val BrandPrimary = Color(0xFFEEF3FF)
private val BrandPrimaryDim = Color(0xFFC9D3EA)
private val BrandPrimaryContainer = Color(0xFF2A3550)
private val BrandOnPrimaryContainer = Color(0xFFEEF3FF)
private val BrandSecondary = Color(0xFFC9D3E8)
private val BrandSecondaryDim = Color(0xFF9FAAC4)
private val BrandSecondaryContainer = Color(0xFF2C364E)
private val BrandOnSecondaryContainer = Color(0xFFE3E9F8)
private val BrandTertiary = Color(0xFFA8D4FF)
private val BrandTertiaryDim = Color(0xFF7FB6E6)
private val BrandTertiaryContainer = Color(0xFF17405C)
private val BrandOnTertiaryContainer = Color(0xFFD6ECFF)

private val brandScheme = ColorScheme(
    primary = BrandPrimary,
    primaryDim = BrandPrimaryDim,
    onPrimary = Color(0xFF10182B),
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    secondaryDim = BrandSecondaryDim,
    onSecondary = Color(0xFF1A2338),
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    tertiaryDim = BrandTertiaryDim,
    onTertiary = Color(0xFF002B45),
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandOnTertiaryContainer,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = Color(0xFFE6E9F2),
    onSurface = Color(0xFFE6E9F2),
    onSurfaceVariant = Color(0xFFC3C8D8),
    outline = Color(0xFF8D94A8),
    outlineVariant = Color(0xFF454B5E),
    surfaceContainerLow = Color(0xFF151922),
    surfaceContainer = Color(0xFF1B1F29),
    surfaceContainerHigh = Color(0xFF252A36)
)

/**
 * Keeps the brand accent (#416FFF) stable while letting dynamic color tint the dark surfaces.
 */
private fun ColorScheme.withBrandAccents(): ColorScheme = copy(
    primary = BrandPrimary,
    primaryDim = BrandPrimaryDim,
    onPrimary = Color(0xFF10182B),
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    secondaryDim = BrandSecondaryDim,
    onSecondary = Color(0xFF1A2338),
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    tertiaryDim = BrandTertiaryDim,
    onTertiary = Color(0xFF002B45),
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = BrandOnTertiaryContainer
)

@Composable
fun WraiveTheme(
    dynamicColor: Boolean = true,
    customFontPath: String = "",
    localeTag: String = "system",
    content: @Composable () -> Unit
) {
    val scheme = if (dynamicColor) {
        (dynamicColorScheme(LocalContext.current) ?: brandScheme).withBrandAccents()
    } else {
        brandScheme
    }
    val fontFamily = androidx.compose.runtime.remember(customFontPath) {
        customFontPath.takeIf(String::isNotBlank)?.let { path ->
            runCatching { FontFamily(Typeface.createFromFile(path)) }.getOrNull()
        } ?: FontFamily.Default
    }
    CompositionLocalProvider(
        LocalScreenRound provides LocalConfiguration.current.isScreenRound,
        LocalUiLocaleTag provides localeTag
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(defaultFontFamily = fontFamily),
            content = content
        )
    }
}
