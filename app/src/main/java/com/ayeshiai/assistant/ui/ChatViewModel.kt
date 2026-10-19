package com.ayeshiai.assistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ayeshiai.assistant.commands.AppCatalog
import com.ayeshiai.assistant.commands.AppEntry
import com.ayeshiai.assistant.commands.AppLauncher
import com.ayeshiai.assistant.commands.Command
import com.ayeshiai.assistant.commands.CommandRouter
import com.ayeshiai.assistant.commands.LaunchResult
import com.ayeshiai.assistant.data.ChatMessage
import com.ayeshiai.assistant.data.ChatRepository
import com.ayeshiai.assistant.data.Conversation
import com.ayeshiai.assistant.data.Lang
import com.ayeshiai.assistant.data.Prefs
import com.ayeshiai.assistant.data.SecureKeyStore
import com.ayeshiai.assistant.data.SettingsStore
import com.ayeshiai.assistant.gemini.GeminiClient
import com.ayeshiai.assistant.gemini.GeminiResult
import com.ayeshiai.assistant.gemini.Persona
import com.ayeshiai.assistant.voice.SpeechInput
import com.ayeshiai.assistant.voice.SpeechOutput
import com.ayeshiai.assistant.voice.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class Screen { HOME, SETTINGS }
enum class Status { SETUP, ONLINE, LISTENING, THINKING, SPEAKING }

