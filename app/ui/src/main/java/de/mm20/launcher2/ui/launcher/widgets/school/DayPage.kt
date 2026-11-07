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
 * Aufgabe". The same bar switches to asking the assistant.
 */
@Composable
fun DayPage(listState: LazyListState = rememberLazyListState()) {
    val brief: SchoolBriefVM = viewModel(key = "school-brief")
    val vm: DayVM = viewModel(key = "school-day")
    val feed by brief.feed.collectAsState(null)
    val now by brief.now.collectAsState(LocalDateTime.now())
    val today by vm.today.collectAsState()
    LaunchedEffect(now) { vm.refresh() }
    var asking by rememberSaveable { mutableStateOf(false) }
    DayContent(
        feed = feed, today = today, now = now, listState = listState,
        asking = asking, onAsking = { asking = it },
        onAdd = vm::add, onTodo = vm::setTodo, onStep = vm::setStep,
        chat = { bottom -> ChatDock(Modifier, bottomInset = bottom) },
    )
}

@Composable
internal fun DayContent(
    feed: Feed?,
    today: TodayData?,
    now: LocalDateTime,
    listState: LazyListState = rememberLazyListState(),
    asking: Boolean,
    onAsking: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onTodo: (String, Boolean) -> Unit,
    onStep: (Int, Boolean) -> Unit,
    chat: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
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
                            style = SchoolFonts.display(36),
                        )
                        val summary = listOfNotNull(
                            date.format(DateTimeFormatter.ofPattern("d. MMMM", locale)),
                            (openTodos + openSteps).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.day_open, it, it) },
                        ).joinToString(" · ")
                        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            0.25f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        )
                    )
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModeSwitch(asking, onAsking, Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
                if (asking) chat(bottom) else CaptureBar(onAdd, Modifier.padding(bottom = bottom + 12.dp))
            }
        }
    }
}

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
    CheckLine(
        text = s.text, done = s.done,
        meta = listOfNotNull(s.minutes?.let { stringResource(R.string.day_minutes, it) }, s.subject.takeIf { it.isNotBlank() }).joinToString(" · "),
        onToggle = { onStep(s.index, !s.done) },
    )
}

@Composable
private fun TodoLine(t: Todo, onTodo: (String, Boolean) -> Unit) {
    CheckLine(text = t.text, done = t.done, meta = null, onToggle = { onTodo(t.id, !t.done) })
}

/** One checkable row: the whole row toggles; done = filled circle, struck-through text. */
@Composable
private fun CheckLine(text: String, done: Boolean, meta: String?, onToggle: () -> Unit) {
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
    }
}

/** "Úloha | Opýtaj sa": two connected segments, the selected one filled; one decision, one bar. */
@Composable
private fun ModeSwitch(asking: Boolean, onAsking: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        ) {
            for ((value, label) in listOf(false to R.string.day_mode_task, true to R.string.day_mode_ask)) {
                val selected = asking == value
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.Tab) { onAsking(value) }
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** Type or say a task; Enter or the button saves it to Schoolhelp. */
@Composable
private fun CaptureBar(onAdd: (String) -> Unit, modifier: Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { said ->
            // Spoken tasks are saved at once: saying it is the confirmation.
            onAdd(said)
        }
    }
    val save = { if (input.isNotBlank()) { onAdd(input); input = "" } }
    Row(
        modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 20.dp, end = 6.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (input.isEmpty()) Text(stringResource(R.string.day_capture_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                input, { input = it.take(200) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (input.isBlank()) {
            IconButton(onClick = {
                runCatching {
                    voice.launch(
                        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "sk-SK")
                    )
                }
            }) { Icon(painterResource(R.drawable.mic_24px), contentDescription = stringResource(R.string.chat_voice)) }
        } else {
            // The page's one primary action: ink circle.
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)
                    .clickable(role = Role.Button) { save() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.add_24px), contentDescription = stringResource(R.string.day_add), tint = MaterialTheme.colorScheme.surface)
            }
        }
    }
}
