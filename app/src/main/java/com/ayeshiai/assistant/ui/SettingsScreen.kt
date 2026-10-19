package com.ayeshiai.assistant.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayeshiai.assistant.data.Accent
import com.ayeshiai.assistant.data.Lang
import com.ayeshiai.assistant.data.Style

@Composable
fun SettingsScreen(vm: ChatViewModel) {
    val s by vm.state.collectAsState()
    val p = s.prefs
    val ctx = LocalContext.current
    BackHandler { vm.setScreen(Screen.HOME) }

    var keyInput by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var newMemory by remember { mutableStateOf("") }
    val installed = remember { vm.installedApps() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.setScreen(Screen.HOME) }) { Text("← Back") }
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        ToastBanner(s.toast) { vm.clearToast() }

        Section("🧠 AI settings") {
            Text(if (s.hasKey) "API key: ✅ saved (encrypted on this device)" else "API key: ❌ not set")
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveApiKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("Save key") }
                OutlinedButton(onClick = { vm.saveApiKey("") }, enabled = s.hasKey) { Text("Remove key") }
            }
            Hint("Free key: aistudio.google.com/apikey")
            OutlinedTextField(
                value = p.model,
                onValueChange = { v -> vm.updatePrefs { it.copy(model = v.trim()) } },
                label = { Text("Model name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.fetchModels() }, enabled = s.hasKey && !s.testing) { Text("Fetch models") }
                Button(onClick = { vm.testConnection() }, enabled = s.hasKey && !s.testing) { Text("Test connection") }
                if (s.testing) CircularProgressIndicator(Modifier.width(24.dp))
            }
            s.testResult?.let { Text(it, fontSize = 13.sp) }
            if (s.models.isNotEmpty()) {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    s.models.forEach { m ->
                        Text(
                            (if (m == p.model) "✔ " else "   ") + m,
                            Modifier.fillMaxWidth().clickable { vm.updatePrefs { it.copy(model = m) } }.padding(vertical = 7.dp),
                            color = if (m == p.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Text("Creativity (temperature): ${"%.2f".format(p.temperature)}")
            Slider(
                value = p.temperature,
                onValueChange = { v -> vm.updatePrefs { it.copy(temperature = v) } },
                valueRange = 0f..1.5f
            )
            Text("Response style")
            Choice(Style.values().toList(), p.style, { it.label }) { v -> vm.updatePrefs { it.copy(style = v) } }
        }

        Section("🎙 Voice settings") {
            SwitchRow("Speak replies (text-to-speech)", null, p.ttsEnabled) { v -> vm.updatePrefs { it.copy(ttsEnabled = v) } }
            SwitchRow(
                "Continuous conversation",
                "Jawab ke baad mic apne aap dobara sunta hai. Rokne ke liye mic dabao. Background mein kabhi nahi sunta.",
                p.continuousMode
            ) { v -> vm.updatePrefs { it.copy(continuousMode = v) } }
            Text("Language")
            Choice(Lang.values().toList(), p.language, { it.label }) { v -> vm.updatePrefs { it.copy(language = v) } }
            Text("Speech speed: ${"%.1f".format(p.speechRate)}x")
            Slider(
                value = p.speechRate,
                onValueChange = { v -> vm.updatePrefs { it.copy(speechRate = v) } },
                valueRange = 0.5f..1.5f
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refreshVoices() }) { Text("Load voices") }
                OutlinedButton(onClick = { vm.speakTest() }) { Text("Test voice") }
            }
            if (s.voices.isNotEmpty()) {
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        (if (p.voiceName.isBlank()) "✔ " else "   ") + "Default voice for language",
                        Modifier.fillMaxWidth().clickable { vm.updatePrefs { it.copy(voiceName = "") } }.padding(vertical = 7.dp)
                    )
                    s.voices.forEach { v ->
                        Text(
                            (if (v.name == p.voiceName) "✔ " else "   ") + "${v.name} (${v.locale})" + if (v.needsNetwork) " ☁" else "",
                            Modifier.fillMaxWidth().clickable { vm.updatePrefs { it.copy(voiceName = v.name) } }.padding(vertical = 7.dp),
                            fontSize = 13.sp,
                            color = if (v.name == p.voiceName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            OutlinedButton(onClick = {
                try {
                    ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (e: Exception) {
                    vm.toast("Voice installer nahi mila. Android Settings > Text-to-speech check karo.")
                }
            }) { Text("Install more voices") }
            Hint("Android voice ka gender nahi batata. Voices ko Test voice se try karke apni pasand ki awaaz chuno. Har phone par voices alag hoti hain.")
        }

        Section("✨ Personalization") {
            OutlinedTextField(
                value = p.userName,
                onValueChange = { v -> vm.updatePrefs { it.copy(userName = v) } },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = p.assistantName,
                onValueChange = { v -> vm.updatePrefs { it.copy(assistantName = v) } },
                label = { Text("Assistant name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow("Dark theme", null, p.darkTheme) { v -> vm.updatePrefs { it.copy(darkTheme = v) } }
            Text("Accent color")
            Choice(Accent.values().toList(), p.accent, { it.label }) { v -> vm.updatePrefs { it.copy(accent = v) } }
            SwitchRow("AI greeting on start", "App khulte hi Gemini ek greeting generate karta hai (1 request).", p.aiGreeting) { v ->
                vm.updatePrefs { it.copy(aiGreeting = v) }
            }
        }

        Section("🔒 Privacy") {
            SwitchRow("Save conversation history", "Sirf is phone ki internal storage mein.", p.historyEnabled) { v ->
                vm.updatePrefs { it.copy(historyEnabled = v) }
            }
            SwitchRow("Use memory", "Saved memories Gemini ko context mein bheji jaati hain.", p.memoryEnabled) { v ->
                vm.updatePrefs { it.copy(memoryEnabled = v) }
            }
            Text("Saved memories", fontWeight = FontWeight.Medium)
            if (s.memories.isEmpty()) Hint("Koi memory nahi. Bolo: \"yaad rakho: mujhe Python pasand hai\" ya neeche add karo.")
            s.memories.forEachIndexed { i, m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("• $m", Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { editing = i to m }) { Text("Edit") }
                    TextButton(onClick = { vm.deleteMemory(i) }) { Text("Delete") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMemory,
                    onValueChange = { newMemory = it },
                    placeholder = { Text("Nayi memory") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.addMemory(newMemory); newMemory = "" }, enabled = newMemory.isNotBlank()) { Text("Add") }
            }
            if (s.memories.isNotEmpty()) OutlinedButton(onClick = { vm.clearMemories() }) { Text("Clear all memories") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.clearHistory() }) { Text("Clear chats") }
                OutlinedButton(onClick = { export.launch("ayeshi_export.json") }) { Text("Export chats") }
            }
            Button(onClick = { confirmClear = true }) { Text("Delete ALL local data") }
            Hint(
                "Gemini ko kya jaata hai: tumhare messages (last ~24 ka context), assistant ka system prompt, tumhara naam aur saved memories (agar on). " +
                    "Voice: audio Android ki speech service process karti hai; Gemini ko sirf text jaata hai."
            )
            Hint(
                "API key security: key Android Keystore se encrypt hoke save hoti hai aur log nahi hoti. Lekin rooted phone ya modified APK se key nikal sakti hai. " +
                    "Production ke liye backend proxy use karo jahan key server par rahe."
            )
        }

        Section("🔗 App integration") {
            Text("YouTube search default")
            Choice(listOf(false, true), p.youtubeMusicDefault, { if (it) "YouTube Music" else "YouTube" }) { v ->
                vm.updatePrefs { it.copy(youtubeMusicDefault = v) }
            }
            Text("Apps the assistant can open", fontWeight = FontWeight.Medium)
            installed.forEach { (app, ok) -> Text((if (ok) "✅ " else "❌ ") + app.label, fontSize = 13.sp) }
            Hint("Termux: sirf open kar sakta hai (upar list mein status dekho). Termux mein commands chalana abhi implement nahi hai, aur AI ke shell commands kabhi chup-chaap run nahi honge.")
            Hint("Permissions: Microphone (sirf mic button dabane par). Aur koi permission (contacts, location, SMS) nahi maangi jaati.")
        }

        Section("ℹ About") {
            Text("AYESHI AI v0.1.0 (stages 1-3)")
            Hint("Troubleshooting: 'API key invalid' = key dobara paste karo. 'Model unavailable' = Fetch models se doosra model chuno. Voice input na chale = Google app / Speech Services update karo. Hindi awaaz nahi = Install more voices.")
            Hint("Open-source libraries: AndroidX, Jetpack Compose, Material 3 (Apache 2.0), OkHttp (Apache 2.0), Kotlin coroutines (Apache 2.0).")
        }
        Spacer(Modifier.padding(16.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all local data?") },
            text = { Text("Chats, memories, settings aur API key hamesha ke liye delete ho jaayenge.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearAllData() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    editing?.let { (idx, text) ->
        EditMemoryDialog(
            initial = text,
            onSave = { vm.editMemory(idx, it); editing = null },
            onCancel = { editing = null }
        )
    }
}

@Composable
private fun EditMemoryDialog(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Edit memory") },
        text = { OutlinedTextField(value = t, onValueChange = { t = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(t) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> AyChip(label(o), o == selected) { onSelect(o) } }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Hint(sub)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
