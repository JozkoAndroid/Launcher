package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefStatus
import de.mm20.launcher2.schoolbrief.SyncDone
import de.mm20.launcher2.schoolbrief.SyncWorking
import de.mm20.launcher2.ui.R

/**
 * "Try again" that shows what it does (round 26, device test: the button seemed to do nothing). Sending,
 * sent, or the reason it failed; when the login itself is gone, the button reconnects instead.
 */
@Composable
internal fun SyncRow() {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val context = LocalContext.current
    val status by vm.status.collectAsState()
    val state by vm.syncState.collectAsState()
    Column {
        if (status is BriefStatus.NeedsLogin) {
            TextButton(onClick = { vm.login(context) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.school_reconnect)) }
            return@Column
        }
        TextButton(onClick = { vm.retryNow() }, enabled = state != SyncWorking, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(if (state == SyncWorking) R.string.school_sending else R.string.school_retry_now))
        }
        when (val s = state) {
            null, SyncWorking -> {}
            SyncDone -> Text(stringResource(R.string.school_sent), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text(s.take(160), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
