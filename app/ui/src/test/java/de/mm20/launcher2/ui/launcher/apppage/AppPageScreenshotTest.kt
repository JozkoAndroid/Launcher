package de.mm20.launcher2.ui.launcher.apppage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class AppPageScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1031, screenHeight = 2640, xdpi = 440, ydpi = 440,
            density = com.android.resources.Density.create(440), locale = "sk",
        ),
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val apps = listOf(
        "Tatra banka", "Orange SK", "Schoolhelp", "Gemini", "Claude", "Čas", "Fotoaparát", "Galéria",
        "Hodiny", "Chrome", "Instagram", "Kalkulačka", "Kontakty", "Mapy", "Nastavenia", "Samsung Health",
        "Samsung Music", "Spotify", "Správy", "Telefón", "WhatsApp", "YouTube", "Zdravie", "2FA Authenticator",
    ).map { AppEntry(it.lowercase(), it, searchOnly = it == "Instagram") }

    private val pinned = listOf("tatra banka", "orange sk", "schoolhelp", "gemini", "claude")

    private val noop = AppPageActions({}, {}, { _, _ -> }, { _, _ -> }, {})

    @Test
    fun appPage() = paparazzi.snapshot {
        Page(AppPageLogic.build(apps, pinned, "", Locale.forLanguageTag("sk")), "")
    }

    @Test
    fun appPage_searchFindsHiddenApp() = paparazzi.snapshot {
        Page(AppPageLogic.build(apps, pinned, "insta", Locale.forLanguageTag("sk")), "insta")
    }

    @Test
    fun appPage_noResults() = paparazzi.snapshot {
        Page(AppPageLogic.build(apps, pinned, "xyz", Locale.forLanguageTag("sk")), "xyz")
    }

    @Composable
    private fun Page(model: AppPageModel, query: String) {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA9C7FF), surfaceContainerHigh = Color(0xFF2A2D33))) {
            // Kvaesitso draws secondary pages on the theme background at 85% over the wallpaper.
            Box(Modifier.fillMaxSize().background(Color(0xFF16181C))) {
                AppPageContent(
                    model = model,
                    query = query,
                    pinnedKeys = pinned.toSet(),
                    actions = noop,
                    icon = { e, size ->
                        // Stand-in for real app icons.
                        val hue = (e.key.hashCode() and 0xFFFFFF).toLong() or 0xFF000000
                        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(Color(hue).copy(alpha = 0.85f)))
                    },
                    contentPadding = PaddingValues(top = 32.dp, bottom = 24.dp),
                )
            }
        }
    }
}
