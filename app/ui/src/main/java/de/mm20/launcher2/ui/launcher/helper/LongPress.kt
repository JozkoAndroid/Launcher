package de.mm20.launcher2.ui.launcher.helper

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Long press that only observes: it never consumes events, so the scaffold's own taps, double tap
 * (screen lock) and swipes keep working (detectTapGestures consumed the up event).
 */
suspend fun PointerInputScope.detectLongPressPassive(onLongPress: () -> Unit) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val slop = viewConfiguration.touchSlop
    var moved = 0f
    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
            if (!c.pressed) return@withTimeoutOrNull true
            moved += c.positionChange().getDistance()
            if (moved > slop || c.isConsumed) return@withTimeoutOrNull true
        }
        @Suppress("UNREACHABLE_CODE") true
    }
    if (released == null) {
        onLongPress()
        // Swallow the rest of this touch so the card under the finger does not also get a click on release.
        do {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.forEach { it.consume() }
        } while (e.changes.any { it.pressed })
    }
}
