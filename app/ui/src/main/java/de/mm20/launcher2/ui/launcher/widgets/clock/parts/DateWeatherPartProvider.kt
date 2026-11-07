package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.MeasurementSystem
import de.mm20.launcher2.ui.base.LocalTime
import de.mm20.launcher2.ui.locals.LocalMeasurementSystem
import de.mm20.launcher2.ui.utils.formatTemperature
import de.mm20.launcher2.weather.DailyForecast
import de.mm20.launcher2.weather.Forecast
import de.mm20.launcher2.weather.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the line under the clock shows for weather. Temperatures in kelvin, as Kvaesitso stores them. */
data class WeatherLine(val nowK: Float, val highK: Float?, val lowK: Float?, val providerUrl: String, val icon: Int = Forecast.UNKNOWN)

object WeatherLineLogic {
    /** The forecast covering [nowMs] (latest one not in the future), plus today's high and low. */
    fun pick(forecasts: List<Forecast>, dailies: List<DailyForecast>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): WeatherLine? {
        val current = forecasts.filter { it.timestamp <= nowMs }.maxByOrNull { it.timestamp }
            ?: forecasts.minByOrNull { it.timestamp }
            ?: return null
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = dailies.firstOrNull { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today }
        return WeatherLine(current.temperature.toFloat(), day?.maxTemp?.toFloat(), day?.minTemp?.toFloat(), current.providerUrl, current.icon)
    }
}

/**
 * Fork: date and weather on one line under the clock ("sobota 7. novembra · 8° · 12°/4°").
 * Replaces the plain date part; music, alarm and battery parts still take over when active.
 */
class DateWeatherPartProvider : PartProvider, KoinComponent {
    private val weather: WeatherRepository by inject()

    override fun getRanking(context: Context): Flow<Int> = flowOf(1)

    private val line: Flow<WeatherLine?> = combine(weather.getForecasts(), weather.getDailyForecasts()) { f, d ->
        WeatherLineLogic.pick(f, d, System.currentTimeMillis())
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current
        val time = LocalTime.current
        val w by remember { line }.collectAsState(null)
        DateWeatherLine(
            dateMillis = time,
            weather = w,
            measurementSystem = LocalMeasurementSystem.current,
            onDate = { openCalendar(context) },
            onWeather = { url -> if (url.isNotBlank()) context.tryStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        )
    }

    private fun openCalendar(context: Context) {
        val b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(b, System.currentTimeMillis())
        context.tryStartActivity(Intent(Intent.ACTION_VIEW).setData(b.build()).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun DateWeatherLine(
    dateMillis: Long,
    weather: WeatherLine?,
    measurementSystem: MeasurementSystem,
    onDate: () -> Unit,
    onWeather: (String) -> Unit,
) {
    val context = LocalContext.current
    val content = LocalContentColor.current
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    // "sobota 7. novembra" in Slovak, "Saturday, November 7" in English: the locale's own long form, weekday first.
    val dateText = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        .replace(Regex(",?\\s*\\d{4}\\s*$"), "")
        .replace(Regex("\\s+\\d{4}\\.?$"), "")
    val style = MaterialTheme.typography.bodyLarge.merge(TextStyle(fontFeatureSettings = "tnum"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            dateText,
            style = style.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDate)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        )
        if (weather != null) {
            val now = formatTemperature(context, weather.nowK, measurementSystem)
            val range = if (weather.highK != null && weather.lowK != null) {
                " · " + formatTemperature(context, weather.highK, measurementSystem) + "/" + formatTemperature(context, weather.lowK, measurementSystem)
            } else ""
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onWeather(weather.providerUrl) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(weatherGlyph(weather.icon)), contentDescription = null,
                    tint = content.copy(alpha = 0.8f), modifier = Modifier.padding(end = 6.dp).size(20.dp),
                )
                Text(text = "$now$range", style = style, color = content.copy(alpha = 0.8f))
            }
        }
    }
}

/** Three calm glyphs are enough for a one-line forecast: sun, cloud, rain/snow. */
private fun weatherGlyph(icon: Int): Int = when (icon) {
    Forecast.CLEAR, Forecast.EXTREME_HEAT -> de.mm20.launcher2.ui.R.drawable.light_mode_24px
    Forecast.RAIN, Forecast.LIGHT_RAIN, Forecast.HEAVY_RAIN, Forecast.THUNDERSTORM, Forecast.SNOW, Forecast.SLEET, Forecast.HAIL ->
        de.mm20.launcher2.ui.R.drawable.rainy_20px
    else -> de.mm20.launcher2.ui.R.drawable.cloud_20px
}
