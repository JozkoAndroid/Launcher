package de.mm20.launcher2.ui.launcher.widgets.school

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay

/**
 * Voice input inside the launcher (round 15). The launcher listens itself: what is heard appears in the
 * field while speaking, and the field's hint says "Počúvam…" or what went wrong. Never the system speech
 * screen. (The device report "the mic opens settings" had another cause: the invisible search pill sat
 * over this bar and took the tap; see LauncherScaffold.searchBarAnimation.)
 */
@Stable
class SpeechInput internal constructor() {
    var listening by mutableStateOf(false)
        internal set

    /** Shown in place of the field's hint: listening, or why nothing was heard. Null = the normal hint. */
    var status by mutableStateOf<String?>(null)
        internal set

    internal var onToggle: () -> Unit = {}

    /** The mic button: start listening, or stop and keep what was heard so far. */
    fun toggle() = onToggle()
}

private class RecognizerHolder {
    var recognizer: SpeechRecognizer? = null
}

/**
 * [onText] gets the final text, [onPartial] the text so far while speaking (null: show nothing until the
 * end). Slovak first; if the recognizer has no Slovak, the phone's language.
 */
@Composable
internal fun rememberSpeechInput(onText: (String) -> Unit, onPartial: ((String) -> Unit)? = null): SpeechInput {
    val context = LocalContext.current
    val speech = remember { SpeechInput() }
    val holder = remember { RecognizerHolder() }
    val text by rememberUpdatedState(onText)
    val partial by rememberUpdatedState(onPartial)

    val sListening = stringResource(R.string.voice_listening)
    val sNothing = stringResource(R.string.voice_nothing_heard)
    val sDenied = stringResource(R.string.voice_mic_denied)
    val sOffline = stringResource(R.string.voice_offline)
    val sFailed = stringResource(R.string.voice_failed)
    val sMissing = stringResource(R.string.voice_unavailable)

    fun listen(language: String?) {
        val service = recognitionService(context)
        if (service == null) {
            speech.status = sMissing
            return
        }
        val r = holder.recognizer ?: SpeechRecognizer.createSpeechRecognizer(context, service).also { holder.recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { speech.status = sListening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(results: Bundle?) {
                results.first()?.let { partial?.invoke(it) }
            }

            override fun onResults(results: Bundle?) {
                speech.listening = false
                val heard = results.first()
                speech.status = if (heard == null) sNothing else null
                heard?.let { text(it) }
            }

            override fun onError(error: Int) {
                speech.listening = false
                speech.status = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> sNothing
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> sDenied
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> sOffline
                    // No Slovak on this recognizer: once more in the phone's language.
                    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> if (language != null) {
                        listen(null); return
                    } else sFailed
                    // After a stop by the user: nothing to report.
                    SpeechRecognizer.ERROR_CLIENT -> null
                    else -> sFailed
                }
            }
        })
        speech.listening = true
        speech.status = sListening
        r.startListening(recognizeIntent(context, language).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen(Slovak) else speech.status = sDenied
    }

    speech.onToggle = {
        if (speech.listening) {
            holder.recognizer?.stopListening()
            speech.listening = false
            speech.status = null
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen(Slovak)
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // A message stays a few seconds, then the normal hint returns.
    val status = speech.status
    LaunchedEffect(status, speech.listening) {
        if (status != null && !speech.listening) {
            delay(4000)
            speech.status = null
        }
    }
    DisposableEffect(Unit) {
        onDispose { holder.recognizer?.destroy(); holder.recognizer = null }
    }
    return speech
}

private const val Slovak = "sk-SK"

/**
 * The speech service to use: Google's first (it knows Slovak; Samsung phones often default to Samsung's
 * own, which may not), then the phone's default, then any. Null if the phone has none.
 */
private fun recognitionService(context: Context): ComponentName? {
    val services = context.packageManager
        .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        .map { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
    if (services.isEmpty()) return null
    val default = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        ?.let(ComponentName::unflattenFromString)
    return services.firstOrNull { it.packageName == "com.google.android.googlequicksearchbox" }
        ?: services.firstOrNull { it.packageName == "com.google.android.tts" }
        ?: services.firstOrNull { it == default }
        ?: services.first()
}

// SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31); plain values so older
// phones simply never see them.
private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
private const val ERROR_LANGUAGE_UNAVAILABLE = 13

private fun recognizeIntent(context: Context, language: String?) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    .apply { if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language) }

private fun Bundle?.first(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
