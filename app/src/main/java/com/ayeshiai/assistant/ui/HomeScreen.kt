package com.ayeshiai.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ayeshiai.assistant.data.ChatMessage
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(vm: ChatViewModel) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.onMicClicked()
        else vm.toast("Mic permission nahi mili, isliye voice input band hai. Text se chat kar sakte ho.")
    }
    val micGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val itemCount = s.messages.size + if (s.thinking) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AYESHI AI", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f))
            AyChip("🕘 History") { vm.setShowHistory(true) }
            Spacer(Modifier.width(8.dp))
            AyChip("⚙ Settings") { vm.setScreen(Screen.SETTINGS) }
        }

        ToastBanner(s.toast) { vm.clearToast() }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AiOrb(s.status, accent)
            Text(statusLabel(s.status), fontWeight = FontWeight.Medium, color = accent)
            Text(
                "🔒 Mic: ${if (micGranted) "allowed" else "not allowed"} • History: ${if (s.prefs.historyEnabled) "on" else "off"} • Memory: ${if (s.prefs.memoryEnabled) "on" else "off"}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (s.speaking) {
                Spacer(Modifier.height(4.dp))
                AyChip("🔇 Stop speaking") { vm.stopSpeaking() }
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!s.hasKey && s.messages.isEmpty()) {
                Card(
                    Modifier.align(Alignment.Center),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gemini API key add karo", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Chat shuru karne ke liye Settings mein apni Gemini API key save karo.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.setScreen(Screen.SETTINGS) }) { Text("Open Settings") }
                    }
                }
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.messages, key = { it.id }) { m -> Bubble(m) }
                    if (s.thinking) {
                        item {
                            Text(
                                "${s.prefs.assistantName} soch rahi hai…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AyChip("😄 Joke") { vm.send("Ek achha sa joke sunao") }
            AyChip("📚 Study") { vm.send("Mujhe study mein help chahiye. Kis topic se start karein?") }
            AyChip("💻 Code") { vm.send("Mujhe coding mein help chahiye. Main kya bana sakta hoon?") }
            AyChip("▶ YouTube") { vm.send("YouTube kholo") }
            AyChip("🧮 Calculator") { vm.send("Calculator kholo") }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("${s.prefs.assistantName} se baat karo…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    vm.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !s.thinking
            ) { Text("➤") }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MicButton(s.listening) {
                if (micGranted) vm.onMicClicked() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Text(
                if (s.listening) "Sun rahi hoon… (rokne ke liye tap karo)" else "Bolne ke liye tap karo",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (s.showHistory) HistoryDialog(vm, s)
}

private fun statusLabel(st: Status): String = when (st) {
    Status.SETUP -> "Setup needed – add API key"
    Status.ONLINE -> "Online"
    Status.LISTENING -> "Listening…"
    Status.THINKING -> "Thinking…"
    Status.SPEAKING -> "Speaking…"
}

@Composable
private fun Bubble(m: ChatMessage) {
    val c = MaterialTheme.colorScheme
    val isUser = m.role == "user"
    val bg = when {
        m.isError -> c.error.copy(alpha = 0.18f)
        isUser -> c.primary.copy(alpha = 0.22f)
        else -> c.surfaceVariant
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    m.text,
                    color = if (m.isError) c.error else c.onSurface,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun HistoryDialog(vm: ChatViewModel, s: UiState) {
    var q by remember { mutableStateOf("") }
    val list = s.conversations.filter { c ->
        q.isBlank() || c.title.contains(q, true) || c.messages.any { it.text.contains(q, true) }
    }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = { vm.setShowHistory(false) },
        title = { Text("Conversations") },
        text = {
            Column {
                if (!s.prefs.historyEnabled) {
                    Text(
                        "History saving abhi OFF hai (Settings > Privacy).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    placeholder = { Text("Search chats") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (list.isEmpty()) {
                    Text("Koi saved conversation nahi.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(list, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.loadConversation(c.id) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, maxLines = 1, fontWeight = FontWeight.Medium)
                                    Text(
                                        fmt.format(Date(c.updated)),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { vm.deleteConversation(c.id) }) { Text("🗑") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.newChat() }) { Text("➕ New chat") } },
        dismissButton = { TextButton(onClick = { vm.setShowHistory(false) }) { Text("Close") } }
    )
}
