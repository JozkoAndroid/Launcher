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
 * Design tokens for the school blocks. Redesign after the device test ("text after text"):
 * each block is one calm card (tinted surface, no border, no shadow), with subject badges and
 * meaningful icons. Colors come from the active Kvaesitso theme; subject hues carry information.
 */
internal object SchoolTokens {
    val Tight = 4.dp
    val Gap = 8.dp
    /** Inside a card; with the widget column's 8dp the text sits 24dp from the screen edge. */
    val Inset = 16.dp
    /** Space above/below each card; with the column's 8dp gap, cards sit 12dp apart. */
    val BlockPad = 2.dp
    val RowMin = 48.dp

    /**
     * Radius scale (round 7 redesign): sheet ends 28, joined edges 8, inner items 14, badges 12.
     * Blocks that follow each other join into one sheet with a 3dp seam; other blocks sit 16dp apart.
     */
    val CardRadius = 28.dp
    val JoinRadius = 8.dp
    val Seam = 3.dp
    val GroupGap = 16.dp
    val ItemRadius = 14.dp
    val BadgeRadius = 12.dp
    val PressRadius = 14.dp

    val BadgeLarge = 44.dp
    val BadgeSmall = 36.dp
    val ProgressHeight = 4.dp

    /** Card fill over the wallpaper: opaque enough for body text contrast on any wallpaper. */
    const val CardAlpha = 0.86f
    const val SecondaryAlpha = 0.72f
    const val StaleAlpha = 0.6f

    val EnterEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val EnterMs = 200
    const val ExitMs = 160

    val Tabular = TextStyle(fontFeatureSettings = "tnum")
    val TightTracking = (-0.01).em
}

/** Where a block sits in a joined sheet: [joinedTop] / [joinedBottom] = a sheet block directly above / below. */
data class Segment(val joinedTop: Boolean = false, val joinedBottom: Boolean = false)

val LocalSegment = androidx.compose.runtime.staticCompositionLocalOf { Segment() }

@Composable
internal fun segmentShape(): androidx.compose.foundation.shape.RoundedCornerShape {
    val s = LocalSegment.current
    val top = if (s.joinedTop) SchoolTokens.JoinRadius else SchoolTokens.CardRadius
    val bottom = if (s.joinedBottom) SchoolTokens.JoinRadius else SchoolTokens.CardRadius
    return androidx.compose.foundation.shape.RoundedCornerShape(top, top, bottom, bottom)
}

/**
 * Type pairing (round 7): a wide cut of the bundled Google Sans Flex for the big moments (current
 * lesson, page titles), a serif for the daily brief (reads like a newspaper), the theme sans for the rest.
 */
internal object SchoolFonts {
    val Display: androidx.compose.ui.text.font.FontFamily by lazy {
        de.mm20.launcher2.ui.theme.typography.fontfamily.GoogleSansFlex(mapOf("wdth" to 112f, "opsz" to 48f))
    }
    val Serif: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Serif

    fun display(size: Int, weight: Int = 500) = TextStyle(
        fontFamily = Display, fontSize = androidx.compose.ui.unit.TextUnit(size.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(size * 1.12f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = androidx.compose.ui.text.font.FontWeight(weight), letterSpacing = (-0.02).em,
    )
}

/** Text color for things drawn straight on the wallpaper (only the Gemini pill now). */
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

/** The single accent: the wallpaper's primary hue at the tone that reads on the current content. */
@Composable
internal fun schoolAccent(content: Color): Color {
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.inversePrimary
    val wantLight = content.luminance() > 0.5f
    return if ((a.luminance() > b.luminance()) == wantLight) a else b
}

/** Background and text color of a subject badge. */
internal data class SubjectColors(val container: Color, val onContainer: Color)

/**
 * Each subject gets one muted hue so it is recognisable at a glance (information, not decoration).
 * Stable per subject name; tuned to the card's light or dark surface.
 */
@Composable
internal fun subjectColors(subject: String): SubjectColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    if (subject.isBlank()) {
        return SubjectColors(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    // 10 hues spaced around the wheel, chosen to stay calm next to each other.
    val hues = floatArrayOf(205f, 145f, 25f, 280f, 50f, 175f, 330f, 100f, 235f, 0f)
    // Device-test fix: a hash let two subjects share a color; subjects of the timetable get distinct slots.
    val slot = SubjectPalette.index(subject) ?: subject.lowercase().hashCode()
    val h = hues[Math.floorMod(slot, hues.size)]
    return if (dark) {
        SubjectColors(Color.hsl(h, 0.32f, 0.27f), Color.hsl(h, 0.55f, 0.84f))
    } else {
        SubjectColors(Color.hsl(h, 0.45f, 0.88f), Color.hsl(h, 0.5f, 0.28f))
    }
}

/** "Mathe" → "Ma", "Slovakisch" → "Sl"; empty for no subject. */
internal fun subjectTag(subject: String): String =
    subject.trim().take(2).replaceFirstChar { it.titlecase() }

/** Gives every subject in the current feed its own color slot (alphabetical, stable while the timetable is). */
internal object SubjectPalette {
    @Volatile private var slots: Map<String, Int> = emptyMap()

    fun register(subjects: Collection<String>) {
        val sorted = subjects.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().sorted()
        if (sorted != slots.keys.sortedBy { slots[it] }) slots = sorted.withIndex().associate { (i, s) -> s to i }
    }

    fun index(subject: String): Int? = slots[subject.trim().lowercase()]
}
