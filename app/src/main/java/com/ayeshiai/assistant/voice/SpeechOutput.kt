package com.ayeshiai.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

data class VoiceInfo(val name: String, val locale: String, val needsNetwork: Boolean)

/** Android TextToSpeech wrapper. Callbacks may arrive on a background thread. */
class SpeechOutput(
    context: Context,
    private val onSpeaking: (Boolean) -> Unit,
    private val onFinished: () -> Unit
) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var currentId = ""
    private var counter = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeaking(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == currentId) {
                            onSpeaking(false)
                            onFinished()
                        }
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == currentId) onSpeaking(false)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        if (utteranceId == currentId) onSpeaking(false)
                    }
                })
            }
        }
    }

    fun isReady(): Boolean = ready

    fun speak(raw: String, langTag: String, rate: Float, voiceName: String): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        val text = clean(raw).take(TextToSpeech.getMaxSpeechInputLength() - 10)
        if (text.isBlank()) return false
        engine.setLanguage(Locale.forLanguageTag(langTag))
        if (voiceName.isNotBlank()) {
            val v = runCatching { engine.voices }.getOrNull()?.firstOrNull { it.name == voiceName }
            if (v != null) engine.setVoice(v)
        }
        engine.setSpeechRate(rate)
        currentId = "u${++counter}"
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, currentId) == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts?.stop()
    }

    fun voices(): List<VoiceInfo> {
        val set = runCatching { tts?.voices }.getOrNull() ?: return emptyList()
        return set.map { VoiceInfo(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired) }
            .sortedBy { it.locale + it.name }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun clean(s: String): String = s
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("https?://\\S+"), " link ")
        .replace(Regex("[*#`>]"), "")
        .replace(Regex("[\\p{So}\\p{Cs}]"), "")
}
