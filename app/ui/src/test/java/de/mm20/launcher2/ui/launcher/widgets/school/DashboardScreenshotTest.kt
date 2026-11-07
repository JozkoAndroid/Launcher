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
 * Lesson, Tasks | Brief, Context, Gemini full width. (The real column needs the running app.)
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
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { AssistantButton(onClick = {}) }
                    }
                }
            }
        }
    }
}
