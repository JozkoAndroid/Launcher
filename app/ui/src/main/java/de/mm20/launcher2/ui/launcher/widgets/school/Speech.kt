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
    val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** True from the mic tap until the stop tap: the recognizer is restarted after every pause. */
    var wanted = false

    /** What was heard in the earlier rounds of this listening. */
    var heard = ""

    /** Rounds in a row that heard nothing; listening ends after [MaxSilentRounds] (mic left on by mistake). */
    var silent = 0

    /** Errors in a row other than silence. */
    var errors = 0
}

/**
 * [onText] gets the text heard so far after each pause and at the stop, [onPartial] the text so far while
 * speaking (null: show nothing until a pause). Listens until the mic is tapped again (device test: it
 * stopped by itself after a pause): Android's recognizer ends at every pause, so it is started again.
 * Slovak first; if the recognizer has no Slovak, the phone's language.
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

    fun joined(next: String?) = listOf(holder.heard, next.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")

    fun end(status: String?) {
        holder.wanted = false
        holder.handler.removeCallbacksAndMessages(null)
        speech.listening = false
        speech.status = status
    }

    fun listen(language: String?) {
        val service = recognitionService(context)
        if (service == null) {
            end(sMissing)
            return
        }
        val r = holder.recognizer ?: SpeechRecognizer.createSpeechRecognizer(context, service).also { holder.recognizer = it }
        // The next round, a moment after the last one ended (an instant restart can find the recognizer busy).
        fun again(delayMs: Long = 150L) {
            holder.handler.removeCallbacksAndMessages(null)
            holder.handler.postDelayed({ if (holder.wanted) listen(language) }, delayMs)
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { speech.status = sListening }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(results: Bundle?) {
                results.first()?.let { partial?.invoke(joined(it)) }
            }

            override fun onResults(results: Bundle?) {
                val heard = results.first()
                if (heard != null) {
                    holder.heard = joined(heard)
                    holder.silent = 0
                    text(holder.heard)
                }
                holder.errors = 0
                if (holder.wanted) {
                    again()
                } else {
                    speech.listening = false
                    speech.status = if (holder.heard.isBlank()) sNothing else null
                }
            }

            override fun onError(error: Int) {
                if (!holder.wanted) {
                    // After a stop by the user: nothing to report.
                    if (error != SpeechRecognizer.ERROR_CLIENT && speech.listening) end(null)
                    return
                }
                when (error) {
                    // A pause with nothing said: keep listening.
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        holder.silent++
                        holder.errors = 0
                        if (holder.silent >= MaxSilentRounds) end(if (holder.heard.isBlank()) sNothing else null) else again()
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                        holder.errors++
                        if (holder.errors >= 4) end(sFailed) else again(400L)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> end(sDenied)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> end(sOffline)
                    // No Slovak on this recognizer: once more in the phone's language.
                    ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> if (language != null) listen(null) else end(sFailed)
                    else -> {
                        holder.errors++
                        if (holder.errors >= 3) end(sFailed) else again(400L)
                    }
                }
            }
        })
        speech.listening = true
        speech.status = sListening
        runCatching {
            r.startListening(
                recognizeIntent(context, language)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // A longer pause before a round ends (recognizers may ignore it; the restart covers that).
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                    .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            )
        }.onFailure { end(sFailed) }
    }

    fun start() {
        holder.wanted = true
        holder.heard = ""
        holder.silent = 0
        holder.errors = 0
        listen(Slovak)
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else speech.status = sDenied
    }

    speech.onToggle = {
        if (speech.listening) {
            // Stop: the recognizer still delivers what it heard last (onResults), then listening ends.
            holder.wanted = false
            holder.handler.removeCallbacksAndMessages(null)
            holder.recognizer?.stopListening()
            speech.listening = false
            speech.status = null
            if (holder.heard.isNotBlank()) text(holder.heard)
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
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
        onDispose {
            holder.wanted = false
            holder.handler.removeCallbacksAndMessages(null)
            holder.recognizer?.destroy(); holder.recognizer = null
        }
    }
    return speech
}

/** About a minute of silence (rounds of a few seconds each) ends listening left on by mistake. */
private const val MaxSilentRounds = 12

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
