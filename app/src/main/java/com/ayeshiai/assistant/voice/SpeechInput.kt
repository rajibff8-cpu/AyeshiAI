package com.ayeshiai.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Push-to-talk speech recognition using Android's SpeechRecognizer. Must be used on the main thread. */
class SpeechInput(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onFail: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null

    fun start(languageTag: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFail("Is phone par speech recognition service nahi mili. Google app / Speech Services install ya update karo.")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                onFail(describe(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onFail("Kuch samajh nahi aaya. Dobara bolo.") else onText(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer = r
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Kuch samajh nahi aaya. Dobara bolo."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch sunai nahi diya. Mic button dobara dabao."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition ke liye network chahiye (ya timeout hua)."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission nahi hai."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy hai, thodi der baad try karo."
        SpeechRecognizer.ERROR_AUDIO -> "Audio record karne mein problem aayi."
        SpeechRecognizer.ERROR_SERVER -> "Speech service ka server error."
        else -> "Speech recognition error (code $code)."
    }
}
