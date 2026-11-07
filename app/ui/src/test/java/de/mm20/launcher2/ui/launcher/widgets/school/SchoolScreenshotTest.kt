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

    /** First run: no data yet, one sentence and one action. */
    @Test
    fun brief_needsLogin() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                // Screenshots capture the first frame: show animated-in content at its end state.
                androidx.compose.ui.platform.LocalInspectionMode provides true,
            ) {
                Box(Modifier.fillMaxSize().background(darkWallpaper)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        BriefStatusContent(de.mm20.launcher2.schoolbrief.BriefStatus.NeedsLogin, hasFeed = false, onLogin = {}, onRetry = {})
                    }
                }
            }
        }
    }

    @Test
    fun contextCards() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                // Screenshots capture the first frame: show animated-in content at its end state.
                androidx.compose.ui.platform.LocalInspectionMode provides true,
            ) {
                Box(Modifier.fillMaxSize().background(darkWallpaper)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        ContextContent(
                            listOf(
                                ContextItem("music", "Bohemian Rhapsody · Queen", icon = de.mm20.launcher2.ui.R.drawable.music_note_24px, onClick = {}, playing = true, onTogglePlay = {}),
                                ContextItem("data", "Mobilné dáta: 2,9 z 3,5 GB", icon = de.mm20.launcher2.ui.R.drawable.data_usage_24px, onClick = {}),
                                ContextItem("lunch", "Obed: Paradajková polievka, kuracie rizoto", icon = de.mm20.launcher2.ui.R.drawable.restaurant_24px, onClick = {}),
                                ContextItem("nameday", "Dnes má meniny René", icon = de.mm20.launcher2.ui.R.drawable.celebration_24px),
                                ContextItem("folder:Škola", "Škola", icon = de.mm20.launcher2.ui.R.drawable.folder_24px, onClick = {}),
                                ContextItem("folder:Banky", "Banky", icon = de.mm20.launcher2.ui.R.drawable.folder_24px, onClick = {}),
                                ContextItem("instagram", "Instagram dnes: 47 min"),
                            )
                        )
                    }
                }
            }
        }
    }

    @Test
    fun weekPageDays() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))) {
            Box(Modifier.fillMaxSize().background(Color(0xFF16181C))) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    DayCard(feed, java.time.LocalDate.parse("2026-11-10"), java.time.LocalDate.parse("2026-11-10"))
                    DayCard(feed, java.time.LocalDate.parse("2026-11-11"), java.time.LocalDate.parse("2026-11-10"))
                }
            }
        }
    }

    @Test
    fun chipStyles() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90), primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD6E3FF))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                androidx.compose.ui.platform.LocalInspectionMode provides true,
            ) {
                val data = ContextItem("data", "Mobilné dáta: 2,9 z 3,5 GB", de.mm20.launcher2.ui.R.drawable.data_usage_24px, value = "2,9 GB", label = "z 3,5 GB", progress = 0.83f)
                val steps = ContextItem("steps", "Kroky: 3 120 z 8 000", de.mm20.launcher2.ui.R.drawable.directions_walk_24px, value = "3 120", label = "Kroky · cieľ 8 000", progress = 0.39f)
                val name = ContextItem("nameday", "Dnes má meniny René", de.mm20.launcher2.ui.R.drawable.celebration_24px, value = "René", label = "Meniny")
                val insta = ContextItem("instagram", "Instagram dnes: 47 min", de.mm20.launcher2.ui.R.drawable.timer_24px, value = "47 min", label = "Instagram dnes")
                val lunch = ContextItem("lunch", "Obed: menu 2", de.mm20.launcher2.ui.R.drawable.restaurant_24px, value = "Menu 2", label = "Obed")
                Box(Modifier.fillMaxSize().background(darkWallpaper)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        val cfg = de.mm20.launcher2.widgets.ChipWidgetConfig()
                        ChipBlock(listOf(name), cfg.copy(style = "glance", font = "serif", icon = "none"))
                        ChipBlock(listOf(insta), cfg.copy(style = "glance", font = "condensed", accent = true))
                        ChipBlock(listOf(data), cfg.copy(style = "tile", font = "rounded", icon = "circle"))
                        ChipBlock(listOf(lunch), cfg.copy(style = "tile", icon = "circle", accent = true))
                        ChipBlock(listOf(steps), cfg.copy(style = "ring", font = "wide"))
                        ChipBlock(listOf(data), cfg.copy(style = "ring", accent = true, font = "mono"))
                        ChipBlock(listOf(name), cfg.copy(style = "pill"))
                    }
                }
            }
        }
    }

    /** Recommended home tiles: test countdown, name day (serif), data and steps rings, Instagram. */
    @Test
    fun tileGrid() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90), primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD6E3FF))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                androidx.compose.ui.platform.LocalInspectionMode provides true,
            ) {
                val items = listOf(
                    ContextItem("test", "", de.mm20.launcher2.ui.R.drawable.quiz_24px, value = "3 dni", label = "do testu: Bio") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle", accent = true),
                    ContextItem("nameday", "", de.mm20.launcher2.ui.R.drawable.celebration_24px, value = "René", label = "Meniny") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle", font = "serif"),
                    ContextItem("data", "", de.mm20.launcher2.ui.R.drawable.data_usage_24px, value = "2,9 GB", label = "z 3,5 GB", progress = 0.83f) to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring"),
                    ContextItem("steps", "", de.mm20.launcher2.ui.R.drawable.directions_walk_24px, value = "3 120", label = "Kroky · cieľ 8 000", progress = 0.39f) to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "ring"),
                    ContextItem("instagram", "", de.mm20.launcher2.ui.R.drawable.timer_24px, value = "47 min", label = "Instagram dnes") to de.mm20.launcher2.widgets.ChipWidgetConfig(style = "tile", icon = "circle", font = "condensed"),
                )
                Box(Modifier.fillMaxSize().background(darkWallpaper)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        val fake = items.map { de.mm20.launcher2.widgets.MyDayWidget(java.util.UUID.randomUUID()) }
                        ChipTiles(fake) { w -> val (item, cfg) = items[fake.indexOf(w)]; ChipBlock(listOf(item), cfg) }
                    }
                }
            }
        }
    }

    @Test
    fun myDay() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                androidx.compose.ui.platform.LocalInspectionMode provides true,
            ) {
                Box(Modifier.fillMaxSize().background(darkWallpaper)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        MyDayContent(feed, LocalDateTime.parse("2026-11-10T07:30"))
                        Box(Modifier.padding(top = 8.dp)) { MyDayContent(feed, LocalDateTime.parse("2026-11-10T19:30")) }
                    }
                }
            }
        }
    }

    @Test
    fun assistantButton() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90))) {
            CompositionLocalProvider(
                LocalTransparencyScheme provides TransparencyScheme(background = 0.85f, surface = 0f, elevatedSurface = 1f),
                // Screenshots capture the first frame: show animated-in content at its end state.
                androidx.compose.ui.platform.LocalInspectionMode provides true,
            ) {
                Box(Modifier.fillMaxSize().background(darkWallpaper)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 24.dp)) {
                        TasksContent(feed, this@SchoolScreenshotTest.now.toLocalDate(), nowOffset)
                        Box(Modifier.padding(top = 8.dp)) { AssistantButton(onClick = {}) }
                    }
                }
            }
        }
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
                // Screenshots capture the first frame: show animated-in content at its end state.
                androidx.compose.ui.platform.LocalInspectionMode provides true,
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
                        // Clock line as Kvaesitso draws it (the clock itself is upstream code, not part of this test).
                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides schoolContentColor()) {
                            de.mm20.launcher2.ui.launcher.widgets.clock.parts.DateWeatherLine(
                                dateMillis = java.time.LocalDate.parse("2026-11-10").atTime(9, 10).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                weather = de.mm20.launcher2.ui.launcher.widgets.clock.parts.WeatherLine(281.15f, 285.15f, 277.15f, ""),
                                measurementSystem = de.mm20.launcher2.preferences.MeasurementSystem.Metric,
                                onDate = {}, onWeather = {},
                            )
                        }
                        LessonContent(feed, this@SchoolScreenshotTest.now, de.mm20.launcher2.schoolbrief.Recording("Physik", false, 1834))
                        // One sheet: brief and tasks joined (as WidgetColumn does for neighbors).
                        CompositionLocalProvider(LocalSegment provides Segment(joinedBottom = true)) {
                            Box(Modifier.padding(top = 16.dp)) { BriefContent(feed, now) }
                        }
                        CompositionLocalProvider(LocalSegment provides Segment(joinedTop = true)) {
                            Box(Modifier.padding(top = SchoolTokens.Seam)) { TasksContent(feed, this@SchoolScreenshotTest.now.toLocalDate(), now) }
                        }
                        Box(Modifier.padding(top = 16.dp)) {
                            ContextContent(
                                listOf(
                                    ContextItem("music", "Bohemian Rhapsody · Queen", icon = de.mm20.launcher2.ui.R.drawable.music_note_24px, onClick = {}, playing = true, onTogglePlay = {}),
                                    ContextItem("nameday", "Dnes má meniny René", icon = de.mm20.launcher2.ui.R.drawable.celebration_24px),
                                )
                            )
                        }
                    }
                    FakeDock(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 24.dp))
                }
            }
        }
    }

    @Composable
    private fun FakeDock(modifier: Modifier, count: Int = 4) {
        val colors = listOf(Color(0xFF3B7DDD), Color(0xFF34A853), Color(0xFFE8710A), Color(0xFF9334E6), Color(0xFFD93025), Color(0xFF12B5CB), Color(0xFFF9AB00))
        DockContent(
            count = count, modifier = modifier,
            icon = { i, size -> Box(Modifier.padding(2.dp).fillMaxSize().background(colors[i % colors.size], androidx.compose.foundation.shape.CircleShape)) },
            label = { "App $it" }, onApp = {}, onEdit = {}, onAssistant = {},
        )
    }

    @Test
    fun dayPage() = paparazzi.snapshot {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), inversePrimary = Color(0xFF3F5F90), surface = Color(0xFF15171B), surfaceContainerHigh = Color(0xFF262A31))) {
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalInspectionMode provides true,
                androidx.activity.compose.LocalActivityResultRegistryOwner provides object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                        override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
                    }
                },
            ) {
                Box(Modifier.fillMaxSize().background(Color(0xFF15171B))) {
                    DayContent(
                        feed = feed,
                        today = de.mm20.launcher2.schoolbrief.TodayData(
                            "2026-11-10",
                            listOf(
                                de.mm20.launcher2.schoolbrief.PlanStep(0, "Nach Hause kommen und kurz entspannen", 15, "", true),
                                de.mm20.launcher2.schoolbrief.PlanStep(1, "Für den Ethik-Leistungsnachweis lernen", 25, "Ethik", false),
                                de.mm20.launcher2.schoolbrief.PlanStep(2, "Zwei Werbeplakate als Dummy entwerfen", 35, "Wirtschaft", false),
                            ),
                            listOf(
                                de.mm20.launcher2.schoolbrief.Todo("a", "Venček info", false),
                                de.mm20.launcher2.schoolbrief.Todo("b", "Vrátiť knihu do knižnice", true),
                            ),
                        ),
                        now = this@SchoolScreenshotTest.now,
                        asking = false, onAsking = {}, onAdd = {}, onTodo = { _, _ -> }, onStep = { _, _ -> },
                        chat = {},
                    )
                }
            }
        }
    }
}
