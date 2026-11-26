package spotter.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The app's colours by what they are for, so each has a light and a dark value. */
data class Palette(
    val page: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    /** The one accent: the main button, a chosen option, the tab you are on. */
    val blue: Color,
    val onBlue: Color,
    /** Something said about a set, on: quieter than a choice. */
    val tint: Color,
    val onTint: Color,
    val warn: Color,
    val warnBg: Color,
    /** A weight going up. */
    val up: Color,
    /** A notice that isn't a warning: a build waiting. */
    val note: Color,
    val onNote: Color,
)

private val Light = Palette(
    page = Color(0xFFF7F7F5), card = Color.White, ink = Color(0xFF16181D), muted = Color(0xFF5B606B),
    blue = Color(0xFF1D4ED8), onBlue = Color.White, tint = Color(0xFFDCE6FF), onTint = Color(0xFF12337F),
    warn = Color(0xFF8A4B00), warnBg = Color(0xFFFFF1DB), up = Color(0xFF17753F),
    note = Color(0xFFE8EEFC), onNote = Color(0xFF1E3A8A),
)

private val Dark = Palette(
    page = Color(0xFF111315), card = Color(0xFF1B1E22), ink = Color(0xFFEEF0F3), muted = Color(0xFFA2A8B3),
    blue = Color(0xFF7BA2FF), onBlue = Color(0xFF0B1430), tint = Color(0xFF26345C), onTint = Color(0xFFD6E1FF),
    warn = Color(0xFFFFC98A), warnBg = Color(0xFF3A2A10), up = Color(0xFF6FD39A),
    note = Color(0xFF1C2540), onNote = Color(0xFFC5D4FF),
)

private val LocalPalette = staticCompositionLocalOf { Light }

internal val palette: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/** Light or dark as the phone is set. The light look is Material's own, with the app's colours on top. */
@Composable
fun SpotterTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colours = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = Dark.blue, onPrimary = Dark.onBlue, background = Dark.page, onBackground = Dark.ink,
            surface = Dark.page, onSurface = Dark.ink, onSurfaceVariant = Dark.muted, outline = Color(0xFF5A606B),
        )
    } else {
        lightColorScheme()
    }
    CompositionLocalProvider(LocalPalette provides colours) { MaterialTheme(colorScheme = scheme, content = content) }
}
