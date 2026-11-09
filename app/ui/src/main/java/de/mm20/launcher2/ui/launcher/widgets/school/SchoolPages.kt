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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
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

    // Round 22: recent chats, saved on the phone after every answer.
    private val history = ChatHistory(org.koin.core.context.GlobalContext.get().get<android.content.Context>())
    private var chatId: Long = 0
    var recent by mutableStateOf(history.all())
        private set

    private fun persist() {
        if (messages.isEmpty()) return
        if (chatId == 0L) chatId = System.currentTimeMillis()
        history.save(chatId, messages.toList())
        recent = history.all()
    }

    /** Opens a saved chat to read or continue. */
    fun restore(c: SavedChat) {
        if (thinking) return
        messages.clear(); messages.addAll(c.messages); chatId = c.id
    }
    val hasKey: Boolean get() = chat.hasKey
    /** Observable key state, so the dock switches from "add key" to chat at once. */
    val keyState = brief.hasGeminiKey
    fun saveKey(key: String) = brief.setGeminiKey(key)

    /** The chat field's text, shared by the bar and the Claude button (round 31). */
    var draft by mutableStateOf("")

    /** Gemini's answer, or the failure in words marked [ChatMessage.failed] (shown with "Skúsiť znova"). */
    private fun reply(answer: Result<String>) = answer.fold(
        { ChatMessage(false, it) },
        { ChatMessage(false, it.message ?: "Odpoveď sa nepodarilo načítať.", failed = true) },
    )

    /** Asks again after a failed answer (round 31: the failure is not sent to Gemini as context). */
    fun retry() {
        if (thinking || messages.lastOrNull()?.failed != true) return
        messages.removeAt(messages.lastIndex)
        if (messages.lastOrNull()?.fromUser != true) return
        thinking = true
        viewModelScope.launch {
            messages += reply(chat.reply(messages.toList(), brief.feed.first()))
            thinking = false
            persist()
        }
    }

    /**
     * Round 31 (user: "just a Claude button"): the question goes to the Claude app, which has the Max plan
     * and the Schoolhelp connector. The field's text, else the last question asked here.
     */
    fun askClaude(context: android.content.Context) {
        val q = draft.trim().ifEmpty { messages.lastOrNull { it.fromUser }?.let { it.modelText ?: it.text }.orEmpty() }
        Links.askClaude(context, q)
        if (draft.isNotBlank()) draft = ""
    }

    fun send(text: String, modelText: String? = null) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        messages += ChatMessage(true, q, modelText)
        thinking = true
        viewModelScope.launch {
            val feed = brief.feed.first()
            messages += reply(chat.reply(messages.toList(), feed))
            thinking = false
            persist()
        }
    }

    /** A quick action whose prompt needs Schoolhelp's lesson topics first (stoff.read). */
    private fun sendWithTopics(label: String, folder: String, prompt: (de.mm20.launcher2.schoolbrief.StudyContext?) -> String) {
        if (thinking) return
        // The short label shows at once; the full prompt is filled in once the topics are loaded.
        messages += ChatMessage(true, label)
        val at = messages.lastIndex
        thinking = true
        viewModelScope.launch {
            val ctx = folder.takeIf { it.isNotBlank() }?.let { brief.studyContext(it) }
            if (at in messages.indices) messages[at] = ChatMessage(true, label, prompt(ctx))
            messages += reply(chat.reply(messages.toList(), brief.feed.first()))
            thinking = false
            persist()
        }
    }

    /** Test prep (device test: Gemini did not know what a "Leistungsnachweis" covers and kept asking back). */
    fun practiseTest(t: de.mm20.launcher2.schoolbrief.SchoolTask, due: String, label: String) {
        clear()
        sendWithTopics(label, t.subject) { c ->
            buildString {
                append("Priprav ma na test „${t.title}“ z predmetu ${t.subject.ifBlank { "?" }} ($due). ")
                append("Je to písomná skúška na známku. ")
                if (c != null && c.describe().isNotBlank()) append("Učivo podľa záznamov hodín v Schoolhelp: ${c.describe()}. ")
                t.note?.takeIf { it.isNotBlank() }?.let { append("Poznámka k testu: $it. ") }
                c?.lang?.let { append("Jazyk predmetu: $it. ") }
                if (c == null || c.describe().isBlank()) append("Záznamy hodín nemám; spýtaj sa ma jednou vetou, ktoré témy test pokrýva. ")
                else append("Začni hneď prvou otázkou z tohto učiva, od ľahšej k ťažšej.")
            }
        }
    }

    fun explainTopic(folder: String, label: String) = sendWithTopics(label, folder) { c ->
        if (c?.current != null) "Vysvetli mi stručne a jednoducho po slovensky aktuálnu tému z predmetu $folder: ${c.describe()}. " +
            "Pridaj 2 kľúčové pojmy v jazyku predmetu (${c.lang ?: "de"}) a jednu kontrolnú otázku."
        else "Z predmetu $folder nemám záznamy hodín. Povedz mi to otvorene a spýtaj sa, akú tému mám vysvetliť."
    }

    /**
     * A revision sheet for a test (round 19): from Schoolhelp's lesson topics and the test's note, what to
     * know, compact enough to read in ten minutes, in the subject's language with Slovak explanations.
     */
    fun revisionSheet(t: de.mm20.launcher2.schoolbrief.SchoolTask, due: String, label: String) {
        clear()
        sendWithTopics(label, t.subject) { c ->
            buildString {
                append("Vytvor mi opakovací list na test „${t.title}“ z predmetu ${t.subject.ifBlank { "?" }} ($due). ")
                if (c != null && c.describe().isNotBlank()) append("Učivo podľa záznamov hodín v Schoolhelp: ${c.describe()}. ")
                t.note?.takeIf { it.isNotBlank() }?.let { append("Poznámka k testu: $it. ") }
                append("Formát: nadpisy podľa tém; pod každou 3 až 5 bodov (pojmy, definície, vzorce, dátumy), ")
                append("odborné pojmy v jazyku predmetu (${c?.lang ?: "de"}) s krátkym slovenským vysvetlením; ")
                append("na konci 5 typických otázok na test bez odpovedí. Len to, čo naozaj pokrýva učivo; ")
                append("ak záznamy hodín chýbajú, napíš to na začiatku jednou vetou a drž sa poznámky k testu.")
            }
        }
    }

    /** A short quiz on the subject's latest lesson, one question at a time. */
    fun quizTopic(folder: String, label: String) = sendWithTopics(label, folder) { c ->
        if (c != null && c.describe().isNotBlank()) "Vyskúšaj ma z poslednej hodiny predmetu $folder: ${c.describe()}. " +
            "Päť krátkych otázok, vždy len jednu; po mojej odpovedi povedz, či je správna, a daj ďalšiu. Jazyk predmetu: ${c.lang ?: "de"}."
        else "Z predmetu $folder nemám záznamy hodín. Spýtaj sa ma jednou vetou, z čoho ma máš vyskúšať."
    }

    fun helpWithTask(t: de.mm20.launcher2.schoolbrief.SchoolTask, label: String) = sendWithTopics(label, t.subject) { c ->
        "Pomôž mi začať s úlohou „${t.title}“ (${t.subject}). " +
            (c?.describe()?.takeIf { it.isNotBlank() }?.let { "Súvisiace učivo: $it. " } ?: "") +
            "Rozdeľ ju na 3 až 4 malé kroky a povedz, čím začať. Úlohu za mňa neriešiť celú."
    }

    /** Schoolhelp's own fast answer to the teacher's last question in the running recording. */
    fun answerTeacher(label: String) {
        if (thinking) return
        messages += ChatMessage(true, label)
        thinking = true
        viewModelScope.launch {
            val r = brief.answerNow()
            messages += ChatMessage(false, r.getOrElse { "Nepodarilo sa (${it.message})." })
            thinking = false
            persist()
        }
    }

    /** Starts a new chat; the current one stays in the recent list. */
    fun clear() { persist(); messages.clear(); chatId = 0 }
}

