package me.lovearound.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The web app's warm colors (web/src/style.css), light and dark, as on the iPhone. */
@Immutable
data class AppColors(
    val ink: Color,
    val muted: Color,
    val faint: Color,
    /** The page. */
    val paper: Color,
    /** Grouped lists and cards. */
    val field: Color,
    val well: Color,
    val link: Color,
    /** Text on a `link`-filled button. */
    val onLink: Color,
    val heart: Color,
    val heartSoft: Color,
    val ok: Color,
    val danger: Color,
    val dangerSoft: Color,
    val star: Color,
    val indigo: Color,
    val dark: Boolean,
)

private val light = AppColors(
    ink = Color(0xFF3B2A23), muted = Color(0xFF6E554A), faint = Color(0xFF7A6156),
    paper = Color(0xFFFFFBF6), field = Color(0xFFF8EFE6), well = Color(0xFFF2E6DA),
    link = Color(0xFFB83A4E), onLink = Color(0xFFFFFFFF), heart = Color(0xFFE0596B), heartSoft = Color(0xFFFDE3E6),
    ok = Color(0xFF39744A), danger = Color(0xFFB3261E), dangerSoft = Color(0xFFFCEAE8),
    star = Color(0xFFD99A1E), indigo = Color(0xFF6474D6), dark = false,
)

private val dark = AppColors(
    ink = Color(0xFFF3E6DC), muted = Color(0xFFC7B0A2), faint = Color(0xFFA8917F),
    paper = Color(0xFF2A211D), field = Color(0xFF372C27), well = Color(0xFF40342E),
    link = Color(0xFFF28C9B), onLink = Color(0xFF2A211D), heart = Color(0xFFEC6F80), heartSoft = Color(0xFF4A2B31),
    ok = Color(0xFF8DCB9B), danger = Color(0xFFFF8A80), dangerSoft = Color(0xFF442E29),
    star = Color(0xFFE8B54F), indigo = Color(0xFF939EEA), dark = true,
)

val LocalColors = staticCompositionLocalOf { light }

/** The colors of the moment: `C.ink`, `C.paper`… */
val C: AppColors @Composable get() = LocalColors.current

@Composable
fun LoveAroundTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) dark else light
    val scheme = if (colors.dark) darkColorScheme(
        primary = colors.link, onPrimary = colors.onLink, background = colors.paper, surface = colors.paper,
        onBackground = colors.ink, onSurface = colors.ink, surfaceVariant = colors.field, onSurfaceVariant = colors.muted,
        surfaceContainer = colors.field, surfaceContainerHigh = colors.well, surfaceContainerLow = colors.paper,
        secondaryContainer = colors.heartSoft, onSecondaryContainer = colors.ink, error = colors.danger, outline = colors.faint,
    ) else lightColorScheme(
        primary = colors.link, onPrimary = colors.onLink, background = colors.paper, surface = colors.paper,
        onBackground = colors.ink, onSurface = colors.ink, surfaceVariant = colors.field, onSurfaceVariant = colors.muted,
        surfaceContainer = colors.field, surfaceContainerHigh = colors.well, surfaceContainerLow = colors.paper,
        secondaryContainer = colors.heartSoft, onSecondaryContainer = colors.ink, error = colors.danger, outline = colors.faint,
    )
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
