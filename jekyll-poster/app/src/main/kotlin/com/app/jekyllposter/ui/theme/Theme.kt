package com.app.jekyllposter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.jekyllposter.R

/** The small touches beyond Material's tokens: a mark beside headings, words for empty places. */
data class Whimsy(
    /** Shown before section headings; empty for none. */
    val mark: String = "",
    /** A wavy line under section headings. */
    val squiggle: Boolean = false,
    val rows: Rows = Rows.Lines,
    /** Colours for categories and tags, picked by name, for light and dark backgrounds. */
    val palette: List<Color> = emptyList(),
    val paletteDark: List<Color> = emptyList(),
    /** The New post button as a stamp, its tilt in degrees, and its ink (fill) and text, light and dark. */
    val stamp: Boolean = false,
    val tilt: Float = 0f,
    val stampInk: Color? = null,
    val stampInkDark: Color? = null,
    val stampText: Color = Color(0xFF2B1B17),
    val emptyBlog: String = "No posts yet. Your first one is a tap away.",
    val live: String = "live on the site",
    /** Decoration after [live]; TalkBack doesn't read it. */
    val liveMark: String = "",
)

val LocalWhimsy = staticCompositionLocalOf { Whimsy() }

/** A look for the whole app: colours for light and dark, type, shapes, and its [Whimsy]. */
data class PosterStyle(
    val name: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val headings: FontFamily = FontFamily.Default,
    val body: FontFamily = FontFamily.Default,
    val corner: Int = 12,
    val whimsy: Whimsy = Whimsy(),
)

private fun typography(headings: FontFamily, body: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.h() = copy(fontFamily = headings)
    fun TextStyle.b() = copy(fontFamily = body)
    return base.copy(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(), headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.h(), titleMedium = base.titleMedium.b().copy(fontWeight = FontWeight.SemiBold), titleSmall = base.titleSmall.h(),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b(), labelMedium = base.labelMedium.b(), labelSmall = base.labelSmall.b(),
    )
}

/**
 * A variable font at [weight] and optical size [opsz]. Without the settings Android takes the
 * file's defaults, which for Bricolage are its heaviest weight at headline size: small text then
 * gets shapes drawn for 96pt and reads poorly.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, opsz: Float) = Font(
    res, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opsz)),
)

object PosterStyles {
    val Classic = PosterStyle(
        name = "classic",
        light = lightColorScheme(
            primary = Color(0xFFB5341C), onPrimary = Color.White, primaryContainer = Color(0xFFFFDAD3), onPrimaryContainer = Color(0xFF3E0500),
            secondary = Color(0xFF6F5A53), secondaryContainer = Color(0xFFF8DDD5),
            background = Color(0xFFFFFBF7), surface = Color(0xFFFFFBF7), surfaceContainer = Color(0xFFF6EFEA), surfaceContainerHigh = Color(0xFFF0E8E3),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB4A5), onPrimary = Color(0xFF631000), primaryContainer = Color(0xFF8C1F0A), onPrimaryContainer = Color(0xFFFFDAD3),
            secondary = Color(0xFFE7BDB3), secondaryContainer = Color(0xFF574238),
            background = Color(0xFF1A1110), surface = Color(0xFF1A1110), surfaceContainer = Color(0xFF271D1B), surfaceContainerHigh = Color(0xFF322826),
        ),
    )

    /**
     * The app's look, chosen over three design rounds (DEVLOG, cycle 7): a risograph zine. Cream
     * paper, fluoro pink and teal inks, a quirky grotesque, squiggles under headings, dotted rules,
     * and a tilted stamp of a New post button printed slightly out of register.
     */
    val Zine = PosterStyle(
        name = "zine",
        light = lightColorScheme(
            // Deep pink for text (4.5:1 on the cream); the fluoro pink is the stamp's ink.
            primary = Color(0xFFC2185B), onPrimary = Color.White, primaryContainer = Color(0xFFFFD6E3), onPrimaryContainer = Color(0xFF3F0019),
            secondary = Color(0xFF00898C), onSecondary = Color.White, secondaryContainer = Color(0xFFBDF0EE), onSecondaryContainer = Color(0xFF00201F),
            background = Color(0xFFFFF6E5), surface = Color(0xFFFFF6E5), onBackground = Color(0xFF2B1B17), onSurface = Color(0xFF2B1B17),
            onSurfaceVariant = Color(0xFF5E4B40), outline = Color(0xFF2B1B17),
            surfaceContainer = Color(0xFFFBEBD0), surfaceContainerHigh = Color(0xFFF6E2C2), surfaceContainerLow = Color(0xFFFFF6E5), outlineVariant = Color(0xFFE7CFA8),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFF8FB4), onPrimary = Color(0xFF5E0029), primaryContainer = Color(0xFF8A1A47),
            secondary = Color(0xFF6FD8D6), secondaryContainer = Color(0xFF00504F),
            background = Color(0xFF1E1714), surface = Color(0xFF1E1714), onSurface = Color(0xFFF6E8DC), onSurfaceVariant = Color(0xFFD9C3B4),
            outline = Color(0xFFF6E8DC),
            surfaceContainer = Color(0xFF2A211C), surfaceContainerHigh = Color(0xFF352B25), surfaceContainerLow = Color(0xFF1E1714),
        ),
        headings = FontFamily(variable(R.font.bricolage, 700, 36f)),
        body = FontFamily(variable(R.font.bricolage, 400, 14f), variable(R.font.bricolage, 600, 14f), variable(R.font.bricolage, 700, 14f)),
        corner = 18,
        whimsy = Whimsy(
            mark = "✶ ", squiggle = true, rows = Rows.Dots, stamp = true, tilt = -3f,
            stampInk = Color(0xFFFF4F8B), stampInkDark = Color(0xFFFF7AA8),
            palette = listOf(Color(0xFFFFC2D6), Color(0xFFB9ECEA), Color(0xFFFFE08F), Color(0xFFC9D5FF), Color(0xFFFFCFA8)),
            // Deep riso inks, each at least 4.5:1 under the light text.
            paletteDark = listOf(Color(0xFF9E1F52), Color(0xFF006A6C), Color(0xFF6E5300), Color(0xFF3F4AA8), Color(0xFF8C3B00)),
            emptyBlog = "Nothing printed yet. Fresh paper, fresh ink.", live = "out in the world", liveMark = " ✶",
        ),
    )




    /** What design rounds (DesignRoundTest) render: the app's look and the original for comparison. */
    val all = listOf(Classic, Zine)
}

@Composable
fun PosterTheme(dark: Boolean = isSystemInDarkTheme(), style: PosterStyle = PosterStyles.Zine, content: @Composable () -> Unit) {
    val c = style.corner.dp
    MaterialTheme(
        colorScheme = if (dark) style.dark else style.light,
        typography = typography(style.headings, style.body),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(c / 3), small = RoundedCornerShape(c / 2), medium = RoundedCornerShape(c),
            large = RoundedCornerShape(c * 1.25f), extraLarge = RoundedCornerShape(c * 1.5f),
        ),
    ) {
        CompositionLocalProvider(LocalWhimsy provides style.whimsy, content = content)
    }
}
