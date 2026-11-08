package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.Feed
import de.mm20.launcher2.schoolbrief.Lesson
import de.mm20.launcher2.schoolbrief.PlanStep
import de.mm20.launcher2.schoolbrief.TodayData
import de.mm20.launcher2.schoolbrief.Todo
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DayVM : ViewModel(), KoinComponent {
    private val brief: BriefRepository by inject()
    val today: StateFlow<TodayData?> = brief.today
    fun refresh() = brief.refreshToday()
    fun add(text: String) = brief.addTodo(text)
    fun setTodo(id: String, done: Boolean) = brief.setTodoDone(id, done)
    fun setStep(index: Int, done: Boolean) = brief.setStepDone(index, done)
}

/**
 * "Môj deň" (round 7, the page to the left of home): today's timeline (lessons, Schoolhelp's plan, my own
 * tasks) and one primary action at the bottom: write or say a task, saved to Schoolhelp as an "eigene
 * Aufgabe". The same bar switches to asking the assistant and to a focus session (round 15).
 */
@Composable
fun DayPage(
    listState: LazyListState = rememberLazyListState(),
    shown: androidx.compose.runtime.State<Boolean>? = null,
    progress: () -> Float = { 1f },
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val leaving = shown?.value == false
    LaunchedEffect(leaving) { if (leaving) focus.clearFocus(force = true) }
    val brief: SchoolBriefVM = viewModel(key = "school-brief")
    val vm: DayVM = viewModel(key = "school-day")
    val feed by brief.feed.collectAsState(null)
    val now by brief.now.collectAsState(LocalDateTime.now())
    val today by vm.today.collectAsState()
    LaunchedEffect(now) { vm.refresh() }
    var mode by rememberSaveable { mutableStateOf(DayBarMode.Task) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Rechecked on every return to the launcher (the user grants it in system settings).
    var canBlock by androidx.compose.runtime.remember { mutableStateOf(FocusGuard.canBlock(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        canBlock = FocusGuard.canBlock(context)
        onPauseOrDispose {}
    }
    DayContent(
        feed = feed, today = today, now = now, listState = listState,
        mode = mode, onMode = { mode = it },
        canBlock = canBlock, onEnableBlocking = { FocusGuard.requestMissing(context) },
        onAdd = vm::add, onTodo = vm::setTodo, onStep = vm::setStep,
        chat = { bottom -> ChatDock(Modifier, bottomInset = bottom) },
        barProgress = progress,
    )
}

@Composable
internal fun DayContent(
    feed: Feed?,
    today: TodayData?,
    now: LocalDateTime,
    listState: LazyListState = rememberLazyListState(),
    mode: DayBarMode,
    onMode: (DayBarMode) -> Unit,
    canBlock: Boolean = true,
    onEnableBlocking: () -> Unit = {},
    onAdd: (String) -> Unit,
    onTodo: (String, Boolean) -> Unit,
    onStep: (Int, Boolean) -> Unit,
    chat: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
    barProgress: () -> Float = { 1f },
) {
    val locale = LocalConfiguration.current.locales[0]
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.ime.union(WindowInsets.navigationBars).asPaddingValues().calculateBottomPadding()
    val date = now.toLocalDate()
    val lessons = feed?.let { lessonsOn(it, date) }.orEmpty()
    val time = now.toLocalTime().toString().take(5)
    // Lessons only while they still matter: before and during school.
    val showLessons = lessons.isNotEmpty() && lessons.last().end > time
    val openTodos = today?.todos?.count { !it.done } ?: 0
    val openSteps = today?.steps?.count { !it.done } ?: 0

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        // Transparent like the widget panel (round 13): the scaffold's page scrim slides with the page.
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = top + 28.dp, bottom = bottom + 180.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEEE", locale)).replaceFirstChar { it.titlecase(locale) },
                            // The page title in the soft serif: "Môj deň" reads like a journal, home like a dashboard.
                            style = SchoolFonts.serifDisplay(44),
                        )
                        val summary = listOfNotNull(
                            date.format(DateTimeFormatter.ofPattern("d. MMMM", locale)),
                            (openTodos + openSteps).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.day_open, it, it) },
                        ).joinToString(" · ")
                        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (today?.offline == true) {
                            // Honest status: nothing is lost, it waits on the phone.
                            Spacer(Modifier.height(8.dp))
                            Secondary(stringResource(R.string.day_offline))
                        }
                    }
                }
                item { FocusPanel() }
                if (showLessons) {
                    item { DaySection(stringResource(R.string.day_section_lessons)) }
                    items(lessons, key = { "l" + it.start }) { l -> LessonLine(l, time) }
                }
                if (today != null && today.steps.isNotEmpty()) {
                    val minutes = today.steps.filter { !it.done }.sumOf { it.minutes ?: 0 }
                    item { DaySection(stringResource(R.string.day_section_plan), minutes.takeIf { it > 0 }?.let { stringResource(R.string.day_minutes_total, it) }) }
                    items(today.steps, key = { "s" + it.index }) { s -> StepLine(s, onStep) }
                }
                item { DaySection(stringResource(R.string.day_section_todos)) }
                if (today == null || today.todos.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.day_todos_empty),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                } else {
                    // Open first, then the done ones (struck through) until Schoolhelp clears them.
                    items(today.todos.sortedBy { it.done }, key = { "t" + it.id }) { t -> TodoLine(t, onTodo) }
                }
            }

            val travel = pillTravelPx()
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    // The task bar arrives like the search pill (round 14).
                    .pillArriving(travel, barProgress)
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Segmented(
                        listOf(
                            DayBarMode.Task to stringResource(R.string.day_mode_task),
                            DayBarMode.Ask to stringResource(R.string.day_mode_ask),
                            DayBarMode.Focus to stringResource(R.string.focus_label),
                        ),
                        selected = mode, onSelect = onMode,
                    )
                }
                when (mode) {
                    DayBarMode.Task -> CaptureBar(onAdd, Modifier.padding(bottom = bottom + 12.dp))
                    DayBarMode.Ask -> chat(bottom)
                    DayBarMode.Focus -> FocusBar(canBlock, onEnableBlocking, Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp).padding(bottom = bottom + 12.dp))
                }
            }
        }
    }
}

