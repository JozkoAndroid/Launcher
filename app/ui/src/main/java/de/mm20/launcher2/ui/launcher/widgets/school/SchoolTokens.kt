package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.theme.transparency.transparency

/**
 * Design tokens for the school blocks (approved plan, see SPEC).
 * Values come from the active Kvaesitso theme; nothing here is a second color system.
 */
internal object SchoolTokens {
    /** Inside a block between related lines. */
    val Tight = 4.dp
    val Gap = 8.dp
    /** Block content inset; the widget column adds the outer 24dp gutter. */
    val Inset = 16.dp
    /** Touch target for every tappable row. */
    val RowMin = 48.dp
    /** Corner of the press highlight; there are no visible surfaces. */
    val PressRadius = 12.dp
    val ProgressHeight = 2.dp

    /** Secondary text is the same color at reduced opacity, never a different hue. */
    const val SecondaryAlpha = 0.72f
    /** Whole block when the feed is older than 12 hours. */
    const val StaleAlpha = 0.6f

    val EnterEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val EnterMs = 200
    const val ExitMs = 160

    /** Fixed-width digits so times and counts don't jitter. */
    val Tabular = TextStyle(fontFeatureSettings = "tnum")
    val TightTracking = (-0.01).em
}

/**
 * Text color for blocks drawn straight on the wallpaper.
 * Near-white (or near-black on light wallpapers), tinted 4% toward the wallpaper hue.
 * With an opaque theme surface, the theme's own onSurface is used instead.
 */
@Composable
internal fun schoolContentColor(): Color {
    if (MaterialTheme.transparency.surface >= 0.5f) return LocalContentColor.current
    val tint = MaterialTheme.colorScheme.primary
    return if (LocalPreferDarkContentOverWallpaper.current) {
        lerp(Color(0xFF15171A), tint, 0.04f)
    } else {
        lerp(Color(0xFFF7F7F8), tint, 0.04f)
    }
}

/**
 * The single accent: the wallpaper's primary hue at the tone that reads on the current content.
 * Light content (dark wallpaper) gets the light tone, dark content the dark tone.
 */
@Composable
internal fun schoolAccent(content: Color): Color {
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.inversePrimary
    val wantLight = content.luminance() > 0.5f
    return if ((a.luminance() > b.luminance()) == wantLight) a else b
}
