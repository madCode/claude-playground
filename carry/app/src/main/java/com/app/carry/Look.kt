package com.app.carry

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Chalk on slate, colour-coded. See docs/DESIGN.md for why each colour
 * exists; the rule that matters in code is that the two colour systems
 * never swap jobs.
 */
object Look {
    val Slate = Color(0xFF16181A)
    val SlateRaised = Color(0xFF1F2225)
    val Rule = Color(0xFF262A2D)
    val RuleStrong = Color(0xFF3A3F44)
    val Chalk = Color(0xFFECEAE4)
    val ChalkDim = Color(0xFF8D8D86)
    val ChalkFaint = Color(0xFF80817B)

    /** Means exactly one thing: this weight moved. Never used for anything else. */
    val Amber = Color(0xFFE0A33E)
    val AmberInk = Color(0xFFF3DCB4)
    val AmberDim = Color(0xFF9A8558)

    /** A warning, not an accent. */
    val Brick = Color(0xFFB4553F)
    val BrickInk = Color(0xFFD5826E)

    /** What kind of movement it is. Lives on a 3dp edge, never on a number. */
    private val Legs = Color(0xFF8AD17F)
    private val Push = Color(0xFF5FA8E8)
    private val Pull = Color(0xFFC98BE0)
    private val CoreC = Color(0xFF4FC7C0)
    private val Combo = Color(0xFFF0866E)

    /** Unknown families get no edge rather than a wrong one -- the sheet is
     *  built from a knowledge base that is still missing exercises, and a
     *  confidently mis-coloured row is worse than an uncoloured one. */
    fun family(name: String?): Color? = when (name) {
        "legs" -> Legs
        "push" -> Push
        "pull" -> Pull
        "core" -> CoreC
        "combo" -> Combo
        else -> null
    }

    val families = listOf(
        "legs" to Legs, "push" to Push, "pull" to Pull, "core" to CoreC, "combo" to Combo,
    )

    // Bundled rather than system faces, and rather than downloadable ones:
    // this app is opened in a lobby with the phone possibly offline, and a
    // font that has not arrived yet would reflow the one screen whose whole
    // job is to be read in a glance.
    //
    // Barlow Condensed for names, because they get long -- "single-arm row
    // · split stance" -- and must not wrap. IBM Plex Mono for figures, for
    // tabular digits that keep their width as they change. Both OFL; the
    // licences are in licenses/.
    val Name = FontFamily(
        Font(R.font.barlow_condensed_medium, FontWeight.Medium),
        Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    )
    val Figure = FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )

    val NameSize = 15.sp
    val FigureSize = 18.sp
    val SmallSize = 11.sp
}
