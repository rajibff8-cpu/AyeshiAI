package com.ayeshiai.assistant.data

import android.content.Context
import org.json.JSONArray

enum class Lang(val label: String, val sttTag: String, val ttsTag: String, val hint: String) {
    HINGLISH(
        "Hinglish", "hi-IN", "en-IN",
        "Reply in Hinglish: Hindi written in English (Roman) letters, mixed naturally with simple English. Do not use Devanagari script."
    ),
    HINDI("Hindi", "hi-IN", "hi-IN", "Reply in Hindi using Devanagari script."),
    ENGLISH("English", "en-IN", "en-IN", "Reply in clear, simple English.")
}

enum class Style(val label: String, val hint: String) {
    SHORT("Short", "Keep answers short: 1-3 sentences unless the user asks for more."),
    BALANCED("Balanced", "Give clear, moderately detailed answers."),
    DETAILED("Detailed", "Give thorough, step-by-step answers when useful.")
}

enum class Accent(val label: String, val argb: Long) {
    CYAN("Cyan", 0xFF00E5FF),
    PURPLE("Purple", 0xFF7C4DFF),
    PINK("Pink", 0xFFFF4081),
    GREEN("Green", 0xFF00E676)
}

data class Prefs(
    val userName: String = "",
    val assistantName: String = "Ayeshi",
    val model: String = "gemini-flash-latest",
    val temperature: Float = 0.8f,
    val style: Style = Style.BALANCED,
    val language: Lang = Lang.HINGLISH,
    val ttsEnabled: Boolean = true,
    val speechRate: Float = 1.0f,
    val voiceName: String = "",
    val continuousMode: Boolean = false,
    val historyEnabled: Boolean = true,
    val memoryEnabled: Boolean = true,
    val aiGreeting: Boolean = true,
    val darkTheme: Boolean = true,
    val accent: Accent = Accent.CYAN,
    val youtubeMusicDefault: Boolean = false
)

private inline fun <reified E : Enum<E>> enumOf(name: String?, fallback: E): E =
    runCatching { if (name == null) fallback else enumValueOf<E>(name) }.getOrDefault(fallback)

class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("ayeshi_settings", Context.MODE_PRIVATE)

    fun load(): Prefs {
        val d = Prefs()
        return Prefs(
            userName = sp.getString("userName", d.userName) ?: d.userName,
            assistantName = (sp.getString("assistantName", d.assistantName) ?: d.assistantName)
                .ifBlank { d.assistantName },
            model = (sp.getString("model", d.model) ?: d.model).ifBlank { d.model },
            temperature = sp.getFloat("temperature", d.temperature),
            style = enumOf(sp.getString("style", null), d.style),
            language = enumOf(sp.getString("language", null), d.language),
            ttsEnabled = sp.getBoolean("ttsEnabled", d.ttsEnabled),
            speechRate = sp.getFloat("speechRate", d.speechRate),
            voiceName = sp.getString("voiceName", d.voiceName) ?: d.voiceName,
            continuousMode = sp.getBoolean("continuousMode", d.continuousMode),
            historyEnabled = sp.getBoolean("historyEnabled", d.historyEnabled),
            memoryEnabled = sp.getBoolean("memoryEnabled", d.memoryEnabled),
            aiGreeting = sp.getBoolean("aiGreeting", d.aiGreeting),
            darkTheme = sp.getBoolean("darkTheme", d.darkTheme),
            accent = enumOf(sp.getString("accent", null), d.accent),
            youtubeMusicDefault = sp.getBoolean("youtubeMusicDefault", d.youtubeMusicDefault)
        )
    }

    fun save(p: Prefs) {
        sp.edit()
            .putString("userName", p.userName)
            .putString("assistantName", p.assistantName)
            .putString("model", p.model)
            .putFloat("temperature", p.temperature)
            .putString("style", p.style.name)
            .putString("language", p.language.name)
            .putBoolean("ttsEnabled", p.ttsEnabled)
            .putFloat("speechRate", p.speechRate)
            .putString("voiceName", p.voiceName)
            .putBoolean("continuousMode", p.continuousMode)
            .putBoolean("historyEnabled", p.historyEnabled)
            .putBoolean("memoryEnabled", p.memoryEnabled)
            .putBoolean("aiGreeting", p.aiGreeting)
            .putBoolean("darkTheme", p.darkTheme)
            .putString("accent", p.accent.name)
            .putBoolean("youtubeMusicDefault", p.youtubeMusicDefault)
            .apply()
    }

    fun loadMemories(): List<String> {
        val raw = sp.getString("memories", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveMemories(list: List<String>) {
        sp.edit().putString("memories", JSONArray(list).toString()).apply()
    }

    fun clearAll() {
        sp.edit().clear().apply()
    }
}
