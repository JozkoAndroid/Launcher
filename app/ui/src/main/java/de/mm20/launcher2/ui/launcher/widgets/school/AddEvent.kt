package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.SchoolTask
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.ChipWidgetConfig
import java.time.LocalDate

/** The "+" tile (round 11): add a homework, test or appointment to Schoolhelp from the home screen. */
@Composable
fun AddTile(config: ChipWidgetConfig) {
    var open by rememberSaveable { mutableStateOf(false) }
    ChipBlock(
        listOf(
            ContextItem(
                "add", stringResource(R.string.add_title), R.drawable.add_24px, onClick = { open = true },
                value = stringResource(R.string.add_value), label = stringResource(R.string.add_label),
            )
        ),
        config,
    )
    if (open) AddEventSheet(onDismiss = { open = false })
}

/**
 * Kind, title, day and (optionally) subject; saved as a private Schoolhelp entry, nobody else is told.
 * Works while Schoolhelp is down: the entry waits on the phone. One primary action: Uložiť.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddEventSheet(onDismiss: () -> Unit) {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val feed by vm.feed.collectAsState(null)
    val context = LocalContext.current
    val today = LocalDate.now()
    var kind by rememberSaveable { mutableStateOf(SchoolTask.Homework) }
    var title by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var day by rememberSaveable { mutableIntStateOf(1) }
    // Any date (device test): an exact day from the calendar picker, else the quick choice above.
    var custom by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = custom?.let { LocalDate.ofEpochDay(it) } ?: today.plusDays(day.toLong())
    val subjects = feed?.timetable?.values?.flatten()?.map { it.subject }?.distinct()?.sorted().orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.add_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(16.dp))
            Segmented(
                listOf(
                    SchoolTask.Homework to stringResource(R.string.task_kind_hausaufgabe),
                    SchoolTask.Test to stringResource(R.string.task_kind_test),
                    SchoolTask.Appointment to stringResource(R.string.task_kind_termin),
                ),
                selected = kind, onSelect = { kind = it },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                title, { title = it.take(200) }, label = { Text(stringResource(R.string.add_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.add_when), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..6) {
                    FilterChip(selected = custom == null && day == i, onClick = { day = i; custom = null }, label = {
                        Text(dueLabel(today.plusDays(i.toLong()), today, null).replaceFirstChar { it.titlecase() })
                    })
                }
                val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                FilterChip(
                    selected = custom != null, onClick = { picking = true },
                    label = {
                        Text(custom?.let { LocalDate.ofEpochDay(it).format(java.time.format.DateTimeFormatter.ofPattern("EEE d. M. yyyy", locale)) }
                            ?: stringResource(R.string.add_other_date))
                    },
                )
            }
            if (subjects.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.add_subject), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in subjects) FilterChip(selected = subject == s, onClick = { subject = if (subject == s) null else s }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(20.dp))
            Row {
                Button(
                    onClick = { vm.addEvent(kind, title, date, subject); onDismiss() },
                    enabled = title.isNotBlank(),
                ) { Text(stringResource(R.string.add_save)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { Links.openSchoolhelp(context); onDismiss() }) { Text(stringResource(R.string.add_open_schoolhelp)) }
            }
            Spacer(Modifier.height(8.dp))
            Secondary(stringResource(R.string.add_hint))
        }
    }
    if (picking) {
        // The picker works in UTC midnight millis.
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { custom = it / 86_400_000L }
                    picking = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) } },
        ) { androidx.compose.material3.DatePicker(state = picker) }
    }
}