@Composable
private fun Bubble(m: ChatMessage) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Text(
            m.text,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                m.fromUser -> MaterialTheme.colorScheme.onPrimaryContainer
                m.failed -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.widthIn(max = 520.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * The conversation as rows of the "Môj deň" list, under the tasks (round 30, user: "not a pop-up, the chat
 * goes underneath my tasks and I scroll down"). Nothing while there is no chat.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.chatConversation(vm: SchoolChatVM) {
    if (vm.messages.isEmpty() && !vm.thinking) return
    item(key = "chat-head", contentType = "chat-head") {
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 20.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chat_title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            // A new chat; this one stays under recent chats.
            androidx.compose.material3.TextButton(onClick = { vm.clear() }, enabled = !vm.thinking) { Text(stringResource(R.string.chat_new)) }
        }
    }
    items(vm.messages.size, key = { "chat$it" }, contentType = { "bubble" }) { i ->
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { Bubble(vm.messages[i]) }
    }
    if (vm.thinking) item(key = "chat-thinking", contentType = "bubble") {
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { Bubble(ChatMessage(false, "…")) }
    } else if (vm.messages.lastOrNull()?.failed == true) item(key = "chat-retry", contentType = "retry") {
        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { RetryButton(vm) }
    }
}

/** After a failed answer: ask again with one tap (round 31). */
@Composable
private fun RetryButton(vm: SchoolChatVM) {
    androidx.compose.material3.TextButton(onClick = { vm.retry() }) { Text(stringResource(R.string.chat_retry)) }
}

/** Recent chats, always at hand in "Opýtaj sa" (round 30, user: "the recent chats button has to be there constantly"). */
@Composable
fun ChatRecentButton(modifier: Modifier) {
    val vm: SchoolChatVM = viewModel(key = "school-chat")
    var show by remember { mutableStateOf(false) }
    if (show) RecentChatsSheet(vm, onDismiss = { show = false })
    val label = stringResource(R.string.chat_recent)
    Box(
        modifier
            .size(48.dp)
            .pillSurface(androidx.compose.foundation.shape.CircleShape)
            .clickable(role = Role.Button, onClickLabel = label) { show = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.history_24px), contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- Week ("Týždeň")

/** The week as what is due ([WeekOverview]); round 15 dropped the per-day timetable. */
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
                item { WeekOverview(f, now.toLocalDate()) }
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

// ---------------------------------------------------------------- Chat dock (side panel)

/** Which part of the chat to draw: all of it, or (on "Môj deň") the conversation above the mode switch and the bar below it. */
enum class ChatPart { All, Above, Bar }

/**
 * The chat docked at the bottom of the side panel. Collapsed it is one input line; after the first
 * question the conversation opens above it. Without a Gemini key it asks for one right here.
 * Bottom padding comes from the scaffold insets, which already include the keyboard (no imePadding:
 * doing both pushed the field off screen in the device test).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatDock(modifier: Modifier, bottomInset: androidx.compose.ui.unit.Dp, part: ChatPart = ChatPart.All) {
    val vm: SchoolChatVM = viewModel(key = "school-chat")
    val hasKey by vm.keyState.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size, vm.thinking) { if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size) }
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    var showRecent by remember { mutableStateOf(false) }
    if (showRecent) RecentChatsSheet(vm, onDismiss = { showRecent = false })

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        // ChatPart.All keeps its own frame (side panel); the parts get theirs from "Môj deň".
        val frame = if (part == ChatPart.All) modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 12.dp).padding(bottom = bottomInset + 8.dp)
        else modifier.fillMaxWidth()
        Column(frame) {
            // On "Môj deň" the conversation is part of the page (round 30, [chatConversation]); only the
            // side panel and the sheets keep the box.
            if (part == ChatPart.All && vm.messages.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.chat_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        if (vm.recent.isNotEmpty()) {
                            androidx.compose.material3.TextButton(onClick = { showRecent = true }) { Text(stringResource(R.string.chat_recent)) }
                        }
                        androidx.compose.material3.TextButton(onClick = { vm.clear() }) { Text(stringResource(R.string.chat_close)) }
                    }
                    // Round 22 (user: "bigger, fill the screen"): the conversation takes most of the page height.
                    val maxChat = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.62f).dp
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxChat), state = listState, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        items(vm.messages) { m -> Bubble(m) }
                        if (vm.thinking) item { Bubble(ChatMessage(false, "…")) }
                        else if (vm.messages.lastOrNull()?.failed == true) item { RetryButton(vm) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            // Round 30 (user: "the suggested questions stay there"): on "Môj deň" they stay during a chat too;
            // recent chats have their own button at the top there.
            if (part == ChatPart.Above && hasKey) QuickActions(vm, onRecent = null)
            if (part == ChatPart.All && hasKey && vm.messages.isEmpty()) QuickActions(vm, onRecent = { showRecent = true })
            if (part == ChatPart.Above) return@Column
            if (!hasKey) {
                KeyRow(onSave = { vm.saveKey(it) })
            } else {
                // Same bar as the dock and the task bar; dictation fills the field to edit or send.
                InputBar(
                    value = vm.draft, onValue = { vm.draft = it },
                    hint = stringResource(R.string.chat_hint),
                    submitIcon = R.drawable.arrow_forward_20px, submitLabel = stringResource(R.string.chat_send),
                    onSubmit = { vm.send(vm.draft); vm.draft = "" },
                    // Dictation fills the field while speaking; the user edits or sends it.
                    voice = rememberSpeechInput(onText = { vm.draft = it }, onPartial = { vm.draft = it }),
                )
            }
        }
    }
}

/**
 * One-tap questions above the chat field (device test: "more buttons"). Each sends a full prompt with the
 * Fach's lesson topics from Schoolhelp, so Gemini knows what is meant; the bubble shows only the label.
 */
@Composable
private fun QuickActions(vm: SchoolChatVM, onRecent: (() -> Unit)?) {
    val brief: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by brief.feed.collectAsState(null)
    val rec by brief.recording.collectAsState()
    val f = feed
    val now = LocalDateTime.now()
    val today = now.toLocalDate()
    val actions = mutableListOf<Pair<String, () -> Unit>>()

    if (rec != null) {
        val l = stringResource(R.string.quick_answer_teacher)
        actions += l to { vm.answerTeacher(l) }
    }
    val tomorrow = stringResource(R.string.quick_tomorrow)
    actions += tomorrow to { vm.send(tomorrow, "Čo mám zajtra? Prvá hodina, testy, čo odovzdať a čo si pribaliť. Stručne.") }
    if (f != null) {
        val rows = BriefLogic.visibleTasks(f.tasks, today)
        rows.filter { it.task.kind == de.mm20.launcher2.schoolbrief.SchoolTask.Test && !it.due.isBefore(today) }.minByOrNull { it.due }?.let { r ->
            // Round 19 (user): a revision sheet for the next test, first: the one most asked for.
            val due = dueLabel(r.due, today, null)
            val sheet = stringResource(R.string.quick_sheet, r.task.subject.ifBlank { r.task.title })
            actions.add(0, sheet to { vm.revisionSheet(r.task, due, sheet) })
            val l = stringResource(R.string.quick_practise, r.task.subject.ifBlank { r.task.title })
            actions += l to { vm.practiseTest(r.task, due, l) }
        }
        // Current or next lesson today, else tomorrow's first: "explain the topic".
        val lesson = lessonsOn(f, today).firstOrNull { it.end > now.toLocalTime().toString().take(5) } ?: lessonsOn(f, today.plusDays(1)).firstOrNull()
        lesson?.let { le ->
            val l = stringResource(R.string.quick_explain, le.subject)
            actions += l to { vm.explainTopic(le.subject, l) }
        }
        rows.firstOrNull { it.task.kind == de.mm20.launcher2.schoolbrief.SchoolTask.Homework || it.task.kind == de.mm20.launcher2.schoolbrief.SchoolTask.Project }?.let { r ->
            val l = stringResource(R.string.quick_help_task, r.task.subject.ifBlank { r.task.title.take(24) })
            actions += l to { vm.helpWithTask(r.task, l) }
        }
    }
    val week = stringResource(R.string.quick_week)
    actions += week to { vm.send(week, "Čo ma čaká tento týždeň? Testy, odovzdania a termíny podľa dní, stručne.") }
    // Round 16 (user: "a few more questions"): ones that help any day, data or not.
    val priority = stringResource(R.string.quick_priority)
    actions += priority to { vm.send(priority, "Čo je teraz najdôležitejšie spraviť? Jedna vec, prečo práve ona a prvý krok. Stručne.") }
    val plan = stringResource(R.string.quick_plan)
    actions += plan to { vm.send(plan, "Naplánuj mi zvyšok dňa: ktoré úlohy a učenie v akom poradí, s odhadom minút a krátkymi prestávkami. Stručne, ako zoznam.") }
    val bag = stringResource(R.string.quick_bag)
    actions += bag to { vm.send(bag, "Čo si mám zbaliť na zajtra podľa rozvrhu a úloh? Zoznam.") }
    if (f != null) {
        lessonsOn(f, today).lastOrNull { it.end <= now.toLocalTime().toString().take(5) }?.let { le ->
            val l = stringResource(R.string.quick_quiz, le.subject)
            actions += l to { vm.quizTopic(le.subject, l) }
        }
    }

    // Round 31: the same question in the Claude app (Max plan, Schoolhelp connector there).
    val context = androidx.compose.ui.platform.LocalContext.current
    actions.add(0, stringResource(R.string.chat_in_claude) to { vm.askClaude(context) })
    if (onRecent != null && vm.recent.isNotEmpty()) actions.add(0, stringResource(R.string.chat_recent) to onRecent)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((label, action) in actions) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .pillSurface(androidx.compose.foundation.shape.CircleShape)
                    .clickable(role = Role.Button, onClick = action)
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
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


/** Recent chats: the first question as the title, when it was; tap to open and continue. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RecentChatsSheet(vm: SchoolChatVM, onDismiss: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val fmt = DateTimeFormatter.ofPattern("EEEE d.\u00A0M., HH:mm", locale)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.chat_recent), style = SchoolFonts.serifDisplay(28))
            Spacer(Modifier.height(12.dp))
            if (vm.recent.isEmpty()) Secondary(stringResource(R.string.chat_recent_empty))
            for (c in vm.recent) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(SchoolTokens.ItemRadius))
                        .clickable(role = Role.Button) { vm.restore(c); onDismiss() }
                        .padding(vertical = 10.dp),
                ) {
                    Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Secondary(
                        java.time.Instant.ofEpochMilli(c.id).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                            .replaceFirstChar { it.titlecase(locale) } + " · " + (c.messages.size / 2).coerceAtLeast(1) + "×"
                    )
                }
            }
        }
    }
}
