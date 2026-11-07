package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefLogic
import de.mm20.launcher2.schoolbrief.BriefStatus
import de.mm20.launcher2.schoolbrief.Feed
import de.mm20.launcher2.schoolbrief.Lesson
import de.mm20.launcher2.schoolbrief.LessonState
import de.mm20.launcher2.schoolbrief.NextSchoolDay
import de.mm20.launcher2.schoolbrief.SchoolTask
import de.mm20.launcher2.schoolbrief.TaskRow
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.widgets.school.context.ContextCard
import de.mm20.launcher2.ui.launcher.widgets.school.context.kind
import de.mm20.launcher2.ui.launcher.widgets.school.context.ContextRules
import de.mm20.launcher2.ui.launcher.widgets.school.context.ContextVM
import de.mm20.launcher2.widgets.BriefWidget
import de.mm20.launcher2.widgets.ContextWidget
import de.mm20.launcher2.widgets.LessonWidget
import de.mm20.launcher2.widgets.TasksWidget
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------------------------------------------------------- Lesson

@Composable
fun LessonWidget(widget: LessonWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val recording by vm.recording.collectAsState()
    val context = LocalContext.current
    val f = feed ?: return
    LessonContent(
        f, now, recording,
        onPause = { vm.pauseRecording(context, it) },
        onStop = { vm.stopRecording(context) },
    )
}

/** "Teraz": the current or next lesson as one glanceable card. */
@Composable
internal fun LessonContent(
    f: Feed,
    now: LocalDateTime,
    recording: de.mm20.launcher2.schoolbrief.Recording? = null,
    onPause: (Boolean) -> Unit = {},
    onStop: () -> Unit = {},
) {
    val context = LocalContext.current
    RegisterSubjects(f)
    SchoolCard(onClick = { Links.openSchoolhelp(context) }) {
        val state = BriefLogic.lessonState(f, now)
        when (val s = state) {
            is LessonState.InLesson -> {
                LessonHeadline(s.current, stringResource(R.string.lesson_time_range, s.current.start, s.current.end))
                Spacer(Modifier.height(12.dp))
                ProgressLine(s.progress)
                s.next?.let {
                    Spacer(Modifier.height(12.dp))
                    NextLessonRow(stringResource(R.string.lesson_next_label), it)
                }
            }
            is LessonState.Upcoming -> LessonHeadline(s.next, stringResource(R.string.lesson_starts_at, s.next.start))
            is LessonState.DayOver -> FreeHeadline(R.drawable.school_24px, stringResource(R.string.lesson_day_over), s.next, now.toLocalDate())
            is LessonState.DayOff -> FreeHeadline(R.drawable.event_24px, s.title, s.next, now.toLocalDate())
            is LessonState.Weekend -> FreeHeadline(R.drawable.light_mode_24px, stringResource(R.string.lesson_weekend), s.next, now.toLocalDate())
        }
        if (recording != null) {
            Spacer(Modifier.height(10.dp))
            RecordingRow(recording, onPause, onStop)
        } else if (state is LessonState.InLesson) {
            TextButton(onClick = { Links.openSchoolhelp(context) }, modifier = Modifier.heightIn(min = SchoolTokens.RowMin)) {
                Text(stringResource(R.string.recording_start))
            }
        }
    }
}

/** "● Nahráva sa · 2:34" with Pozastaviť/Pokračovať and Ukončiť (confirmed in Schoolhelp). */
@Composable
private fun RecordingRow(r: de.mm20.launcher2.schoolbrief.Recording, onPause: (Boolean) -> Unit, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A dot that marks a real state (recording), no glow, no pulse.
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (r.paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(8.dp))
        val time = "%d:%02d".format(r.seconds / 60, r.seconds % 60)
        Text(
            // The subject is the card's title already; the line stays short enough for the time.
            stringResource(if (r.paused) R.string.recording_paused else R.string.recording_on) + " · " + time,
            style = MaterialTheme.typography.bodyMedium.merge(SchoolTokens.Tabular),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onPause(!r.paused) }) {
            Text(stringResource(if (r.paused) R.string.recording_resume else R.string.recording_pause))
        }
        TextButton(onClick = onStop) { Text(stringResource(R.string.recording_stop)) }
    }
}

