package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.luminance
import de.mm20.launcher2.schoolbrief.BriefStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefLogic
import de.mm20.launcher2.schoolbrief.Feed
import de.mm20.launcher2.schoolbrief.LessonState
import de.mm20.launcher2.schoolbrief.TaskRow
import de.mm20.launcher2.ui.R
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

/** Schoolhelp's web app. Deep links to single tasks are unverified (spec risk), so taps open the start page. */
private const val SchoolhelpUrl = "https://lessons.labeltranslator.com/"

@Composable
fun LessonWidget(widget: LessonWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed ?: return
    LessonContent(f, now)
}

@Composable
internal fun LessonContent(f: Feed, now: LocalDateTime) {
    val context = LocalContext.current
    SchoolBlock {
        val content = LocalContentColor.current
        val accent = schoolAccent(content)
        Column(
            Modifier
                .fillMaxWidth()
                .pressable(role = Role.Button) { open(context, SchoolhelpUrl) }
                .padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap)
        ) {
            when (val s = BriefLogic.lessonState(f, now)) {
                is LessonState.InLesson -> {
                    PrimaryLine(s.current.subject, trailing = stringResource(R.string.lesson_until, s.current.end))
                    Spacer(Modifier.height(SchoolTokens.Gap))
                    ProgressLine(s.progress, accent)
                    s.next?.let {
                        Spacer(Modifier.height(SchoolTokens.Gap))
                        SecondaryLine(stringResource(R.string.lesson_next, it.subject, it.start))
                    }
                }
                is LessonState.Upcoming -> PrimaryLine(s.next.subject, trailing = stringResource(R.string.lesson_upcoming_at, s.next.start))
                is LessonState.DayOver -> {
                    PrimaryLine(stringResource(R.string.lesson_day_over))
                    s.tomorrowFirst?.let {
                        Spacer(Modifier.height(SchoolTokens.Tight))
                        SecondaryLine(stringResource(R.string.lesson_tomorrow, it.subject, it.start))
                    }
                }
                is LessonState.DayOff -> PrimaryLine(s.title)
                LessonState.Weekend -> PrimaryLine(stringResource(R.string.lesson_weekend))
            }
        }
    }
}

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

/**
 * Login, loading and error states. With cached data on screen only an expired login is shown;
 * a failed refresh just lets the data age into "staré údaje".
 */
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
    SchoolBlock {
        Column(Modifier.padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap)) {
            Text(line, style = MaterialTheme.typography.bodyLarge)
            action?.let { (label, onClick) ->
                Spacer(Modifier.height(SchoolTokens.Gap))
                val content = LocalContentColor.current
                if (filled) {
                    // Ink button: the one action of this state.
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = content,
                            contentColor = if (content.luminance() > 0.5f) Color(0xFF15171A) else Color(0xFFF7F7F8),
                        ),
                        modifier = Modifier.heightIn(min = SchoolTokens.RowMin),
                    ) { Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) }
                } else {
                    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = SchoolTokens.RowMin)) {
                        Text(label, color = content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun BriefContent(f: Feed, now: OffsetDateTime) {
    val context = LocalContext.current
    val stale = BriefLogic.isStale(f, now)
    SchoolBlock(Modifier.alpha(if (stale) SchoolTokens.StaleAlpha else 1f)) {
        Column(Modifier.padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap)) {
            Text(
                text = f.brief,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(SchoolTokens.Tight))
            SecondaryLine(
                if (stale) stringResource(R.string.school_stale) + " · " + stringResource(R.string.school_updated_at, updatedTime(f))
                else stringResource(R.string.school_updated_at, updatedTime(f))
            )
        }
        Spacer(Modifier.height(SchoolTokens.Gap))
        val openLabel = stringResource(R.string.open_article)
        for (item in f.news) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SchoolTokens.RowMin)
                    .pressable(role = Role.Button) { open(context, item.url) }
                    .semantics { contentDescription = "$openLabel: ${item.title}" }
                    .padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap),
            )
        }
    }
}

@Composable
fun TasksWidget(widget: TasksWidget) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed ?: return
    TasksContent(f, now.toLocalDate(), OffsetDateTime.now())
}

@Composable
internal fun TasksContent(f: Feed, today: LocalDate, now: OffsetDateTime) {
    val context = LocalContext.current
    val rows = remember(f, today) { BriefLogic.visibleTasks(f.tasks, today) }
    val stale = BriefLogic.isStale(f, now)
    SchoolBlock(Modifier.alpha(if (stale) SchoolTokens.StaleAlpha else 1f)) {
        Text(
            text = stringResource(R.string.widget_name_school_tasks),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = SchoolTokens.TightTracking),
            modifier = Modifier.padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap),
        )
        if (rows.isEmpty()) {
            SecondaryLine(
                stringResource(R.string.tasks_empty),
                Modifier.padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap),
            )
        }
        for (row in rows) {
            TaskRowItem(row, today) { open(context, SchoolhelpUrl) }
        }
    }
}

