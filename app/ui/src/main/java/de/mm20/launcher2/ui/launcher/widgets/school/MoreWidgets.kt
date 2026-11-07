package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefLogic
import de.mm20.launcher2.schoolbrief.Feed
import de.mm20.launcher2.schoolbrief.Lesson
import de.mm20.launcher2.schoolbrief.SchoolTask
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.MyDayWidget
import de.mm20.launcher2.widgets.TestPrepWidget
import de.mm20.launcher2.widgets.WeekWidget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------- Week block

@Composable
fun WeekWidget(widget: WeekWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed ?: return
    Column {
        for (d in schoolDays(f, now.toLocalDate(), 3)) DayCard(f, d, now.toLocalDate())
    }
}

// ---------------------------------------------------------------- My day (adaptive)

internal fun lessonsOn(f: Feed, d: LocalDate): List<Lesson> {
    val key = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> "mon"; DayOfWeek.TUESDAY -> "tue"; DayOfWeek.WEDNESDAY -> "wed"
        DayOfWeek.THURSDAY -> "thu"; DayOfWeek.FRIDAY -> "fri"; else -> return emptyList()
    }
    if (f.daysOff.any { !d.isBefore(LocalDate.parse(it.start)) && !d.isAfter(LocalDate.parse(it.end)) }) return emptyList()
    return f.timetable[key].orEmpty().sortedBy { it.start }
}

private fun needsSportClothes(lessons: List<Lesson>) = lessons.any { l -> listOf("sport", "šport", "telesn").any { l.subject.contains(it, true) } }
private fun needsArtSupplies(lessons: List<Lesson>) = lessons.any { l -> listOf("kunst", "výtvar", "art").any { l.subject.contains(it, true) } }

/**
 * Mornings (before 10:00, school day): first lesson, tests today, what to bring.
 * Evenings (from 18:00): tomorrow in one card with a bag checklist (ticks saved per day).
 * Otherwise it stays out of the way.
 */
@Composable
fun MyDayWidget(widget: MyDayWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed ?: return
    MyDayContent(f, now)
}

@Composable
internal fun MyDayContent(f: Feed, now: LocalDateTime) {
    val today = now.toLocalDate()
    when {
        now.hour < 10 && lessonsOn(f, today).isNotEmpty() -> {
            val lessons = lessonsOn(f, today)
            val tests = f.tasks.filter { it.kind == SchoolTask.Test && !it.done && it.due == today.toString() }
            SchoolCard {
                CardHeader(stringResource(R.string.myday_morning), null)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.myday_first, lessons.first().subject, lessons.first().start), style = MaterialTheme.typography.bodyLarge)
                tests.forEach { Text(stringResource(R.string.myday_tests_today, "„${it.title}“"), style = MaterialTheme.typography.bodyLarge) }
                bringList(lessons)?.let { Text(stringResource(R.string.myday_bring, it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
            }
        }
        now.hour >= 18 -> {
            val tomorrow = today.plusDays(1)
            val lessons = lessonsOn(f, tomorrow)
            SchoolCard {
                CardHeader(stringResource(R.string.myday_evening), tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d.\u00A0M.", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])))
                Spacer(Modifier.height(8.dp))
                if (lessons.isEmpty()) {
                    Text(stringResource(R.string.myday_no_school), style = MaterialTheme.typography.bodyLarge)
                    return@SchoolCard
                }
                Text(stringResource(R.string.myday_first, lessons.first().subject, lessons.first().start), style = MaterialTheme.typography.bodyLarge)
                val due = BriefLogic.visibleTasks(f.tasks, today).filter { it.due == tomorrow }
                due.forEach { r -> Secondary("„${r.task.title}“") }
                bringList(lessons)?.let { Text(stringResource(R.string.myday_bring, it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.myday_bag), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BagChecklist(tomorrow, lessons.map { it.subject }.distinct())
            }
        }
    }
}

@Composable
private fun bringList(lessons: List<Lesson>): String? = listOfNotNull(
    stringResource(R.string.myday_sport).takeIf { needsSportClothes(lessons) },
    stringResource(R.string.myday_art).takeIf { needsArtSupplies(lessons) },
).takeIf { it.isNotEmpty() }?.joinToString(", ")

@Composable
private fun BagChecklist(day: LocalDate, subjects: List<String>) {
    val prefs = LocalContext.current.getSharedPreferences("school_bag", Context.MODE_PRIVATE)
    var packed by remember(day) { mutableStateOf(prefs.getStringSet(day.toString(), emptySet()).orEmpty()) }
    for (s in subjects) {
        val on = s in packed
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                packed = if (on) packed - s else packed + s
                prefs.edit().clear().putStringSet(day.toString(), packed).apply() // keep only the current day
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = on, onCheckedChange = null)
            Spacer(Modifier.width(10.dp))
            SubjectBadge(s, 24.dp, fontSize = 11)
            Spacer(Modifier.width(8.dp))
            Text(s, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ---------------------------------------------------------------- Test prep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPrepWidget(widget: TestPrepWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val chat: SchoolChatVM = viewModel(key = "school-chat")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed ?: return
    val today = now.toLocalDate()
    val test = f.tasks.filter { it.kind == SchoolTask.Test && !it.done }
        .mapNotNull { t -> runCatching { LocalDate.parse(t.due) }.getOrNull()?.let { t to it } }
        .filter { (_, d) -> !d.isBefore(today) && ChronoUnit.DAYS.between(today, d) <= 14 }
        .minByOrNull { it.second }
    var practising by remember { mutableStateOf(false) }
    RegisterSubjects(f)
    SchoolCard {
        CardHeader(stringResource(R.string.widget_name_testprep), null)
        Spacer(Modifier.height(8.dp))
        if (test == null) {
            Secondary(stringResource(R.string.testprep_none))
            return@SchoolCard
        }
        val (t, d) = test
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(t, t.kind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Secondary(listOf(t.subject, dueLabel(d, today, null)).filter { it.isNotBlank() }.joinToString(" · "))
            }
        }
        Spacer(Modifier.height(10.dp))
        val due = dueLabel(d, today, null)
        val label = stringResource(R.string.quick_practise, t.subject.ifBlank { t.title })
        Button(onClick = { chat.practiseTest(t, due, label); practising = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.testprep_practice))
        }
    }
    if (practising) {
        ModalBottomSheet(onDismissRequest = { practising = false }) {
            ChatDock(Modifier.padding(bottom = 16.dp), bottomInset = 0.dp)
        }
    }
}
