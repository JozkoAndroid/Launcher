package de.mm20.launcher2.ui.settings.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.launcher.ForkSetup
import de.mm20.launcher2.ui.launcher.widgets.school.ConfigureSchoolBrief
import de.mm20.launcher2.ui.launcher.widgets.school.context.ConfigureSchoolContext
import kotlinx.serialization.Serializable

@Serializable
data object SchoolSettingsRoute : NavKey

/**
 * Everything the school launcher needs in one place (device test: settings were spread over blocks and
 * screens): Schoolhelp and Gemini, every permission with its button, the "Práve teraz" values, the layout.
 * Block settings keep only their own look and link here.
 */
@Composable
fun SchoolSettingsScreen() {
    PreferenceScreen(title = stringResource(R.string.school_settings)) {
        item { Section { ConfigureSchoolBrief() } }
        item { Section { ConfigureSchoolContext() } }
        item { Section { LayoutReset() } }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceBright).padding(20.dp),
        content = content,
    )
}

/** Destructive, so a quiet text button and a confirmation (design rules). */
@Composable
private fun ColumnScope.LayoutReset() {
    val context = LocalContext.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.school_settings_layout), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.layout_reset_info), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.layout_reset)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.layout_reset_confirm)) },
            confirmButton = { TextButton(onClick = { ForkSetup.resetLayout(context); confirm = false }) { Text(stringResource(R.string.layout_reset_ok)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
