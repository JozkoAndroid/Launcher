package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.typography.fontfamily.GoogleSansFlex
import de.mm20.launcher2.widgets.ChipWidgetConfig

/**
 * Looks for a single chip block (device test: "different fonts, boxes, styles, icons"):
 * pill = the "Práve teraz" chip; tile = small card with a big value; glance = no background, large type
 * on the wallpaper; ring = progress ring for data and steps (others fall back to tile).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipBlock(items: List<ContextItem>, config: ChipWidgetConfig) {
    if (config.style == "pill") {
        ContextContent(items.map { if (config.icon == "none") it.copy(icon = null) else it })
        return
    }
    val one: @Composable (ContextItem) -> Unit = { item ->
        when {
            config.style == "glance" -> GlanceChip(item, config)
            config.style == "ring" && item.progress != null -> RingChip(item, config)
            else -> TileChip(item, config)
        }
    }
    // In the tile grid each block is one cell (a column keeps its own width); on its own it wraps in a row.
    if (LocalTileWidth.current != null) {
        Column { for (item in items) one(item) }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = SchoolTokens.BlockPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in items) one(item)
    }
}

/** Width of one tile when single-value blocks share rows (set by [ChipTiles]); null = block on its own. */
val LocalTileWidth = androidx.compose.runtime.staticCompositionLocalOf<Dp?> { null }

/** True inside the instrument panel ([ChipTiles]): tiles draw no box of their own. */
val LocalInPanel = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Where the panel's hairlines go, filled during layout and read when drawing (same frame). */
private class PanelLines {
    var cols = 0
    var colPx = 0
    var rows: List<Pair<Int, Int>> = emptyList() // (y, cells in the row)
    var rowHeights: List<Int> = emptyList()
}

/**
 * Consecutive single-value blocks (name day, data, steps, ...) as one instrument panel (round 21, design
 * language "instrument panel"): a single surface instead of a box per value, the cells parted by hairlines,
 * the numbers as the heroes. Equal cells, the same in every row (round 15: a hidden tile no longer leaves a
 * gap; it takes no cell). Empty values take no cell; the last row starts at the left.
 */
@Composable
fun ChipTiles(
    widgets: List<de.mm20.launcher2.widgets.Widget>,
    modifier: Modifier = Modifier,
    item: @Composable (de.mm20.launcher2.widgets.Widget) -> Unit,
) {
    val lines = remember { PanelLines() }
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val surface = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // 2 cells across on narrow screens (cover), 3 from 450dp (phone, unfolded, tablet column).
        val cols = (maxWidth / 150.dp).toInt().coerceIn(2, 3)
        val w = maxWidth / cols
        CompositionLocalProvider(LocalTileWidth provides w, LocalInPanel provides true) {
            androidx.compose.ui.layout.Layout(
                modifier = Modifier
                    .clip(RoundedCornerShape(SchoolTokens.CardRadius))
                    .background(surface)
                    .drawWithContent {
                        drawContent()
                        val stroke = 1.dp.toPx()
                        val inset = 18.dp.toPx()
                        lines.rows.forEachIndexed { ri, (y, n) ->
                            val h = lines.rowHeights[ri]
                            // Between cells of a row: short vertical hairlines, inset from top and bottom.
                            for (c in 1 until n) {
                                val x = (c * lines.colPx).toFloat()
                                drawLine(hairline, androidx.compose.ui.geometry.Offset(x, y + inset), androidx.compose.ui.geometry.Offset(x, y + h - inset), stroke)
                            }
                            // Between rows: one hairline, inset from the panel's sides.
                            if (ri > 0) drawLine(hairline, androidx.compose.ui.geometry.Offset(inset, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width - inset, y.toFloat()), stroke)
                        }
                    },
                content = { for (x in widgets) androidx.compose.runtime.key(x.id) { item(x) } },
            ) { measurables, constraints ->
                val colPx = constraints.maxWidth / cols
                val shown = measurables
                    .map { it.measure(androidx.compose.ui.unit.Constraints(minWidth = colPx, maxWidth = colPx)) }
                    .filter { it.height > 0 }
                val rows = shown.chunked(cols)
                val rowHeights = rows.map { r -> r.maxOf { it.height } }
                val height = rowHeights.sum()
                var y = 0
                lines.cols = cols
                lines.colPx = colPx
                lines.rowHeights = rowHeights
                lines.rows = rows.mapIndexed { ri, r -> (y to r.size).also { y += rowHeights[ri] } }
                layout(constraints.maxWidth, height) {
                    var top = 0
                    rows.forEachIndexed { ri, r ->
                        r.forEachIndexed { ci, p -> p.placeRelative(ci * colPx, top) }
                        top += rowHeights[ri]
                    }
                }
            }
        }
    }
}

