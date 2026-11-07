package de.mm20.launcher2.ui.launcher.apppage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.theme.transparency.transparency

/**
 * Fork, step 6: from [MinWidth] on (Fold 5 inner screen ~ 775dp, tablets) home and app page
 * sit side by side. Below it (Fold 5 cover ~ 360dp, phones) the app page is one swipe away.
 */
object TwoPane {
    const val MinWidthDp = 600
    val MinWidth = MinWidthDp.dp

    /** Home column cap, so brief lines don't run too long. */
    val HomeMaxWidth = 560.dp

    @Composable
    fun Layout(
        modifier: Modifier,
        home: @Composable (Modifier) -> Unit,
        side: @Composable () -> Unit,
    ) {
        BoxWithConstraints(modifier) {
            if (maxWidth >= MinWidth) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        home(Modifier.widthIn(max = HomeMaxWidth).fillMaxHeight())
                    }
                    // The app list needs a calm surface to stay readable over any wallpaper; no border, no shadow.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = MaterialTheme.transparency.background))
                    ) { side() }
                }
            } else {
                home(Modifier.fillMaxSize())
            }
        }
    }
}
