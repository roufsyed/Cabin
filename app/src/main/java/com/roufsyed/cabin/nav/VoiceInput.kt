package com.roufsyed.cabin.nav

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Hears one spoken phrase, for "Where to?". Uses whatever speech recogniser the unit has. */
interface VoiceInput {
    /** False when the unit has no speech recogniser; "Where to?" then opens the maps app instead. */
    val available: Boolean

    /** Listens once. [onResult] gets what was heard, or null if nothing was understood. */
    fun listen(onResult: (String?) -> Unit)

    fun stop()
}

/** A voice that always "hears" the same thing: for the demo launch and for tests. */
class FixedVoice(private val heard: String? = null, override val available: Boolean = true) : VoiceInput {
    var listening = false
        private set
    private var pending: ((String?) -> Unit)? = null

    override fun listen(onResult: (String?) -> Unit) {
        listening = true
        pending = onResult
    }

    /** Test hook: finishes the current listen with [heard]. */
    fun finish() {
        listening = false
        pending?.invoke(heard)
        pending = null
    }

    override fun stop() {
        listening = false
        pending = null
    }
}

/**
 * Android's own speech recogniser. It exists only while listening, so nothing is held, and nothing
 * listens, between uses. Must be called on the main thread, which is where the screens call it from.
 */
class AndroidVoiceInput(private val context: Context) : VoiceInput {
    private var recognizer: SpeechRecognizer? = null

    override val available: Boolean
        get() = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: RuntimeException) {
            false
        }

    override fun listen(onResult: (String?) -> Unit) {
        stop()
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: RuntimeException) {
            onResult(null)
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish(heard?.takeIf { it.isNotBlank() })
            }

            override fun onError(error: Int) = finish(null)

            private fun finish(heard: String?) {
                if (recognizer !== created) return
                stop()
                onResult(heard)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        try {
            created.startListening(request)
        } catch (e: RuntimeException) {
            stop()
            onResult(null)
        }
    }

    override fun stop() {
        val current = recognizer ?: return
        recognizer = null
        try {
            current.cancel()
            current.destroy()
        } catch (e: RuntimeException) {
            // The recogniser's service has already gone; nothing left to release.
        }
    }
}