/** The test countdown as dots: one per day left (up to 14), today's in the accent. */
@Composable
private fun CountdownDots(days: Int, ink: Color) {
    val shown = (days + 1).coerceIn(1, 14)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until shown) {
            Box(
                Modifier.size(6.dp).clip(CircleShape)
                    .background(if (i == shown - 1) MaterialTheme.colorScheme.primary else ink.copy(alpha = 0.28f))
            )
        }
    }
}

/** Font variants from the bundled Google Sans Flex (roundness and width axes) plus system serif and mono. */
@Composable
internal fun chipFont(font: String): FontFamily? = when (font) {
    "rounded" -> GoogleSansFlex(mapOf("ROND" to 100f))
    "condensed" -> GoogleSansFlex(mapOf("wdth" to 75f))
    "wide" -> GoogleSansFlex(mapOf("wdth" to 120f))
    "serif" -> SchoolFonts.SerifDisplay
    "mono" -> FontFamily.Monospace
    else -> null
}

@Composable
private fun ChipIcon(item: ContextItem, config: ChipWidgetConfig, tint: Color, size: Int = 20) {
    val icon = item.icon ?: return
    when (config.icon) {
        "none" -> return
        "circle" -> Box(
            Modifier.size((size + 16).dp).clip(CircleShape)
                .background(if (config.accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(size.dp)) }
        else -> Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(size.dp))
    }
}

private fun Modifier.tap(item: ContextItem) = if (item.onClick != null) clickable(role = Role.Button, onClick = item.onClick) else this

/**
 * Tile: the value is the hero (display cut by default, or the chosen font), the label sits under it,
 * the icon top-left. A thin bar when the value has a goal (data, steps).
 */
