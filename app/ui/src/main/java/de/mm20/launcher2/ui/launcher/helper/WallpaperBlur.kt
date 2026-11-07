package de.mm20.launcher2.ui.launcher.helper

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import de.mm20.launcher2.ktx.isAtLeastApiLevel

@Composable
fun WallpaperBlur(blurRadius: () -> Int) {
    if (!isAtLeastApiLevel(31)) return
    val context = LocalContext.current
    val density = LocalDensity.current

    val radius = blurRadius()
    val window = (context as Activity).window
    val on = radius > 0
    // Fork: the window flag changes only when blur turns on or off; the radius is set cheaply each change
    // (assigning window.attributes on every frame of a swipe caused jank).
    LaunchedEffect(on) {
        val a = window.attributes
        a.flags = if (on) a.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        else a.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        window.attributes = a
    }
    SideEffect { window.setBackgroundBlurRadius(radius) }
}