/** What the bottom bar of "Môj deň" does: add a task, ask the assistant, or focus. */
enum class DayBarMode { Task, Ask, Focus }

@Composable
private fun DaySection(title: String, trailing: String? = null) {
    Row(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 28.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium.merge(SchoolTokens.Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** "08:00  Physik": the running lesson carries the accent dot, past ones fade. */
@Composable
private fun LessonLine(l: Lesson, now: String) {
    val past = l.end <= now
    val current = l.start <= now && now < l.end
    Row(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 44.dp).alpha(if (past) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(l.start, style = MaterialTheme.typography.bodyMedium.merge(SchoolTokens.Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (current) MaterialTheme.colorScheme.primary else subjectColors(l.subject).container))
        Spacer(Modifier.width(14.dp))
        Text(
            l.subject,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text("– " + l.end, style = MaterialTheme.typography.bodyMedium.merge(SchoolTokens.Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepLine(s: PlanStep, onStep: (Int, Boolean) -> Unit) {
    val startFocus = rememberFocusStarter()
    CheckLine(
        text = s.text, done = s.done,
        meta = listOfNotNull(s.minutes?.let { stringResource(R.string.day_minutes, it) }, s.subject.takeIf { it.isNotBlank() }).joinToString(" · "),
        onToggle = { onStep(s.index, !s.done) },
        trailing = if (s.done || s.minutes == null) null else ({
            IconButton(onClick = { startFocus(s.index, s.text, s.minutes ?: return@IconButton) }) {
                Icon(painterResource(R.drawable.play_arrow_24px), contentDescription = stringResource(R.string.focus_start, s.minutes ?: 0))
            }
        }),
    )
}

@Composable
private fun TodoLine(t: Todo, onTodo: (String, Boolean) -> Unit) {
    CheckLine(text = t.text, done = t.done, meta = if (t.pending) stringResource(R.string.day_pending) else null, onToggle = { onTodo(t.id, !t.done) })
}

/** One checkable row: the whole row toggles; done = filled circle, struck-through text. */
@Composable
private fun CheckLine(text: String, done: Boolean, meta: String?, onToggle: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val ring by animateColorAsState(if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, label = "ring")
    val label = stringResource(if (done) R.string.day_mark_open else R.string.task_done)
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(SchoolTokens.ItemRadius))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = "$label: $text" }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(if (done) Modifier.background(ring) else Modifier.border(2.dp, ring, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.check_20px), null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (done) TextDecoration.LineThrough else null),
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (!meta.isNullOrBlank()) Secondary(meta)
        }
        trailing?.invoke()
    }
}

/** Type or say a task; Enter or the ink button saves it. */
@Composable
private fun CaptureBar(onAdd: (String) -> Unit, modifier: Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    // What is heard fills the field; the user checks or edits it and saves with the button (device test:
    // "it may hear me wrong", saving at once left no chance to fix it).
    val speak = rememberSpeechInput(onText = { input = it.take(200) }, onPartial = { input = it.take(200) })
    InputBar(
        value = input, onValue = { input = it.take(200) },
        hint = stringResource(R.string.day_capture_hint),
        submitIcon = R.drawable.add_24px, submitLabel = stringResource(R.string.day_add),
        onSubmit = { if (input.isNotBlank()) { onAdd(input); input = "" } },
        voice = speak,
        imeAction = ImeAction.Done,
        modifier = modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp),
    )
}
