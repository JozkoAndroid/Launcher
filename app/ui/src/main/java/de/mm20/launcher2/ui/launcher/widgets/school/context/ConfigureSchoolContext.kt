package de.mm20.launcher2.ui.launcher.widgets.school.context

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import java.time.LocalDate

/** Context block settings: what each card needs (one action per missing permission) and the spec's values. */
@Composable
fun ColumnScope.ConfigureSchoolContext() {
    val vm: ContextVM = viewModel(key = "school-context")
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val notif by vm.hasNotificationAccess.collectAsState(false)
    val contacts by vm.hasContacts.collectAsState(false)

    // Usage access and Health Connect are granted in other screens: re-check whenever we come back.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumes++ } }
    val usage = remember(resumes) { vm.hasUsageAccess() }
    var steps by remember { mutableStateOf(false) }
    LaunchedEffect(resumes) { steps = vm.hasStepsPermission() }
    val hcAvailable = remember(resumes) { vm.healthConnectAvailable() }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { resumes++ }

    Text(stringResource(R.string.context_settings_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.context_settings_info),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    PermissionRow(stringResource(R.string.context_perm_music), notif) { activity?.let { vm.requestNotificationAccess(it) } }
    PermissionRow(stringResource(R.string.context_perm_usage), usage) { vm.openUsageAccess() }
    PermissionRow(stringResource(R.string.context_perm_contacts), contacts) { activity?.let { vm.requestContacts(it) } }
    if (hcAvailable) {
        PermissionRow(stringResource(R.string.context_perm_steps), steps) { hcLauncher.launch(setOf(ContextSources.StepsPermission)) }
    } else {
        Text(
            stringResource(R.string.context_perm_steps_missing),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }

    // Lunch source: the EduPage food app (detected by name unless chosen).
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.context_lunch_app), style = MaterialTheme.typography.titleMedium)
    val chosenLunch by vm.lunchPackage.collectAsState()
    val candidates = remember(resumes) { vm.lunchCandidates() }
    ChoiceRow(stringResource(R.string.context_lunch_auto), chosenLunch == null) { vm.setLunchPackage(null) }
    for ((pkg, label) in candidates) ChoiceRow(label, chosenLunch == pkg) { vm.setLunchPackage(pkg) }
    if (chosenLunch != null && candidates.none { it.first == chosenLunch }) {
        ChoiceRow(vm.appLabel(chosenLunch!!), true) {}
    }

    Spacer(Modifier.height(24.dp))
    val s by vm.settings.collectAsState()
    var plan by remember(s) { mutableStateOf(java.text.NumberFormat.getNumberInstance().format(s.planBytes / 1e9)) }
    var anchor by remember(s) { mutableStateOf(s.cycleAnchor.toString()) }
    var goal by remember(s) { mutableStateOf(s.stepsGoal.toString()) }
    val planOk = plan.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val anchorOk = runCatching { LocalDate.parse(anchor) }.getOrNull()
    val goalOk = goal.toLongOrNull()?.takeIf { it > 0 }
    OutlinedTextField(
        plan, { plan = it }, label = { Text(stringResource(R.string.context_plan_gb)) }, singleLine = true,
        isError = planOk == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        anchor, { anchor = it }, label = { Text(stringResource(R.string.context_cycle_start)) }, singleLine = true,
        isError = anchorOk == null,
        supportingText = { Text(stringResource(R.string.context_cycle_start_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        goal, { goal = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.context_steps_goal)) }, singleLine = true,
        isError = goalOk == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.Button(
        onClick = {
            vm.saveSettings(s.copy(planBytes = (planOk!! * 1e9).toLong(), cycleAnchor = anchorOk!!, stepsGoal = goalOk!!))
        },
        enabled = planOk != null && anchorOk != null && goalOk != null,
    ) { Text(stringResource(R.string.context_save)) }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Text(
                stringResource(R.string.context_perm_granted),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onGrant) { Text(stringResource(R.string.context_perm_grant)) }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