@Composable
private fun LessonHeadline(lesson: Lesson, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SubjectBadge(lesson.subject, SchoolTokens.BadgeLarge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                lesson.subject,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = SchoolTokens.TightTracking),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Secondary(detail)
        }
    }
}

@Composable
private fun FreeHeadline(@DrawableRes icon: Int, title: String, next: NextSchoolDay?, today: LocalDate) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, SchoolTokens.BadgeLarge)
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = SchoolTokens.TightTracking),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    next?.let {
        Spacer(Modifier.height(12.dp))
        NextLessonRow(dueLabel(it.date, today, null).replaceFirstChar { c -> c.titlecase() }, it.first)
    }
}

/** "Ďalej  [Wi] Wirtschaft · 09:55" */
@Composable
private fun NextLessonRow(label: String, lesson: Lesson) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Secondary(label)
        Spacer(Modifier.width(10.dp))
        SubjectBadge(lesson.subject, 28.dp, fontSize = 12)
        Spacer(Modifier.width(8.dp))
        Text(lesson.subject, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(6.dp))
        Secondary("· " + lesson.start)
    }
}

// ---------------------------------------------------------------- Brief

@Composable
fun BriefWidget(widget: BriefWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val status by vm.status.collectAsState()
    vm.now.collectAsState(LocalDateTime.now()).value // recompose each minute for staleness
    val context = LocalContext.current
    Column {
        feed?.let { BriefContent(it, OffsetDateTime.now()) }
        BriefStatusContent(status, hasFeed = feed != null, onLogin = { vm.login(context) }, onRetry = { vm.retry() })
    }
}

/** The AI brief and three headlines with topic icons, one line each. */
@Composable
internal fun BriefContent(f: Feed, now: OffsetDateTime) {
    val context = LocalContext.current
    val stale = BriefLogic.isStale(f, now)
    SchoolCard(modifier = Modifier.alpha(if (stale) SchoolTokens.StaleAlpha else 1f)) {
        CardHeader(
            stringResource(R.string.brief_title),
            if (stale) stringResource(R.string.school_stale) + " · " + updatedTime(f) else updatedTime(f),
        )
        Spacer(Modifier.height(8.dp))
        Text(f.brief, style = MaterialTheme.typography.bodyLarge)
        if (f.news.isNotEmpty()) Spacer(Modifier.height(8.dp))
        val openLabel = stringResource(R.string.open_article)
        for (item in f.news) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = SchoolTokens.RowMin)
                    .clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                    .clickable(role = Role.Button) { Links.openArticle(context, item.url) }
                    .semantics { contentDescription = "$openLabel: ${item.title}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(topicIcon(item.topic)), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@DrawableRes
private fun topicIcon(topic: String): Int = when (topic) {
    "aviation" -> R.drawable.flight_24px
    "tech" -> R.drawable.smartphone_24px
    "eu" -> R.drawable.account_balance_24px
    else -> R.drawable.newspaper_24px
}

/** Login, loading and error states: one sentence and one action. */
@Composable
internal fun BriefStatusContent(status: BriefStatus, hasFeed: Boolean, onLogin: () -> Unit, onRetry: () -> Unit) {
    val line: String
    var action: Pair<String, () -> Unit>? = null
    var filled = false
    when {
        status is BriefStatus.NeedsLogin -> {
            line = stringResource(if (hasFeed) R.string.school_login_expired else R.string.school_login_needed)
            action = stringResource(if (hasFeed) R.string.school_login_again else R.string.school_login) to onLogin
            filled = true
        }
        status is BriefStatus.LoggingIn -> line = stringResource(R.string.school_login_in_browser)
        hasFeed -> return
        status is BriefStatus.Error -> {
            line = stringResource(R.string.school_load_failed, status.message)
            action = stringResource(R.string.school_retry) to onRetry
        }
        else -> line = stringResource(R.string.school_loading)
    }
    SchoolCard {
        Text(line, style = MaterialTheme.typography.bodyLarge)
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(SchoolTokens.Gap))
            if (filled) {
                Button(onClick = onClick, modifier = Modifier.heightIn(min = SchoolTokens.RowMin)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                }
            } else {
                TextButton(onClick = onClick, modifier = Modifier.heightIn(min = SchoolTokens.RowMin)) { Text(label) }
            }
        }
    }
}