@Composable
fun ContextWidget(widget: ContextWidget) {
    // Build step 1: sample items. Each real rule (music, Instagram, steps, data, birthday) arrives in step 5.
    val items = listOf(
        ContextItem("instagram", stringResource(R.string.context_instagram_today, "47")),
        ContextItem("steps", stringResource(R.string.context_steps, "3 120", "8 000")),
    )
    ContextContent(items)
}

/** At most 3 cards at once (spec); the caller orders them: music first, then by urgency. */
@Composable
internal fun ContextContent(items: List<ContextItem>) {
    SchoolBlock {
        for (item in items.take(3)) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(SchoolTokens.EnterMs, easing = SchoolTokens.EnterEasing)) +
                    scaleIn(tween(SchoolTokens.EnterMs, easing = SchoolTokens.EnterEasing), initialScale = 0.95f),
                exit = fadeOut(tween(SchoolTokens.ExitMs, easing = SchoolTokens.EnterEasing)) +
                    scaleOut(tween(SchoolTokens.ExitMs, easing = SchoolTokens.EnterEasing), targetScale = 0.95f),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = SchoolTokens.RowMin)
                        .padding(horizontal = SchoolTokens.Inset),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(item.text, style = MaterialTheme.typography.bodyLarge.merge(SchoolTokens.Tabular))
                }
            }
        }
    }
}

@Composable
private fun TaskRowItem(row: TaskRow, today: LocalDate, onClick: () -> Unit) {
    val content = LocalContentColor.current
    val accent = schoolAccent(content)
    val kind = when (row.task.kind) {
        "hausaufgabe" -> stringResource(R.string.task_kind_hausaufgabe)
        "test" -> stringResource(R.string.task_kind_test)
        "projekt" -> stringResource(R.string.task_kind_projekt)
        else -> stringResource(R.string.task_kind_termin)
    }
    val when_ = if (row.overdue) stringResource(R.string.task_overdue) else dueLabel(row.due, today, row.task.time)
    val meta = listOfNotNull(
        when_,
        row.task.subject.takeIf { it.isNotBlank() },
        kind,
        stringResource(R.string.task_estimated).takeIf { row.task.estimated && !row.overdue },
    ).joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = SchoolTokens.RowMin)
            .pressable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SchoolTokens.Inset, vertical = SchoolTokens.Gap),
    ) {
        Text(
            // Titles exactly as in Schoolhelp, never translated or shortened.
            text = row.task.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(SchoolTokens.Tight))
        Text(
            text = meta,
            style = MaterialTheme.typography.bodyLarge.merge(SchoolTokens.Tabular),
            color = if (row.overdue) accent else content.copy(alpha = SchoolTokens.SecondaryAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A block drawn straight on the wallpaper: no box, only content color and spacing. */
@Composable
private fun SchoolBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides schoolContentColor()) {
        Column(modifier.fillMaxWidth().padding(vertical = SchoolTokens.BlockPad), content = content)
    }
}

@Composable
private fun PrimaryLine(text: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = SchoolTokens.TightTracking),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.width(SchoolTokens.Gap))
            SecondaryLine(trailing)
        }
    }
}

@Composable
private fun SecondaryLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.merge(SchoolTokens.Tabular),
        color = LocalContentColor.current.copy(alpha = SchoolTokens.SecondaryAlpha),
        modifier = modifier,
    )
}

@Composable
private fun ProgressLine(progress: Float, accent: Color) {
    val track = LocalContentColor.current.copy(alpha = 0.16f)
    val percent = (progress * 100).toInt()
    val label = stringResource(R.string.lesson_progress, percent)
    Box(
        Modifier
            .fillMaxWidth()
            .height(SchoolTokens.ProgressHeight)
            .clip(RoundedCornerShape(SchoolTokens.ProgressHeight))
            .background(track)
            .semantics { contentDescription = label }
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(SchoolTokens.ProgressHeight)
                .background(accent)
        )
    }
}

/** Press highlight only; no resting surface. */
private fun Modifier.pressable(role: Role, onClick: () -> Unit): Modifier =
    this.clip(RoundedCornerShape(SchoolTokens.PressRadius)).clickable(role = role, onClick = onClick)

@Composable
private fun dueLabel(due: LocalDate, today: LocalDate, time: String?): String {
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

private fun open(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