@Composable
private fun TileChip(item: ContextItem, config: ChipWidgetConfig) {
    val bg = if (config.accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = SchoolTokens.CardAlpha)
    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha)
    val fg = if (config.accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val family = chipFont(config.font) ?: SchoolFonts.Display
    // Narrow grid tiles (5 across when unfolded) get a smaller value so names like "Bohumír" fit.
    val narrow = (LocalTileWidth.current ?: 200.dp) < 180.dp
    val size = when {
        config.font == "serif" -> if (narrow) 26 else 30
        else -> if (narrow) 26 else 30
    }
    val panel = LocalInPanel.current
    CompositionLocalProvider(LocalContentColor provides fg) {
        Column(
            Modifier
                .width(LocalTileWidth.current ?: 168.dp)
                .then(
                    // In the panel: no box of its own, the panel is the surface; the press shows in the cell.
                    if (panel) Modifier.height(136.dp).tap(item).padding(horizontal = 18.dp, vertical = 16.dp)
                    else Modifier.height(160.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp)).background(bg).tap(item).padding(16.dp)
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipIcon(item, config, tint = if (config.accent) fg else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (item.onTogglePlay != null && item.playing != null) PlayButton(item)
            }
            Column {
                Spacer(Modifier.height(12.dp))
                Text(
                    item.value,
                    style = androidx.compose.ui.text.TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = (size + 4).sp, fontWeight = FontWeight.Medium)
                        .merge(SchoolTokens.Tabular),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                item.label?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = SchoolTokens.SecondaryAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.days?.let { d ->
                    Spacer(Modifier.height(10.dp))
                    CountdownDots(d, fg)
                }
                item.progress?.let { p ->
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { p }, modifier = Modifier.fillMaxWidth().height(if (panel) 2.dp else SchoolTokens.ProgressHeight).clip(CircleShape),
                        color = if (config.accent) fg else MaterialTheme.colorScheme.primary,
                        trackColor = fg.copy(alpha = 0.14f), drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

/** At-a-glance look: straight on the wallpaper, large value, soft shadow for legibility on bright photos. */
@Composable
private fun GlanceChip(item: ContextItem, config: ChipWidgetConfig) {
    val content = schoolContentColor()
    val accent = schoolAccent(content)
    val shadow = Shadow(Color.Black.copy(alpha = if (content.red > 0.5f) 0.35f else 0f), blurRadius = 8f)
    val family = chipFont(config.font)
    CompositionLocalProvider(LocalContentColor provides content) {
        Row(
            Modifier.clip(RoundedCornerShape(SchoolTokens.ItemRadius)).tap(item).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChipIcon(item, config, tint = if (config.accent) accent else content, size = 26)
            if (item.icon != null && config.icon != "none") Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    item.value,
                    style = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, shadow = shadow,
                        fontFamily = family ?: MaterialTheme.typography.displaySmall.fontFamily).merge(SchoolTokens.Tabular),
                    color = if (config.accent) accent else content,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                item.label?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow), color = content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (item.onTogglePlay != null && item.playing != null) PlayButton(item)
        }
    }
}

@Composable
private fun RingChip(item: ContextItem, config: ChipWidgetConfig) {
    val p = item.progress ?: return
    val bg = if (config.accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = SchoolTokens.CardAlpha)
    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha)
    val fg = if (config.accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val family = chipFont(config.font)
    val tileWidth = LocalTileWidth.current
    val ring = @Composable {
        Box(contentAlignment = Alignment.Center) {
            // Instrument ring: thin, the same weight as the hairlines' family.
            val panel = LocalInPanel.current
            CircularProgressIndicator(
                progress = { p }, modifier = Modifier.size(if (panel) 44.dp else 56.dp), strokeWidth = if (panel) 3.dp else 6.dp, strokeCap = StrokeCap.Round,
                color = if (config.accent) fg else MaterialTheme.colorScheme.primary, trackColor = fg.copy(alpha = 0.14f),
                gapSize = 0.dp,
            )
            if (config.icon != "none") item.icon?.let { Icon(painterResource(it), null, Modifier.size(if (LocalInPanel.current) 18.dp else 22.dp)) }
            else Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
        }
    }
    val valueStyle = androidx.compose.ui.text.TextStyle(fontFamily = family ?: SchoolFonts.Display, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
        .merge(SchoolTokens.Tabular)
    CompositionLocalProvider(LocalContentColor provides fg) {
        if (tileWidth != null) {
            // In the grid: ring on top, the value and its label under it, same height as the other tiles.
            val panel = LocalInPanel.current
            Column(
                Modifier.width(tileWidth).then(
                    if (panel) Modifier.height(136.dp).tap(item).padding(horizontal = 18.dp, vertical = 16.dp)
                    else Modifier.height(160.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp)).background(bg).tap(item).padding(16.dp)
                ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ring()
                    Spacer(Modifier.weight(1f))
                    Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.labelLarge.merge(SchoolTokens.Tabular), color = fg.copy(alpha = SchoolTokens.SecondaryAlpha))
                }
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(item.value, style = valueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = SchoolTokens.SecondaryAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        } else {
            Row(
                Modifier.clip(RoundedCornerShape(SchoolTokens.CardRadius)).background(bg).tap(item).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ring()
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(item.value, style = valueStyle, maxLines = 1)
                    item.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = SchoolTokens.SecondaryAlpha), maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun PlayButton(item: ContextItem) {
    val playing = item.playing ?: return
    IconButton(onClick = { item.onTogglePlay?.invoke() }) {
        Icon(
            painterResource(if (playing) R.drawable.pause_24px else R.drawable.play_arrow_24px),
            contentDescription = stringResource(if (playing) R.string.context_pause else R.string.context_play),
        )
    }
}

/** Sample values so the settings sheet can preview a style even when the card has no value right now. */
@Composable
private fun sampleItem(kind: String): ContextItem = when (kind) {
    "data" -> ContextItem("s", "", R.drawable.data_usage_24px, value = "2,9 GB", label = stringResource(R.string.glance_data_of, "3,5"), progress = 0.83f)
    "steps" -> ContextItem("s", "", R.drawable.directions_walk_24px, value = "3 120", label = stringResource(R.string.glance_steps_goal, "8 000"), progress = 0.39f)
    "instagram" -> ContextItem("s", "", R.drawable.timer_24px, value = "47 min", label = stringResource(R.string.glance_instagram))
    "nameday" -> ContextItem("s", "", R.drawable.celebration_24px, value = "René", label = stringResource(R.string.chip_name_nameday))
    "birthday" -> ContextItem("s", "", R.drawable.cake_24px, value = "Jana", label = stringResource(R.string.chip_name_birthday))
    "lunch" -> ContextItem("s", "", R.drawable.restaurant_24px, value = stringResource(R.string.glance_menu, "2"), label = stringResource(R.string.chip_name_lunch))
    "test" -> ContextItem("s", "", R.drawable.history_edu_24px, value = "Bio", label = stringResource(R.string.glance_test_tomorrow))
    "chats" -> ContextItem("s", "", R.drawable.chat_24px, value = "3", label = stringResource(R.string.glance_chats))
    else -> ContextItem("s", "", R.drawable.music_note_24px, value = "Bohemian Rhapsody", label = "Queen")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.ConfigureChipWidget(
    widget: de.mm20.launcher2.widgets.ChipWidget,
    onWidgetUpdated: (de.mm20.launcher2.widgets.ChipWidget) -> Unit,
) {
    val c = widget.config
    fun set(n: ChipWidgetConfig) = onWidgetUpdated(widget.copy(config = n))

    // Live preview on a neutral backdrop.
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SchoolTokens.CardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(16.dp),
    ) { ChipBlock(listOf(sampleItem(widget.kind)), c) }
    Spacer(Modifier.height(16.dp))

    @Composable
    fun Options(title: Int, options: List<Pair<String, Int>>, selected: String, onPick: (String) -> Unit) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in options) {
                FilterChip(selected = selected == value, onClick = { onPick(value) }, label = { Text(stringResource(label)) })
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    Options(
        R.string.chip_style, buildList {
            add("pill" to R.string.chip_style_pill); add("tile" to R.string.chip_style_tile); add("glance" to R.string.chip_style_glance)
            if (widget.kind == "data" || widget.kind == "steps") add("ring" to R.string.chip_style_ring)
        }, c.style,
    ) { set(c.copy(style = it)) }
    if (c.style != "pill") {
        Options(
            R.string.chip_font, listOf(
                "theme" to R.string.chip_font_theme, "rounded" to R.string.chip_font_rounded, "condensed" to R.string.chip_font_condensed,
                "wide" to R.string.chip_font_wide, "serif" to R.string.chip_font_serif, "mono" to R.string.chip_font_mono,
            ), c.font,
        ) { set(c.copy(font = it)) }
    }
    Options(
        R.string.chip_icon, listOf("plain" to R.string.chip_icon_plain, "circle" to R.string.chip_icon_circle, "none" to R.string.chip_icon_none), c.icon,
    ) { set(c.copy(icon = it)) }
    if (c.style != "pill") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { set(c.copy(accent = !c.accent)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chip_accent), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = c.accent, onCheckedChange = null)
        }
    }
    if (widget.kind == "nameday") {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { set(c.copy(onlyContacts = !c.onlyContacts)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chip_only_contacts), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = c.onlyContacts, onCheckedChange = null)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.chip_hidden_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SchoolSettingsLink()
}

/** Accounts, keys and permissions live in one place; block settings point there. */
@Composable
fun SchoolSettingsLink() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Text(stringResource(R.string.school_settings_where), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    androidx.compose.material3.TextButton(onClick = { Links.openSchoolSettings(context) }) { Text(stringResource(R.string.school_settings_open)) }
}