// ---------------------------------------------------------------- Tasks

@Composable
fun TasksWidget(widget: TasksWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed ?: return
    TasksContent(f, now.toLocalDate(), OffsetDateTime.now(), onDone = { vm.markDone(it) }, onUndo = { vm.markDone(it, false) })
}

/** Up to 5 tasks grouped by day ("Po termíne", "Dnes", "Zajtra", …); the rest one tap away in Schoolhelp. */
@Composable
internal fun TasksContent(f: Feed, today: LocalDate, now: OffsetDateTime, onDone: (String) -> Unit = {}, onUndo: (String) -> Unit = {}) {
    val context = LocalContext.current
    // Undo for a few seconds after "Hotovo" (device test: an unticked task never came back).
    var lastDone by remember { mutableStateOf<SchoolTask?>(null) }
    LaunchedEffect(lastDone) { if (lastDone != null) { kotlinx.coroutines.delay(6_000); lastDone = null } }
    val undoRow: @Composable () -> Unit = {
        lastDone?.let { t ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Secondary(stringResource(R.string.task_done_label) + ": „${t.title}“", modifier = Modifier.weight(1f))
                TextButton(onClick = { onUndo(t.id); lastDone = null }, modifier = Modifier.heightIn(min = SchoolTokens.RowMin)) {
                    Text(stringResource(R.string.task_undo))
                }
            }
        }
    }
    RegisterSubjects(f)
    val all = remember(f, today) { BriefLogic.visibleTasks(f.tasks, today) }
    val shown = all.take(BriefLogic.MaxRows)
    val stale = BriefLogic.isStale(f, now)
    SchoolCard(modifier = Modifier.alpha(if (stale) SchoolTokens.StaleAlpha else 1f)) {
        CardHeader(stringResource(R.string.widget_name_school_tasks), null)
        if (shown.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.check_circle_24px), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Secondary(stringResource(R.string.tasks_empty))
            }
            undoRow()
            return@SchoolCard
        }
        val accent = MaterialTheme.colorScheme.primary
        var lastGroup: String? = null
        for (row in shown) {
            val group = if (row.overdue) stringResource(R.string.task_overdue).replaceFirstChar { it.titlecase() }
            else dueLabel(row.due, today, null).replaceFirstChar { it.titlecase() }
            if (group != lastGroup) {
                Spacer(Modifier.height(if (lastGroup == null) 6.dp else 10.dp))
                Text(
                    group,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (row.overdue) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lastGroup = group
            }
            TaskRowItem(row, onDone = { lastDone = row.task; onDone(row.task.id) }) { Links.openSchoolhelp(context) }
        }
        undoRow()
        val more = all.size - shown.size
        if (more > 0) {
            TextButton(onClick = { Links.openSchoolhelp(context) }, modifier = Modifier.heightIn(min = SchoolTokens.RowMin)) {
                Text(stringResource(R.string.tasks_more, more))
            }
        }
    }
}

