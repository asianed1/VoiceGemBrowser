package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.VoiceGemScreen
import com.example.ui.VoiceGemViewModel
import com.example.ui.theme.GemDeepNavy
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceGemViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLaunchIntent(intent, isNewIntent = false)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GemDeepNavy
                ) {
                    VoiceGemScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent, isNewIntent = true)
    }

    private fun handleLaunchIntent(intent: Intent?, isNewIntent: Boolean) {
        if (intent == null) return
        val isVoiceGemDeepLink =
            intent.action == Intent.ACTION_VIEW &&
                intent.data?.scheme == "voicegem" &&
                intent.data?.host == "listen"
        val isLauncherIntent =
            intent.action == Intent.ACTION_MAIN &&
                intent.hasCategory(Intent.CATEGORY_LAUNCHER)

        if (isVoiceGemDeepLink) {
            viewModel.onAssistantWakeRequested(skipGreeting = true, isFromNewIntent = isNewIntent)
        } else if (isNewIntent && isLauncherIntent) {
            // Choice B: Re-opening via "Hey Google, open VoiceGemBrowser" or app icon wakes the 5s mic
            viewModel.onAssistantWakeRequested(skipGreeting = false, isFromNewIntent = true)
        }
    }
}
