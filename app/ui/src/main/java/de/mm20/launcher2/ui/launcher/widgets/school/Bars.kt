package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mm20.launcher2.ui.R

/*
 * One bar design for the dock and every input bar (round 10, device test: "unify the dock and the
 * chat box"): a raised pill and, next to it, a round ink button that is the screen's one primary action.
 * Same height, same curvature, same shadow everywhere; bigger on wide screens.
 */

private val ShadowTint = Color(0xFF0A0C10)

@Composable
internal fun barHeight(): Dp = if (LocalConfiguration.current.screenWidthDp >= 600) 76.dp else 64.dp

/**
 * The pill's surface, shared by the dock, every input bar and the search bar (round 15, device test: "the
 * search bar is not a pill, the chat bar on the left looks different from the search bar on the right"):
 * the raised surface a step off the page behind it, a soft tinted shadow and a hairline outline, so the
 * pill reads as one object on any wallpaper or page.
 */
@Composable
internal fun Modifier.pillSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    return this
        .shadow(16.dp, shape, ambientColor = ShadowTint, spotColor = ShadowTint)
        .clip(shape)
        .background(surface.copy(alpha = 0.94f))
        .border(1.dp, pillOutline(), shape)
}

/** Hairline on the pill: white at 12% on dark surfaces, black at 8% on light ones. */
@Composable
internal fun pillOutline(): Color =
    if (MaterialTheme.colorScheme.surfaceContainerHigh.luminance() < 0.5f) Color.White.copy(alpha = 0.12f)
    else Color.Black.copy(alpha = 0.08f)

@Composable
internal fun PillBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val h = barHeight()
    val shape = RoundedCornerShape(h / 2)
    Row(
        modifier
            .height(h)
            .pillSurface(shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Ink: near-white on dark content, near-black on light ([base] is the color text has on this surface). */
@Composable
internal fun InkButton(@DrawableRes icon: Int, label: String, base: Color, onClick: () -> Unit) {
    val light = base.luminance() > 0.5f
    val ink = if (light) Color(0xFFF4F4F6) else Color(0xFF16181C)
    val onInk = if (light) Color(0xFF16181C) else Color(0xFFF4F4F6)
    // Pressed: a small squeeze, the same answer to a tap everywhere.
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.94f else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 900f), label = "press",
    )
    Box(
        Modifier
            .size(barHeight())
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(16.dp, CircleShape, ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(CircleShape)
            .background(ink)
            .border(1.dp, if (light) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = onInk, modifier = Modifier.size(26.dp))
    }
}

/**
 * Text field in the pill, ink button beside it: the mic while the field is empty, [submitIcon] once
 * something is typed, stop while listening. Enter submits too. The hint shows [voice]'s status
 * ("Počúvam…", or why nothing was heard).
 */
@Composable
internal fun InputBar(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    @DrawableRes submitIcon: Int,
    submitLabel: String,
    onSubmit: () -> Unit,
    voice: SpeechInput,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Send,
) {
    val text = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PillBar(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                if (value.isEmpty()) Text(voice.status ?: hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                BasicTextField(
                    value, onValue,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = text, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }, onDone = { onSubmit() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // Round 34 (user): an X empties the field, only while there is text.
            if (value.isNotEmpty() && !voice.listening) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.action_clear)) { onValue("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.close_24px), stringResource(R.string.action_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            // Round 33: the dictation language, a tap switches SK, DE, EN (also while listening).
            if (value.isEmpty() || voice.listening) {
                Text(
                    SpeechLanguage.label,
                    style = MaterialTheme.typography.labelMedium.merge(SchoolTokens.Tabular),
                    color = if (voice.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.voice_language)) { voice.nextLanguage() }
                        .wrapContentHeight(),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            voice.listening -> InkButton(R.drawable.stop_24px, stringResource(R.string.voice_stop), text, voice::toggle)
            value.isBlank() -> InkButton(R.drawable.mic_24px, stringResource(R.string.chat_voice), text, voice::toggle)
            else -> InkButton(submitIcon, submitLabel, text, onSubmit)
        }
    }
}

/**
 * Connected segments, the selected one in ink; one choice, one control ("Úloha | Opýtaj sa", task kinds).
 * The pill surface like the bars. [equal]: segments share the width evenly (round 15, device test: on
 * "Môj deň" the selected box changed size with its label).
 */
@Composable
internal fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    equal: Boolean = false,
) {
    Row(modifier.pillSurface(CircleShape).padding(4.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .then(if (equal) Modifier.weight(1f) else Modifier)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/*
 * One motion for every bottom pill (round 14, device test: "make all pills consistent"), the same curve
 * the search pill uses: a pill arriving with its page sits D·(1−p)² below its place, a pill leaving
 * with its page D·p² (p = how far the page swipe has gone). D = 128dp plus the navigation bar.
 */
@Composable
internal fun pillTravelPx(): Float {
    val nav = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    return with(androidx.compose.ui.platform.LocalDensity.current) { (128.dp + nav).toPx() }
}

/**
 * The pill at [shown] (0 = away, 1 = in place): it rises along the curve above and grows from 92% to full
 * size, landing softly (round 15, device test: "a bit better animation"). It is fully opaque from the
 * first third of the way on, so going back still mirrors coming in (round 14).
 */
internal fun androidx.compose.ui.graphics.GraphicsLayerScope.pillAt(travel: Float, shown: Float) {
    val p = shown.coerceIn(0f, 1f)
    val away = 1 - p
    translationY = travel * away * away
    val s = 0.92f + 0.08f * (1 - away * away * away)
    scaleX = s
    scaleY = s
    alpha = (p * 3f).coerceAtMost(1f)
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
}

/** For a pill on a page that comes in with progress [progress]. */
internal fun Modifier.pillArriving(travel: Float, progress: () -> Float) =
    graphicsLayer { pillAt(travel, progress()) }

/** For a pill on home, leaving as another page comes in with progress [progress]. */
internal fun Modifier.pillLeaving(travel: Float, progress: () -> Float) =
    graphicsLayer { pillAt(travel, 1 - progress()) }