@Composable
private fun TaskRowItem(row: TaskRow, onDone: () -> Unit = {}, onClick: () -> Unit) {
    val t = row.task
    val kind = when (t.kind) {
        SchoolTask.Homework -> stringResource(R.string.task_kind_hausaufgabe)
        SchoolTask.Test -> stringResource(R.string.task_kind_test)
        SchoolTask.Project -> stringResource(R.string.task_kind_projekt)
        else -> stringResource(R.string.task_kind_termin)
    }
    val meta = listOfNotNull(
        t.subject.takeIf { it.isNotBlank() },
        t.time,
        kind.takeIf { t.kind == SchoolTask.Test || t.kind == SchoolTask.Project },
        stringResource(R.string.task_estimated).takeIf { t.estimated && !row.overdue },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(SchoolTokens.ItemRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindBadge(t, kind)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // Titles exactly as in Schoolhelp, never translated or shortened.
            Text(t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Secondary(meta)
        }
        // One-tap "Hotovo" for homework and projects: ticks it in Schoolhelp (only the student's own tick).
        if (t.kind == SchoolTask.Homework || t.kind == SchoolTask.Project) {
            IconButton(onClick = onDone) {
                Icon(
                    painterResource(R.drawable.check_circle_24px), contentDescription = stringResource(R.string.task_done),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Subject-colored circle with the task's kind icon (homework, test, project, appointment). */
@Composable
internal fun KindBadge(t: SchoolTask, kindLabel: String) {
    val c = subjectColors(t.subject)
    Box(
        Modifier.size(SchoolTokens.BadgeSmall).clip(CircleShape).background(c.container)
            .semantics { contentDescription = kindLabel },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(
                when (t.kind) {
                    SchoolTask.Homework -> R.drawable.edit_note_24px
                    SchoolTask.Test -> R.drawable.quiz_24px
                    SchoolTask.Project -> R.drawable.folder_24px
                    else -> R.drawable.event_24px
                }
            ),
            contentDescription = null, tint = c.onContainer, modifier = Modifier.size(20.dp),
        )
    }
}

// ---------------------------------------------------------------- Context

@Composable
fun ContextWidget(widget: ContextWidget) {
    val vm: ContextVM = viewModel(key = "school-context")
    val cards by vm.cards.collectAsState(emptyList())
    val locale: Locale = LocalConfiguration.current.locales[0]
    val items = cards.map { c -> contextItem(c, locale, vm) }
    if (items.isNotEmpty()) ContextContent(items)
}

@Composable
internal fun contextItem(c: ContextCard, locale: Locale, vm: ContextVM?): ContextItem {
    val nf = java.text.NumberFormat.getIntegerInstance(locale)
    val gb = { b: Long -> java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1; minimumFractionDigits = 1 }.format(b / 1e9) }
    val open: (() -> Unit)? = vm?.let { v -> { v.open(c) } }
    return when (c) {
        is ContextCard.Music -> ContextItem(
            key = "music", text = listOfNotNull(c.title, c.artist?.takeIf { it.isNotBlank() }).joinToString(" · "),
            icon = R.drawable.music_note_24px, onClick = open, playing = c.playing, onTogglePlay = vm?.let { v -> { v.togglePause() } },
            value = c.title, label = c.artist?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chip_name_music),
        )
        is ContextCard.MobileData -> ContextItem(
            "data", stringResource(R.string.context_mobile_data, gb(c.usedBytes), gb(c.planBytes)), R.drawable.data_usage_24px, onClick = open,
            value = "${gb(c.usedBytes)} GB", label = stringResource(R.string.glance_data_of, gb(c.planBytes)),
            progress = if (c.planBytes > 0) (c.usedBytes.toFloat() / c.planBytes).coerceIn(0f, 1f) else null,
        )
        is ContextCard.Birthday -> ContextItem(
            "birthday:${c.contactId}", stringResource(R.string.context_birthday, c.name), R.drawable.cake_24px, onClick = open,
            value = c.name, label = stringResource(R.string.chip_name_birthday),
        )
        is ContextCard.Steps -> ContextItem(
            "steps", stringResource(R.string.context_steps, nf.format(c.steps), nf.format(c.goal)), R.drawable.directions_walk_24px, onClick = open,
            value = nf.format(c.steps), label = stringResource(R.string.glance_steps_goal, nf.format(c.goal)),
            progress = if (c.goal > 0) (c.steps.toFloat() / c.goal).coerceIn(0f, 1f) else null,
        )
        is ContextCard.Instagram -> ContextItem(
            "instagram", stringResource(R.string.context_instagram_today, nf.format(c.minutes)), R.drawable.timer_24px,
            value = "${nf.format(c.minutes)} min", label = stringResource(R.string.glance_instagram),
        )
        is ContextCard.TestSoon -> ContextItem(
            "test:${c.title}",
            when (c.days) {
                0L -> stringResource(R.string.context_test_today, c.subject.ifBlank { c.title })
                1L -> stringResource(R.string.context_test_tomorrow, c.subject.ifBlank { c.title })
                else -> stringResource(R.string.context_test_in, c.subject.ifBlank { c.title }, c.days.toInt())
            },
            R.drawable.quiz_24px, onClick = open,
            value = c.subject.ifBlank { c.title },
            label = when (c.days) {
                0L -> stringResource(R.string.glance_test_today)
                1L -> stringResource(R.string.glance_test_tomorrow)
                else -> pluralStringResource(R.plurals.glance_test_in, c.days.toInt(), c.days.toInt())
            },
        )
        is ContextCard.Chats -> ContextItem(
            "chats", pluralStringResource(R.plurals.context_chats, c.unread, c.unread), R.drawable.chat_24px, onClick = open,
            value = c.unread.toString(), label = stringResource(R.string.glance_chats),
        )
        is ContextCard.Lunch -> {
            val menu = ContextRules.menuNumber(c.text)
            ContextItem(
                "lunch",
                menu?.let {
                    stringResource(if (c.tomorrow) R.string.context_lunch_menu_tomorrow else R.string.context_lunch_menu, it)
                } ?: stringResource(if (c.tomorrow) R.string.context_lunch_tomorrow else R.string.context_lunch, c.text),
                R.drawable.restaurant_24px, onClick = open,
                value = menu?.let { stringResource(R.string.glance_menu, it) } ?: c.text,
                label = stringResource(if (c.tomorrow) R.string.glance_lunch_tomorrow else R.string.chip_name_lunch),
            )
        }
        is ContextCard.NameDay -> ContextItem(
            "nameday",
            if (c.contactName != null) stringResource(R.string.context_nameday_contact, c.contactName)
            else stringResource(R.string.context_nameday, c.names.joinToString(", ")),
            R.drawable.celebration_24px,
            onClick = if (c.contactId != null) open else null,
            value = c.contactName ?: c.names.joinToString(", "),
            label = stringResource(R.string.chip_name_nameday),
        )
    }
}

/** Small chips: each card is one discrete, glanceable item. At most 3 (spec); they fade in when they appear. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContextContent(items: List<ContextItem>) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = SchoolTokens.BlockPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // At most 4 context cards, chosen and ordered by ContextRules.pick.
        for (item in items) {
            key(item.key) {
                val inspection = LocalInspectionMode.current
                val state = remember { MutableTransitionState(inspection).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = state,
                    enter = fadeIn(tween(SchoolTokens.EnterMs, easing = SchoolTokens.EnterEasing)) +
                        scaleIn(tween(SchoolTokens.EnterMs, easing = SchoolTokens.EnterEasing), initialScale = 0.95f),
                    exit = fadeOut(tween(SchoolTokens.ExitMs, easing = SchoolTokens.EnterEasing)) +
                        scaleOut(tween(SchoolTokens.ExitMs, easing = SchoolTokens.EnterEasing), targetScale = 0.95f),
                ) { ContextChip(item) }
            }
        }
    }
}

@Composable
private fun ContextChip(item: ContextItem) {
    val surface = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier
                .heightIn(min = SchoolTokens.RowMin)
                .clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                .background(surface)
                .then(if (item.onClick != null) Modifier.clickable(role = Role.Button, onClick = item.onClick) else Modifier)
                .padding(start = 14.dp, end = if (item.onTogglePlay != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.icon?.let {
                Icon(painterResource(it), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                item.text, style = MaterialTheme.typography.bodyLarge.merge(SchoolTokens.Tabular),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (item.onTogglePlay != null && item.playing != null) {
                IconButton(onClick = item.onTogglePlay) {
                    Icon(
                        painterResource(if (item.playing) R.drawable.pause_24px else R.drawable.play_arrow_24px),
                        contentDescription = stringResource(if (item.playing) R.string.context_pause else R.string.context_play),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Shared pieces

/** Timetable subjects (plus task subjects) get distinct badge colors. */
@Composable
internal fun RegisterSubjects(f: Feed) {
    remember(f) { SubjectPalette.register(f.timetable.values.flatten().map { it.subject } + f.tasks.map { it.subject }); 0 }
}

/** One block = one calm card: tinted surface, 24dp corners, no border, no shadow. */
@Composable
internal fun SchoolCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = SchoolTokens.CardAlpha)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(vertical = SchoolTokens.BlockPad)
                .clip(RoundedCornerShape(SchoolTokens.CardRadius))
                .background(surface)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(SchoolTokens.Inset),
            content = content,
        )
    }
}

