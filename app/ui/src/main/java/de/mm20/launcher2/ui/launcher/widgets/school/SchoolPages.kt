package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefLogic
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.ChatMessage
import de.mm20.launcher2.schoolbrief.Feed
import de.mm20.launcher2.schoolbrief.SchoolChat
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------- Chat ("Spýtaj sa")

class SchoolChatVM : ViewModel(), KoinComponent {
    private val chat: SchoolChat by inject()
    private val brief: BriefRepository by inject()

    val messages = mutableStateListOf<ChatMessage>()
    var thinking by mutableStateOf(false)
        private set
    val hasKey: Boolean get() = chat.hasKey
    /** Observable key state, so the dock switches from "add key" to chat at once. */
    val keyState = brief.hasGeminiKey
    fun saveKey(key: String) = brief.setGeminiKey(key)

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        messages += ChatMessage(true, q)
        thinking = true
        viewModelScope.launch {
            val feed = brief.feed.first()
            val answer = chat.reply(messages.toList(), feed)
            messages += ChatMessage(false, answer.getOrElse { "Odpoveď sa nepodarilo načítať (${it.message}). Skús to znova." })
            thinking = false
        }
    }

    fun clear() = messages.clear()
}

/** The chat page: school-aware Gemini, suggestion chips, keyboard only after tapping the field. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchoolChatPage(contentPadding: PaddingValues, listState: LazyListState = rememberLazyListState()) =
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        ChatPageContent(contentPadding, listState)
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatPageContent(contentPadding: PaddingValues, listState: LazyListState) {
    val vm: SchoolChatVM = viewModel(key = "school-chat")
    var input by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    LaunchedEffect(vm.messages.size) { if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size) }

    Column(Modifier.fillMaxSize().padding(contentPadding).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f)) {
            Text(
                stringResource(R.string.chat_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp)) {
                item {
                    if (vm.messages.isEmpty()) {
                        Text(
                            stringResource(if (vm.hasKey) R.string.chat_intro else R.string.chat_no_key),
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                        if (vm.hasKey) {
                            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (q in listOf(R.string.chat_q_tomorrow, R.string.chat_q_test, R.string.chat_q_week, R.string.chat_q_learn)) {
                                    val text = stringResource(q)
                                    Box(
                                        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .clickable(role = Role.Button) { vm.send(text) }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(text, style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                        }
                    }
                }
                items(vm.messages) { m -> Bubble(m) }
                if (vm.thinking) item { Bubble(ChatMessage(false, "…")) }
            }
        }
        // Input row; the keyboard opens only when the field is tapped.
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 18.dp, end = 4.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text(stringResource(R.string.chat_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    input, { input = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && vm.hasKey) {
                Icon(painterResource(R.drawable.arrow_forward_20px), contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Text(
            m.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (m.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

// ---------------------------------------------------------------- Week ("Týždeň")

/** The next five school days: lessons with subject badges, and what is due that day. */
@Composable
fun SchoolWeekPage(contentPadding: PaddingValues) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val now by vm.now.collectAsState(LocalDateTime.now())
    val f = feed
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Text(
                    stringResource(R.string.week_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
            if (f == null) {
                item { Text(stringResource(R.string.school_loading), modifier = Modifier.padding(8.dp)) }
            } else {
                items(schoolDays(f, now.toLocalDate()), key = { it.toString() }) { d -> DayCard(f, d, now.toLocalDate()) }
            }
        }
    }
}

internal fun schoolDays(f: Feed, from: LocalDate, count: Int = 5): List<LocalDate> {
    val out = mutableListOf<LocalDate>()
    var d = from
    var guard = 0
    while (out.size < count && guard++ < 30) {
        val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
        val off = f.daysOff.any { !d.isBefore(LocalDate.parse(it.start)) && !d.isAfter(LocalDate.parse(it.end)) }
        if (!weekend && !off) out += d
        d = d.plusDays(1)
    }
    return out
}

@Composable
internal fun DayCard(f: Feed, day: LocalDate, today: LocalDate) =
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        DayCardContent(f, day, today)
    }

@Composable
private fun DayCardContent(f: Feed, day: LocalDate, today: LocalDate) {
    RegisterSubjects(f)
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val key = when (day.dayOfWeek) {
        DayOfWeek.MONDAY -> "mon"; DayOfWeek.TUESDAY -> "tue"; DayOfWeek.WEDNESDAY -> "wed"
        DayOfWeek.THURSDAY -> "thu"; else -> "fri"
    }
    val lessons = f.timetable[key].orEmpty().sortedBy { it.start }
    val due = BriefLogic.visibleTasks(f.tasks, today).filter { it.due == day && !it.overdue }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(SchoolTokens.CardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(SchoolTokens.Inset),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dueLabel(day, today, null).replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f),
            )
            Secondary(day.format(DateTimeFormatter.ofPattern("d. M.", locale)))
        }
        Spacer(Modifier.height(10.dp))
        for (l in lessons) {
            Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                Secondary(l.start, Modifier.width(52.dp))
                SubjectBadge(l.subject, 28.dp, fontSize = 12)
                Spacer(Modifier.width(10.dp))
                Text(l.subject, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (due.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            for (r in due) {
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    KindBadge(r.task, r.task.kind)
                    Spacer(Modifier.width(12.dp))
                    Text(r.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Chat dock (side panel)

/**
 * The chat docked at the bottom of the side panel. Collapsed it is one input line; after the first
 * question the conversation opens above it. Without a Gemini key it asks for one right here.
 * Bottom padding comes from the scaffold insets, which already include the keyboard (no imePadding:
 * doing both pushed the field off screen in the device test).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatDock(modifier: Modifier, bottomInset: androidx.compose.ui.unit.Dp) {
    val vm: SchoolChatVM = viewModel(key = "school-chat")
    val hasKey by vm.keyState.collectAsState()
    var input by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size, vm.thinking) { if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size) }
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 12.dp).padding(bottom = bottomInset + 8.dp)) {
            if (vm.messages.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.chat_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        androidx.compose.material3.TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.chat_close)) }
                    }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = listState, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(vm.messages) { m -> Bubble(m) }
                        if (vm.thinking) item { Bubble(ChatMessage(false, "…")) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!hasKey) {
                KeyRow(onSave = { vm.saveKey(it) })
            } else {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(surface)
                        .padding(start = 18.dp, end = 4.dp).heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) Text(stringResource(R.string.chat_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            input, { input = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank()) {
                        Icon(painterResource(R.drawable.arrow_forward_20px), contentDescription = stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }
}

/** Inline Gemini key entry (device test: the key setting was hard to find). Stored encrypted on the phone. */
@Composable
private fun KeyRow(onSave: (String) -> Unit) {
    var key by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
    ) {
        Text(stringResource(R.string.chat_key_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.brief_settings_gemini_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(
            key, { key = it.trim() }, singleLine = true,
            label = { Text(stringResource(R.string.brief_settings_gemini)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            androidx.compose.material3.Button(onClick = { onSave(key) }, enabled = key.length >= 20) { Text(stringResource(R.string.brief_settings_gemini_save)) }
            androidx.compose.material3.TextButton(onClick = {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.brief_settings_gemini_get)) }
        }
    }
}
