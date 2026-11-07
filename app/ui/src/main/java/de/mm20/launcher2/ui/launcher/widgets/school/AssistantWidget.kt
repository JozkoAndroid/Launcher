package de.mm20.launcher2.ui.launcher.widgets.school

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.AssistantWidget

@Composable
fun AssistantWidget(widget: AssistantWidget) {
    val context = LocalContext.current
    AssistantButton(onClick = { AssistantLauncher.open(context) })
}

/** The home screen's single primary action: an "ink" button, near-white on dark wallpapers. */
@Composable
internal fun AssistantButton(onClick: () -> Unit) {
    SchoolBlockFrame {
        val content = LocalContentColor.current
        val on = if (content.luminance() > 0.5f) Color(0xFF15171A) else Color(0xFFF7F7F8)
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = on),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SchoolTokens.Inset)
                .heightIn(min = 52.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Text(stringResource(R.string.assistant_button), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

/**
 * Opens Gemini.
 * 1. System assistant overlay: the same internal call the system makes for a power-button long
 *    press (SearchManager.launchAssist). Not public API, so it can fail; then:
 * 2. the Gemini app, 3. gemini.google.com in the Gemini app, 4. in the browser.
 * Device test: the generic assist intent opened Google search, so it is not used.
 */
object AssistantLauncher {
    const val GeminiPackage = "com.google.android.apps.bard"
    const val GeminiUrl = "https://gemini.google.com/app"

    /** Asks the system to show the default assistant as an overlay. True if the call went through. */
    fun launchSystemAssist(context: Context): Boolean = runCatching {
        val sm = context.getSystemService(Context.SEARCH_SERVICE) as android.app.SearchManager
        val m = android.app.SearchManager::class.java.getMethod("launchAssist", android.os.Bundle::class.java)
        m.invoke(sm, android.os.Bundle())
        true
    }.getOrDefault(false)

    fun candidates(context: Context): List<Intent> {
        val pm = context.packageManager
        val out = mutableListOf<Intent>()
        pm.getLaunchIntentForPackage(GeminiPackage)?.let { out += it }
        out += Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GeminiUrl)).setPackage(GeminiPackage)
        out += Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GeminiUrl)).addCategory(Intent.CATEGORY_BROWSABLE)
        return out.filter { it.resolveActivity(pm) != null }.map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    fun open(context: Context) {
        if (launchSystemAssist(context)) return
        for (intent in candidates(context)) {
            try {
                context.startActivity(intent); return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(context, R.string.assistant_missing, Toast.LENGTH_LONG).show()
    }
}
