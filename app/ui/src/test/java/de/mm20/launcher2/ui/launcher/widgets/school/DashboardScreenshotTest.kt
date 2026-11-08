package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import de.mm20.launcher2.schoolbrief.Recording
import de.mm20.launcher2.schoolbrief.SampleFeed
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.theme.transparency.LocalTransparencyScheme
import de.mm20.launcher2.ui.theme.transparency.TransparencyScheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Unfolded Galaxy Z Fold 5 (1812 x 2176 px, ~374 dpi). Mirrors WidgetColumn's wide arrangement:
 * Lesson, Tasks | Brief, Context; the floating dock with 7 apps and the assistant. (The real column needs the running app.)
 */
class DashboardScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1812, screenHeight = 2176, xdpi = 374, ydpi = 374, density = Density.create(374), locale = "sk",
        ),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val now = LocalDateTime.parse("2026-11-10T09:10")
    private val nowOffset = OffsetDateTime.parse("2026-11-10T09:10:00+01:00")
    private val feed = SampleFeed.create(LocalDate.parse("2026-11-10"), nowOffset)

    @Test
    fun foldUnfoldedDashboard() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                LocalInspectionMode provides true,
            ) {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1D2B3A), Color(0xFF3B2F45), Color(0xFF14161A))))) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 32.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                LessonContent(feed, now, Recording("Physik", false, 1834))
                                Box(Modifier.padding(top = 8.dp)) { TasksContent(feed, now.toLocalDate(), nowOffset) }
                            }
                            Column(Modifier.weight(1f)) {
                                BriefContent(feed, nowOffset)
                                Box(Modifier.padding(top = 8.dp)) {
                                    ContextContent(
                                        listOf(
                                            ContextItem("lunch", "Obed: Paradajková polievka, rizoto", R.drawable.restaurant_24px, onClick = {}),
                                            ContextItem("nameday", "Dnes má meniny René", R.drawable.celebration_24px),
                                            ContextItem("folder:Škola", "Škola", R.drawable.folder_24px, onClick = {}),
                                            ContextItem("folder:Banky", "Banky", R.drawable.folder_24px, onClick = {}),
                                        )
                                    )
                                }
                            }
                        }
                    }
                    // Tiles: one even row across the full width (as WideDashboard draws them).
                    val tiles = listOf(
                        ContextItem("add", "", R.drawable.add_24px, value = "Pridať", label = "úlohu, test, termín") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle"),
                        ContextItem("test", "", R.drawable.history_edu_24px, value = "3 dni", label = "do testu: Ethik") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle", accent = true),
                        ContextItem("nameday", "", R.drawable.celebration_24px, value = "Bohumír", label = "Meniny") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle", font = "serif"),
                        ContextItem("data", "", R.drawable.data_usage_24px, value = "1,3 GB", label = "z 3,5 GB", progress = 0.37f) to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring"),
                        ContextItem("steps", "", R.drawable.directions_walk_24px, value = "7", label = "Kroky · cieľ 8 000", progress = 0.01f) to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring"),
                        ContextItem("instagram", "", R.drawable.timer_24px, value = "0 min", label = "Instagram dnes") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle", font = "condensed"),
                    )
                    val fake = tiles.map { de.mm20.launcher2.widgets.MyDayWidget(java.util.UUID.randomUUID()) }
                    Box(Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 112.dp)) {
                        ChipTiles(fake) { w -> val (item, cfg) = tiles[fake.indexOf(w)]; ChipBlock(listOf(item), cfg) }
                    }
                    // The floating dock: 7 apps on a wide screen, then the assistant.
                    val colors = listOf(Color(0xFF3B7DDD), Color(0xFF34A853), Color(0xFFE8710A), Color(0xFF9334E6), Color(0xFFD93025), Color(0xFF12B5CB), Color(0xFFF9AB00))
                    DockContent(
                        count = 7, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                        icon = { i, _ -> Box(Modifier.padding(2.dp).fillMaxSize().background(colors[i], androidx.compose.foundation.shape.CircleShape)) },
                        label = { "App $it" }, onApp = { _, _ -> }, onEdit = {}, onAssistant = {},
                    )
                }
            }
        }
    }
}
