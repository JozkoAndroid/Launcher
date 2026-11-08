package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.schoolbrief.BriefStatus
import de.mm20.launcher2.ui.R

/** Settings for the Brief block: Schoolhelp login and the Gemini key. */
@Composable
fun ColumnScope.ConfigureSchoolBrief() {
    val vm: SchoolBriefVM = viewModel(key = "school-brief")
    val status by vm.status.collectAsState()
    val hasKey by vm.hasGeminiKey.collectAsState()
    val context = LocalContext.current

    Text(stringResource(R.string.brief_settings_schoolhelp), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    when (status) {
        BriefStatus.NeedsLogin -> {
            Text(stringResource(R.string.school_login_needed), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.login(context) }) { Text(stringResource(R.string.school_login)) }
        }
        is BriefStatus.LoggingIn -> Text(stringResource(R.string.school_login_in_browser), style = MaterialTheme.typography.bodyLarge)
        else -> {
            Text(stringResource(R.string.brief_settings_logged_in), style = MaterialTheme.typography.bodyLarge)
            val waiting by vm.pendingChanges.collectAsState()
            if (waiting > 0) {
                Text(
                    androidx.compose.ui.res.pluralStringResource(R.plurals.pending_changes, waiting, waiting),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            (status as? BriefStatus.Error)?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.school_load_failed, it.message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Destructive action stays tertiary on the page (design rules).
            TextButton(onClick = { vm.logout() }) { Text(stringResource(R.string.brief_settings_logout)) }
        }
    }

    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.brief_settings_gemini), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.brief_settings_gemini_info),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (hasKey) {
        Text(stringResource(R.string.brief_settings_gemini_saved), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { vm.setGeminiKey(null) }) { Text(stringResource(R.string.brief_settings_gemini_remove)) }
    } else {
        var key by remember { mutableStateOf("") }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text(stringResource(R.string.brief_settings_gemini)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { vm.setGeminiKey(key) }, enabled = key.length >= 20) {
                Text(stringResource(R.string.brief_settings_gemini_save))
            }
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text(stringResource(R.string.brief_settings_gemini_get)) }
        }
    }
}
