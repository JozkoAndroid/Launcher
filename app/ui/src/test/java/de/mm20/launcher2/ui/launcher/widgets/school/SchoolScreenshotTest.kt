package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import de.mm20.launcher2.schoolbrief.SampleFeed
import de.mm20.launcher2.ui.locals.LocalPreferDarkContentOverWallpaper
import de.mm20.launcher2.ui.theme.transparency.LocalTransparencyScheme
import de.mm20.launcher2.ui.theme.transparency.TransparencyScheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Renders the school blocks the way the home screen stacks them, on simulated wallpapers.
 * Not pixel-exact (default font, no real wallpaper), but shows spacing, hierarchy and contrast.
 */
class SchoolScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        // Galaxy Z Fold 5 cover screen is about 360dp wide; 375dp is the design rules' mobile check.
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1031, screenHeight = 2640, xdpi = 440, ydpi = 440, density = com.android.resources.Density.create(440), locale = "sk"),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    // A Tuesday, 09:10: in the second half of the first lesson.
    private val now = LocalDateTime.parse("2026-11-10T09:10")
    private val nowOffset = OffsetDateTime.parse("2026-11-10T09:10:00+01:00")
    private val feed = SampleFeed.create(LocalDate.parse("2026-11-10"), nowOffset)

    /** Bright, busy wallpaper: the worst case for white text. */
    private val brightWallpaper = Brush.linearGradient(listOf(Color(0xFFF2D9A6), Color(0xFF9EC9E8), Color(0xFFF4F1EA)))
    private val darkWallpaper = Brush.linearGradient(listOf(Color(0xFF1D2B3A), Color(0xFF3B2F45), Color(0xFF14161A)))

    /** Android reports that a bright wallpaper supports dark text, so the blocks switch to dark text. */
    @Test
    fun home_brightWallpaper() = paparazzi.snapshot {
        Home(brightWallpaper, dim = false, darkContent = true)
    }

    @Test
    fun home_darkWallpaper() = paparazzi.snapshot {
        Home(darkWallpaper, dim = false, darkContent = false)
    }

    @Test
    fun home_staleFeed() = paparazzi.snapshot {
        Home(darkWallpaper, dim = false, darkContent = false, now = nowOffset.plusHours(13))
    }

    @Composable
    private fun Home(wallpaper: Brush, dim: Boolean, darkContent: Boolean, now: OffsetDateTime = nowOffset) {
        // Wallpaper-derived scheme stand-in: a muted blue hue at the tones Kvaesitso's palette produces.
        val scheme = if (darkContent) {
            lightColorScheme(primary = Color(0xFF3F5F90), inversePrimary = Color(0xFFA9C7FF))
        } else {
            darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))
        }
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(
                LocalPreferDarkContentOverWallpaper provides darkContent,
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(wallpaper)
                        // Kvaesitso's wallpaper dim in dark mode.
                        .background(if (dim) Color.Black.copy(alpha = 0.4f) else Color.Transparent)
                ) {
                    // Same insets as the real widget column: 8dp sides, 8dp between widgets.
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        LessonContent(feed, this@SchoolScreenshotTest.now)
                        Box(Modifier.padding(top = 8.dp)) { BriefContent(feed, now) }
                        Box(Modifier.padding(top = 8.dp)) { TasksContent(feed, this@SchoolScreenshotTest.now.toLocalDate(), now) }
                        Box(Modifier.padding(top = 8.dp)) {
                            ContextContent(
                                listOf(
                                    ContextItem("instagram", "Instagram dnes: 47 min"),
                                    ContextItem("steps", "Kroky: 3 120 z 8 000"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
