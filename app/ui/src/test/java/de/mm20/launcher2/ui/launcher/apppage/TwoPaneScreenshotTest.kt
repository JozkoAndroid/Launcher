package de.mm20.launcher2.ui.launcher.apppage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import de.mm20.launcher2.schoolbrief.SampleFeed
import de.mm20.launcher2.ui.launcher.widgets.school.BriefContent
import de.mm20.launcher2.ui.launcher.widgets.school.LessonContent
import de.mm20.launcher2.ui.launcher.widgets.school.TasksContent
import de.mm20.launcher2.ui.theme.transparency.LocalTransparencyScheme
import de.mm20.launcher2.ui.theme.transparency.TransparencyScheme
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.Locale

/** The real TwoPane.Layout at Galaxy Z Fold 5 inner-screen and tablet sizes, with invented data. */
abstract class TwoPaneScreenshotBase {

    abstract val rule: Paparazzi

    @Test
    fun twoPane() = rule.snapshot { Screen() }

    protected fun paparazzi(widthPx: Int, heightPx: Int, dpi: Int) = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = widthPx, screenHeight = heightPx, xdpi = dpi, ydpi = dpi,
            density = Density.create(dpi), locale = "sk",
            orientation = if (widthPx > heightPx) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
        ),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val now = LocalDateTime.parse("2026-11-10T09:10")
    private val nowOffset = OffsetDateTime.parse("2026-11-10T09:10:00+01:00")
    private val feed = SampleFeed.create(LocalDate.parse("2026-11-10"), nowOffset)
    private val apps = listOf(
        "Tatra banka", "Orange SK", "Schoolhelp", "Gemini", "Claude", "Čas", "Fotoaparát", "Galéria", "Hodiny", "Chrome",
        "Kalkulačka", "Kontakty", "Mapy", "Nastavenia", "Samsung Health", "Samsung Music", "Spotify", "Správy", "Telefón", "YouTube",
    ).map { AppEntry(it.lowercase(), it) }
    private val pinned = listOf("tatra banka", "orange sk", "schoolhelp", "gemini", "claude")

    @Composable
    private fun Screen() {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90), surface = Color(0xFF16181C), surfaceContainerHigh = Color(0xFF2A2D33))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                LocalInspectionMode provides true,
            ) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1D2B3A), Color(0xFF3B2F45), Color(0xFF14161A))))) {
                    TwoPane.Layout(
                        modifier = Modifier.fillMaxSize(),
                        home = { m ->
                            Column(m.padding(horizontal = 8.dp, vertical = 32.dp)) {
                                LessonContent(feed, now)
                                Box(Modifier.padding(top = 8.dp)) { BriefContent(feed, nowOffset) }
                                Box(Modifier.padding(top = 8.dp)) { TasksContent(feed, now.toLocalDate(), nowOffset) }
                            }
                        },
                        side = {
                            AppPageContent(
                                model = AppPageLogic.build(apps, pinned, "", Locale.forLanguageTag("sk")),
                                query = "",
                                pinnedKeys = pinned.toSet(),
                                actions = AppPageActions({}, {}, { _, _ -> }, { _, _ -> }, {}),
                                icon = { e, size ->
                                    val hue = (e.key.hashCode() and 0xFFFFFF).toLong() or 0xFF000000
                                    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color(hue).copy(alpha = 0.85f)))
                                },
                                contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Galaxy Z Fold 5 inner screen: 1812 x 2176 px, ~ 374 dpi (~ 775 x 931 dp). */
class FoldInnerScreenshotTest : TwoPaneScreenshotBase() {
    @get:org.junit.Rule
    override val rule = paparazzi(1812, 2176, 374)
}

/** Typical 11" Samsung tablet in landscape: 2560 x 1600 px, ~ 274 dpi (~ 1495 x 934 dp). */
class TabletLandscapeScreenshotTest : TwoPaneScreenshotBase() {
    @get:org.junit.Rule
    override val rule = paparazzi(2560, 1600, 274)
}

/** Fold 5 cover screen: 904 x 2316 px, ~ 402 dpi (~ 360 dp): stays a single column. */
class FoldCoverScreenshotTest : TwoPaneScreenshotBase() {
    @get:org.junit.Rule
    override val rule = paparazzi(904, 2316, 402)
}
