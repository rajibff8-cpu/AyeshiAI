package com.ayeshiai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ayeshiai.assistant.ui.AyeshiTheme
import com.ayeshiai.assistant.ui.ChatViewModel
import com.ayeshiai.assistant.ui.HomeScreen
import com.ayeshiai.assistant.ui.Screen
import com.ayeshiai.assistant.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.state.collectAsState()
            val dark = s.prefs.darkTheme
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                this@MainActivity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            AyeshiTheme(dark, Color(s.prefs.accent.argb)) {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        when (s.screen) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    /** Never keep the microphone open while the app is in the background. */
    override fun onStop() {
        vm.stopListening()
        super.onStop()
    }
}
