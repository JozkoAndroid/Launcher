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
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = SchoolTokens.BlockPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (item in items) {
            when {
                config.style == "glance" -> GlanceChip(item, config)
                config.style == "ring" && item.progress != null -> RingChip(item, config)
                else -> TileChip(item, config)
            }
        }
    }
}

/** Font variants from the bundled Google Sans Flex (roundness and width axes) plus system serif and mono. */
@Composable
internal fun chipFont(font: String): FontFamily? = when (font) {
    "rounded" -> GoogleSansFlex(mapOf("ROND" to 100f))
    "condensed" -> GoogleSansFlex(mapOf("wdth" to 75f))
    "wide" -> GoogleSansFlex(mapOf("wdth" to 120f))
    "serif" -> FontFamily.Serif
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

@Composable
private fun TileChip(item: ContextItem, config: ChipWidgetConfig) {
    val bg = if (config.accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = SchoolTokens.CardAlpha)
    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha)
    val fg = if (config.accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val family = chipFont(config.font)
    CompositionLocalProvider(LocalContentColor provides fg) {
        Column(
            Modifier.width(168.dp).clip(RoundedCornerShape(SchoolTokens.CardRadius)).background(bg).tap(item).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipIcon(item, config, tint = if (config.accent) fg else MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.icon != null && config.icon != "none") Spacer(Modifier.width(10.dp))
                item.label?.let {
                    Text(
                        it, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = SchoolTokens.SecondaryAlpha),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                item.value,
                style = MaterialTheme.typography.headlineSmall.merge(SchoolTokens.Tabular)
                    .copy(fontFamily = family ?: MaterialTheme.typography.headlineSmall.fontFamily, fontWeight = FontWeight.SemiBold),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            item.progress?.let { p ->
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { p }, modifier = Modifier.fillMaxWidth().height(SchoolTokens.ProgressHeight).clip(CircleShape),
                    color = if (config.accent) fg else MaterialTheme.colorScheme.primary,
                    trackColor = fg.copy(alpha = 0.14f), drawStopIndicator = {},
                )
            }
            if (item.onTogglePlay != null && item.playing != null) PlayButton(item)
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
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            Modifier.clip(RoundedCornerShape(SchoolTokens.CardRadius)).background(bg).tap(item).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { p }, modifier = Modifier.size(56.dp), strokeWidth = 6.dp, strokeCap = StrokeCap.Round,
                    color = if (config.accent) fg else MaterialTheme.colorScheme.primary, trackColor = fg.copy(alpha = 0.14f),
                    gapSize = 0.dp,
                )
                if (config.icon != "none") item.icon?.let { Icon(painterResource(it), null, Modifier.size(22.dp)) }
                else Text("${(p * 100).toInt()} %", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    item.value,
                    style = MaterialTheme.typography.titleLarge.merge(SchoolTokens.Tabular)
                        .copy(fontFamily = family ?: MaterialTheme.typography.titleLarge.fontFamily, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                item.label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = SchoolTokens.SecondaryAlpha), maxLines = 1) }
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
    "test" -> ContextItem("s", "", R.drawable.quiz_24px, value = "Bio", label = stringResource(R.string.glance_test_tomorrow))
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
}
