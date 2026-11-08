package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

@Composable
internal fun PillBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val h = barHeight()
    val shape = RoundedCornerShape(h / 2)
    Row(
        modifier
            .height(h)
            .shadow(16.dp, shape, ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
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
    Box(
        Modifier
            .size(barHeight())
            .shadow(16.dp, CircleShape, ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(CircleShape)
            .background(ink)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = onInk, modifier = Modifier.size(26.dp))
    }
}

/** Android's speech input in Slovak; [onText] gets what was said. */
@Composable
internal fun rememberSpeechInput(onText: (String) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r -> r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText) }
    return {
        runCatching {
            launcher.launch(
                android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "sk-SK")
            )
        }
    }
}

/**
 * Text field in the pill, ink button beside it: the mic while the field is empty, [submitIcon] once
 * something is typed. Enter submits too.
 */
@Composable
internal fun InputBar(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    @DrawableRes submitIcon: Int,
    submitLabel: String,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Send,
) {
    val text = MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PillBar(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
        }
        Spacer(Modifier.width(10.dp))
        if (value.isBlank()) InkButton(R.drawable.mic_24px, stringResource(R.string.chat_voice), text, onVoice)
        else InkButton(submitIcon, submitLabel, text, onSubmit)
    }
}