/** Same frame as the other blocks, for blocks in other files (Gemini button: no card). */
@Composable
internal fun SchoolBlockFrame(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides schoolContentColor()) {
        Column(Modifier.fillMaxWidth().padding(vertical = SchoolTokens.BlockPad), content = content)
    }
}

@Composable
internal fun CardHeader(title: String, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
        trailing?.let { Secondary(it) }
    }
}

@Composable
internal fun SubjectBadge(subject: String, size: androidx.compose.ui.unit.Dp, fontSize: Int = 16) {
    val c = subjectColors(subject)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (size > 32.dp) SchoolTokens.BadgeRadius else 8.dp)).background(c.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(subjectTag(subject), color = c.onContainer, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IconBadge(@DrawableRes icon: Int, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(SchoolTokens.BadgeRadius)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun Secondary(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.merge(SchoolTokens.Tabular),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Thin progress through the current lesson in the accent; the one accent on the card. */
@Composable
private fun ProgressLine(progress: Float) {
    val percent = (progress * 100).toInt()
    val label = stringResource(R.string.lesson_progress, percent)
    Box(
        Modifier.fillMaxWidth().height(SchoolTokens.ProgressHeight).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = label }
    ) {
        Box(Modifier.fillMaxWidth(progress).height(SchoolTokens.ProgressHeight).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}

/** "dnes", "zajtra", weekday within a week, else short date; locale-aware. */
@Composable
internal fun dueLabel(due: LocalDate, today: LocalDate, time: String?): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val days = ChronoUnit.DAYS.between(today, due)
    val day = when {
        days == 0L -> stringResource(R.string.due_today)
        days == 1L -> stringResource(R.string.due_tomorrow)
        days in 2..6 -> due.format(DateTimeFormatter.ofPattern("EEEE", locale))
        else -> due.format(DateTimeFormatter.ofPattern("EEE d. M.", locale))
    }
    return if (time != null) "$day $time" else day
}

private fun updatedTime(feed: Feed): String =
    runCatching { OffsetDateTime.parse(feed.updated).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }
        .getOrDefault("–")

// ---------------------------------------------------------------- Single chip blocks


/** One chip as its own block: its card's value in the block's style (birthdays: one per person). */
@Composable
fun ChipWidget(widget: de.mm20.launcher2.widgets.ChipWidget) {
    val vm: ContextVM = viewModel(key = "school-context")
    val all by vm.allCards.collectAsState(emptyList())
    val usage by vm.usageAccess.collectAsState()
    val locale: Locale = LocalConfiguration.current.locales[0]
    val mine = all.filter { it.kind() == widget.kind }
        .filter { widget.kind != "nameday" || !widget.config.onlyContacts || (it as ContextCard.NameDay).contactName != null }
    when {
        mine.isNotEmpty() -> ChipBlock(mine.map { contextItem(it, locale, vm) }, widget.config)
        // Without usage access these blocks would stay empty (device test: "mobile data doesn't work").
        !usage && (widget.kind == "data" || widget.kind == "instagram") -> ContextContent(
            listOf(ContextItem("setup", stringResource(R.string.chip_needs_usage), R.drawable.data_usage_24px, onClick = { vm.openUsageAccess() }))
        )
    }
}
