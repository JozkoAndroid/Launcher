package de.mm20.launcher2.ui.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import de.mm20.launcher2.ui.launcher.widgets.school.pillSurface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.preferences.SearchBarStyle
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.layout.BottomReversed
import de.mm20.launcher2.ui.theme.transparency.transparency

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    style: SearchBarStyle,
    level: SearchBarLevel,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocus: () -> Unit = {},
    onUnfocus: () -> Unit = {},
    reverse: Boolean = false,
    darkColors: Boolean = false,
    readOnly: Boolean = false,
    menu: @Composable RowScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
    onKeyboardActionGo: (KeyboardActionScope.() -> Unit)? = null
) {

    val transition = updateTransition(level, label = "Searchbar")
    val context = LocalContext.current

    val contentColor by transition.animateColor(label = "textColor",
        transitionSpec = {
            when {
                initialState == SearchBarLevel.Resting -> tween(durationMillis = 200)
                targetState == SearchBarLevel.Resting -> tween(
                    durationMillis = 200,
                    delayMillis = 200
                )

                else -> tween(durationMillis = 500)
            }
        }) {
        // Fork (round 15): always on the pill surface, so always its text color.
        MaterialTheme.colorScheme.onSurface
    }

    val opacity by transition.animateFloat(label = "opacity") {
        // Fork (round 14): no fade for the hidden style. The pill slides with the page (offset), so going
        // back mirrors coming in; the fade used to hide it halfway through the way back.
        1f
    }

    // Fork (round 13): the same pill as the dock and the input bars: height, round ends, surface, shadow.
    // Round 15 (device test: "the search bar still doesn't look like a pill"): the shared pill surface at
    // every level, outline included; Kvaesitso's levels used to fade it to the bare wallpaper.
    val barH = de.mm20.launcher2.ui.launcher.widgets.school.barHeight()
    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(barH / 2)
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .alpha(opacity)
            .pillSurface(pillShape),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor
        ) {
            Column(
                verticalArrangement = if (reverse) Arrangement.BottomReversed else Arrangement.Top
            ) {
                Row(
                    modifier = Modifier.height(barH).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        modifier = Modifier.padding(12.dp),
                        painter = painterResource(R.drawable.search_24px),
                        contentDescription = null,
                        tint = contentColor
                    )
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_bar_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = contentColor
                            )
                        }
                        BasicTextField(
                            modifier = Modifier
                                .onFocusChanged {
                                    if (it.hasFocus) onFocus()
                                }
                                .focusRequester(focusRequester)
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = context.getString(R.string.search_bar_placeholder)
                                },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = contentColor
                            ),
                            singleLine = true,
                            value = value,
                            onValueChange = onValueChange,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Go,
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = onKeyboardActionGo,
                            ),
                            readOnly = readOnly,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        menu()
                    }
                }
                this.actions()
            }
        }
    }
}

enum class SearchBarLevel: Comparable<SearchBarLevel> {
    /**
     * The default, "hidden" state, when the launcher is in its initial state (scroll position is 0
     * and search is closed)
     */
    Resting,

    /**
     * When the search is open but there is no content behind the search bar (scroll position is 0)
     */
    Active,

    /**
     * When there is content below the search bar which requires the search bar to be raised above
     * this content (scroll position is not 0)
     */
    Raised
}