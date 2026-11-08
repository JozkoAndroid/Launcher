package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.SchoolTask
import de.mm20.launcher2.schoolbrief.StudyContext
import de.mm20.launcher2.ui.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Minutes of the revision session the sheet starts. */
internal const val RevisionMinutes = 20

/**
 * Revision sheet (round 15, from the test countdown tile): the test, when, and what it covers, from
 * Schoolhelp's note on the test and the latest lesson topics of the subject (stoff.read, loaded live; the
 * note alone when offline). One primary action: a 20 minute focus session on it (the blocked apps stay
 * closed). Practising with Gemini is the quiet second action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RevisionSheet(title: String, onDismiss: () -> Unit) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val chat: SchoolChatVM = viewModel(key = "school-chat")
    val feed by vm.feed.collectAsState(null)
    val test = feed?.tasks?.firstOrNull { it.kind == SchoolTask.Test && it.title == title && !it.done }
    val today = LocalDate.now()
    val due = test?.let { runCatching { LocalDate.parse(it.due) }.getOrNull() }
    var study by remember(test?.subject) { mutableStateOf<StudyContext?>(null) }
    var loading by remember(test?.subject) { mutableStateOf(true) }
    LaunchedEffect(test?.subject) {
        study = test?.subject?.takeIf { it.isNotBlank() }?.let { vm.studyContext(it) }
        loading = false
    }
    val start = rememberFocusStarter()
    var practising by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            if (test == null || due == null) {
                Text(title, style = SchoolFonts.serifDisplay(28))
                Spacer(Modifier.height(8.dp))
                Secondary(stringResource(R.string.revision_missing))
                return@Column
            }
            val days = ChronoUnit.DAYS.between(today, due)
            Text(test.title, style = SchoolFonts.serifDisplay(28))
            Spacer(Modifier.height(6.dp))
            Secondary(listOfNotNull(test.subject.takeIf { it.isNotBlank() }, dueLabel(due, today, null)).joinToString(" · "))
            Spacer(Modifier.height(20.dp))
            Text(
                when (days) {
                    0L -> stringResource(R.string.revision_today)
                    1L -> stringResource(R.string.revision_tomorrow)
                    else -> androidx.compose.ui.res.pluralStringResource(R.plurals.revision_days_n, days.toInt(), days.toInt())
                },
                style = SchoolFonts.display(40).merge(SchoolTokens.Tabular),
                color = MaterialTheme.colorScheme.primary,
            )

            // What it covers: the note first (Schoolhelp writes the topics there), then the lessons.
            val topics = topicsOf(test.note) + listOfNotNull(study?.current) + study?.points.orEmpty()
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.revision_topics), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            when {
                topics.isNotEmpty() -> topics.distinct().take(8).forEach { TopicLine(it) }
                loading -> Secondary(stringResource(R.string.revision_loading))
                else -> Secondary(stringResource(R.string.revision_no_topics))
            }
            study?.recent?.takeIf { it.isNotEmpty() }?.let { recent ->
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.revision_recent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                recent.take(4).forEach { TopicLine(it) }
            }

            Spacer(Modifier.height(28.dp))
            val focusText = stringResource(R.string.revision_focus_text, test.subject.ifBlank { test.title })
            Button(
                onClick = { start(null, focusText, RevisionMinutes); onDismiss() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.revision_start, RevisionMinutes)) }
            val label = stringResource(R.string.quick_practise, test.subject.ifBlank { test.title })
            val dueText = dueLabel(due, today, null)
            TextButton(
                onClick = { chat.practiseTest(test, dueText, label); practising = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.testprep_practice)) }
        }
    }
    if (practising) {
        ModalBottomSheet(onDismissRequest = { practising = false }, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ChatDock(Modifier.padding(bottom = 16.dp), bottomInset = 0.dp)
        }
    }
}

/** Schoolhelp's note split into topics (it lists them with ;, commas or new lines). */
internal fun topicsOf(note: String?): List<String> =
    note.orEmpty().split('\n', ';', '•').map { it.trim().trimStart('-', '–').trim() }.filter { it.length > 1 }

@Composable
private fun TopicLine(text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("·", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