data class UiState(
    val prefs: Prefs = Prefs(),
    val hasKey: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val currentId: Long = System.currentTimeMillis(),
    val conversations: List<Conversation> = emptyList(),
    val memories: List<String> = emptyList(),
    val screen: Screen = Screen.HOME,
    val showHistory: Boolean = false,
    val listening: Boolean = false,
    val thinking: Boolean = false,
    val speaking: Boolean = false,
    val toast: String? = null,
    val models: List<String> = emptyList(),
    val voices: List<VoiceInfo> = emptyList(),
    val testing: Boolean = false,
    val testResult: String? = null
) {
    val status: Status
        get() = when {
            listening -> Status.LISTENING
            thinking -> Status.THINKING
            speaking -> Status.SPEAKING
            !hasKey -> Status.SETUP
            else -> Status.ONLINE
        }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val keyStore = SecureKeyStore(app)
    private val repo = ChatRepository(app)
    private val gemini = GeminiClient()
    private val launcher = AppLauncher(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var continuousSession = false
    private var idCounter = System.currentTimeMillis()

    private val tts = SpeechOutput(
        app,
        onSpeaking = { sp -> _state.update { it.copy(speaking = sp) } },
        onFinished = { viewModelScope.launch { maybeContinue() } }
    )
    private val stt = SpeechInput(
        app,
        onText = { text ->
            _state.update { it.copy(listening = false) }
            send(text)
        },
        onFail = { msg ->
            continuousSession = false
            _state.update { it.copy(listening = false, toast = msg) }
        }
    )

    private val prefs: Prefs get() = _state.value.prefs
    private val english: Boolean get() = prefs.language == Lang.ENGLISH

    init {
        val p = store.load()
        _state.value = UiState(
            prefs = p,
            hasKey = keyStore.hasKey(),
            memories = store.loadMemories(),
            conversations = if (p.historyEnabled) repo.all() else emptyList()
        )
        greet()
    }

    private fun nextId(): Long = ++idCounter

    // ---------------------------------------------------------------- navigation / UI state

    fun setScreen(s: Screen) = _state.update { it.copy(screen = s) }
    fun setShowHistory(v: Boolean) = _state.update { it.copy(showHistory = v) }
    fun toast(msg: String) = _state.update { it.copy(toast = msg) }
    fun clearToast() = _state.update { it.copy(toast = null) }

    fun updatePrefs(f: (Prefs) -> Prefs) {
        val np = f(_state.value.prefs)
        store.save(np)
        _state.update { it.copy(prefs = np) }
        if (!np.ttsEnabled) tts.stop()
    }

    // ---------------------------------------------------------------- chat

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _state.value.thinking) return
        tts.stop()
        appendMessage(ChatMessage(nextId(), "user", text))
        when (val cmd = CommandRouter.parse(text, prefs.assistantName)) {
            is Command.OpenApp -> handleOpen(cmd.appKey)
            is Command.YouTubeSearch -> handleYouTube(cmd)
            is Command.Remember -> handleRemember(cmd.text)
            Command.None -> askGemini()
        }
    }

    private fun appendMessage(m: ChatMessage) {
        _state.update { it.copy(messages = it.messages + m) }
        persist()
    }

    private fun persist() {
        val s = _state.value
        if (!s.prefs.historyEnabled || s.messages.none { it.role == "user" }) return
        val title = s.messages.first { it.role == "user" }.text.replace('\n', ' ').take(40)
        val conv = Conversation(s.currentId, title, System.currentTimeMillis(), s.messages)
        viewModelScope.launch(Dispatchers.IO) {
            repo.upsert(conv)
            val all = repo.all()
            _state.update { it.copy(conversations = all) }
        }
    }

    private fun reply(text: String) {
        appendMessage(ChatMessage(nextId(), "model", text))
        val p = prefs
        if (p.ttsEnabled) {
            val ok = tts.speak(text, p.language.ttsTag, p.speechRate, p.voiceName)
            if (!ok) {
                toast("Voice output ready nahi hai. Phone ka Text-to-speech engine check karo.")
                maybeContinue()
            }
        } else {
            maybeContinue()
        }
    }

    private fun showError(text: String) {
        continuousSession = false
        appendMessage(ChatMessage(nextId(), "model", text, isError = true))
    }

    private fun askGemini() {
        val key = keyStore.loadApiKey()
        if (key.isBlank()) {
            showError("Pehle Settings mein Gemini API key add karo.")
            return
        }
        _state.update { it.copy(thinking = true) }
        val s = _state.value
        val cid = s.currentId
        val history = s.messages.filter { !it.isError }.map { it.role to it.text }
        val system = Persona.build(s.prefs, s.memories)
        viewModelScope.launch {
            val r = gemini.generate(key, s.prefs.model, system, history, s.prefs.temperature)
            _state.update { it.copy(thinking = false) }
            if (_state.value.currentId != cid) return@launch
            when (r) {
                is GeminiResult.Ok -> reply(r.value)
                is GeminiResult.Err -> showError(r.error.message)
            }
        }
    }

    /** The greeting is generated by Gemini (not hardcoded). Not spoken automatically. */
    private fun greet() {
        val s = _state.value
        if (!s.prefs.aiGreeting || s.messages.isNotEmpty()) return
        val key = keyStore.loadApiKey()
        if (key.isBlank()) return
        val cid = s.currentId
        val system = Persona.build(s.prefs, s.memories) +
            "\nThe user just opened the app. Greet them warmly in 1-2 short sentences suited to the current time of day (${timeOfDay()}) and ask what you can help with. Do not claim to have done anything."
        viewModelScope.launch {
            val r = gemini.generate(key, s.prefs.model, system, listOf("user" to "(app opened)"), s.prefs.temperature)
            val now = _state.value
            if (now.currentId != cid || now.messages.isNotEmpty()) return@launch
            val msg = when (r) {
                is GeminiResult.Ok -> ChatMessage(nextId(), "model", r.value)
                is GeminiResult.Err -> ChatMessage(nextId(), "model", r.error.message, isError = true)
            }
            _state.update { it.copy(messages = listOf(msg)) }
        }
    }

    private fun timeOfDay(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "morning"
            h < 17 -> "afternoon"
            h < 21 -> "evening"
            else -> "night"
        }
    }

    // ---------------------------------------------------------------- local commands

    private fun handleOpen(key: String) {
        val label = AppCatalog.byKey(key)?.label ?: key
        val en = english
        when (launcher.open(key)) {
            LaunchResult.OPENED -> reply(if (en) "Sure! Opening $label." else "Bilkul! $label open kar rahi hoon.")
            LaunchResult.NOT_INSTALLED ->
                reply(if (en) "$label isn't installed on this phone." else "$label is phone mein installed nahi hai.")
            LaunchResult.FAILED ->
                reply(if (en) "I couldn't open $label." else "$label open nahi ho paaya.")
        }
    }

    private fun handleYouTube(cmd: Command.YouTubeSearch) {
        val music = cmd.music ?: prefs.youtubeMusicDefault
        val label = if (music) "YouTube Music" else "YouTube"
        val en = english
        if (cmd.query.isBlank()) {
            val key = if (music) "youtube_music" else "youtube"
            when (launcher.open(key)) {
                LaunchResult.OPENED -> reply(
                    if (en) "Opened $label. Tell me the song or artist name and I'll search it."
                    else "$label khol diya. Gaane ya artist ka naam batao, main search kar dungi."
                )
                LaunchResult.NOT_INSTALLED ->
                    reply(if (en) "$label isn't installed on this phone." else "$label is phone mein installed nahi hai.")
                LaunchResult.FAILED ->
                    reply(if (en) "I couldn't open $label." else "$label open nahi ho paaya.")
            }
            return
        }
        when (launcher.searchYouTube(cmd.query, music)) {
            LaunchResult.OPENED -> reply(
                if (en) "Opening $label search for \"${cmd.query}\". You'll need to tap the result yourself to play it."
                else "$label par \"${cmd.query}\" search khol rahi hoon. Play karne ke liye result par tumhe khud tap karna hoga."
            )
            else -> reply(
                if (en) "I couldn't open $label search." else "$label search open nahi ho paaya."
            )
        }
    }

    private fun handleRemember(text: String) {
        val en = english
        val t = text.trim().take(200)
        when {
            !prefs.memoryEnabled -> reply(
                if (en) "Memory is off. Turn it on in Settings > Privacy."
                else "Memory band hai. Settings > Privacy mein on karo."
            )
            looksSensitive(t) -> reply(
                if (en) "That looks sensitive (password, key, OTP...), so I won't save it."
                else "Ye sensitive lag raha hai (password, key, OTP...), isliye main ise save nahi karungi."
            )
            else -> {
                val list = _state.value.memories + t
                store.saveMemories(list)
                _state.update { it.copy(memories = list) }
                reply(
                    if (en) "Okay, I'll remember: \"$t\". You can view or delete it in Settings > Privacy."
                    else "Theek hai, yaad rakh liya: \"$t\". Settings > Privacy mein dekh ya delete kar sakte ho."
                )
            }
        }
    }

    private fun looksSensitive(t: String): Boolean = SENSITIVE.containsMatchIn(t.lowercase())

    // ---------------------------------------------------------------- voice

    fun onMicClicked() {
        val s = _state.value
        if (s.listening) {
            stopListening()
            return
        }
        if (s.thinking) return
        tts.stop()
        continuousSession = prefs.continuousMode
        startListening()
    }

    private fun startListening() {
        _state.update { it.copy(listening = true) }
        stt.start(prefs.language.sttTag)
    }

    fun stopListening() {
        continuousSession = false
        stt.stop()
        _state.update { it.copy(listening = false) }
    }

    fun stopSpeaking() {
        continuousSession = false
        tts.stop()
        _state.update { it.copy(speaking = false) }
    }

    private fun maybeContinue() {
        val s = _state.value
        if (continuousSession && !s.listening && !s.thinking) startListening()
    }

    fun refreshVoices() {
        val list = tts.voices().filter { it.locale.startsWith("en") || it.locale.startsWith("hi") }
        _state.update { it.copy(voices = list) }
        if (list.isEmpty()) toast("Koi voice nahi mili. TTS engine ready nahi hai ya voices install nahi hain.")
    }

    fun speakTest() {
        val p = prefs
        val ok = tts.speak("Hello, main Ayeshi hoon. Meri awaaz kaisi lag rahi hai?", p.language.ttsTag, p.speechRate, p.voiceName)
        if (!ok) toast("Voice output ready nahi hai.")
    }

    // ---------------------------------------------------------------- conversations

    fun newChat() {
        stopListening()
        tts.stop()
        _state.update { it.copy(messages = emptyList(), currentId = nextId(), showHistory = false, thinking = false) }
        greet()
    }

    fun loadConversation(id: Long) {
        val c = _state.value.conversations.firstOrNull { it.id == id } ?: return
        tts.stop()
        _state.update { it.copy(messages = c.messages, currentId = c.id, showHistory = false, thinking = false) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            val all = repo.all()
            _state.update { it.copy(conversations = all) }
        }
        if (_state.value.currentId == id) newChat()
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.clear()
            _state.update { it.copy(conversations = emptyList()) }
        }
        newChat()
        toast("Saari saved conversations delete ho gayi.")
    }

    fun clearAllData() {
        stopListening()
        tts.stop()
        repo.clear()
        store.clearAll()
        runCatching { keyStore.saveApiKey("") }
        _state.value = UiState(
            prefs = Prefs(),
            hasKey = false,
            screen = Screen.SETTINGS,
            toast = "Saara local data (chats, memories, settings, API key) delete ho gaya."
        )
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val json = repo.exportJson(store.loadMemories())
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
            toast(if (ok) "Export ho gaya (chats + memories, API key nahi)." else "Export fail ho gaya.")
        }
    }

    // ---------------------------------------------------------------- memory

    fun addMemory(text: String) {
        val t = text.trim().take(200)
        if (t.isEmpty()) return
        if (looksSensitive(t)) {
            toast("Sensitive info (password/key/OTP) memory mein save nahi hoti.")
            return
        }
        val list = _state.value.memories + t
        store.saveMemories(list)
        _state.update { it.copy(memories = list) }
    }

    fun editMemory(index: Int, text: String) {
        val t = text.trim().take(200)
        val cur = _state.value.memories
        if (index !in cur.indices || t.isEmpty()) return
        if (looksSensitive(t)) {
            toast("Sensitive info (password/key/OTP) memory mein save nahi hoti.")
            return
        }
        val list = cur.toMutableList().also { it[index] = t }
        store.saveMemories(list)
        _state.update { it.copy(memories = list) }
    }

    fun deleteMemory(index: Int) {
        val cur = _state.value.memories
        if (index !in cur.indices) return
        val list = cur.toMutableList().also { it.removeAt(index) }
        store.saveMemories(list)
        _state.update { it.copy(memories = list) }
    }

    fun clearMemories() {
        store.saveMemories(emptyList())
        _state.update { it.copy(memories = emptyList()) }
    }

    // ---------------------------------------------------------------- API key / models

    fun saveApiKey(key: String) {
        try {
            keyStore.saveApiKey(key)
        } catch (e: Exception) {
            toast("Keystore error: key secure save nahi ho paayi.")
            return
        }
        val has = keyStore.hasKey()
        _state.update { it.copy(hasKey = has, testResult = null) }
        toast(if (has) "API key encrypt karke save ho gayi." else "API key hata di gayi.")
        if (has) greet()
    }

    fun fetchModels() {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null) }
            val r = gemini.listModels(keyStore.loadApiKey())
            when (r) {
                is GeminiResult.Ok -> _state.update {
                    it.copy(testing = false, models = r.value, testResult = "${r.value.size} models mile. Neeche se chuno.")
                }
                is GeminiResult.Err -> _state.update { it.copy(testing = false, testResult = "❌ " + r.error.message) }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null) }
            val key = keyStore.loadApiKey()
            val model = prefs.model
            val list = gemini.listModels(key)
            val result: String = when (list) {
                is GeminiResult.Err -> "❌ " + list.error.message
                is GeminiResult.Ok -> {
                    _state.update { it.copy(models = list.value) }
                    val g = gemini.generate(key, model, "You are a connection test. Reply with the single word OK.", listOf("user" to "ping"), 0f)
                    when (g) {
                        is GeminiResult.Ok -> "✅ Connected. Model '$model' kaam kar raha hai."
                        is GeminiResult.Err -> "❌ " + g.error.message
                    }
                }
            }
            _state.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun installedApps(): List<Pair<AppEntry, Boolean>> =
        AppCatalog.entries.map { it to launcher.isInstalled(it.key) }

    override fun onCleared() {
        stt.stop()
        tts.shutdown()
        super.onCleared()
    }

    private companion object {
        val SENSITIVE = Regex(
            "(password|passcode|passwd|\\botp\\b|\\bpin\\b|api[ _-]?key|secret|token|aiza[0-9a-z_-]{10,}|\\bcvv\\b|aadhaar|aadhar|पासवर्ड)"
        )
    }
}
