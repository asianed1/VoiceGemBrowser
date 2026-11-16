package com.example.ui

import android.app.Application
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.repository.FreeKnowledgeFallbackRepository
import com.example.data.repository.GeminiApiException
import com.example.data.repository.GeminiRepository
import com.example.data.user.UserManager
import com.example.data.user.UserProfile
import com.example.media.BluetoothCarAudioManager
import com.example.media.CarAudioRoutingMode
import com.example.media.CarAudioStatus
import com.example.media.DefaultRadioStations
import com.example.media.PodcastPreferences
import com.example.media.RadioServiceBridge
import com.example.media.RadioStation
import com.example.media.RadioStreamManager
import com.example.media.VoiceAudioFocusManager
import com.example.notification.CapturedMessage
import com.example.notification.LocalMessageSynthesizer
import com.example.notification.NotificationQueueManager
import com.example.voice.SoundFxManager
import com.example.voice.VoiceGenderPreference
import com.example.voice.VoiceRecognizer
import com.example.voice.VoiceSpeaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

class VoiceGemViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GeminiRepository()
    private val fallbackKnowledge = FreeKnowledgeFallbackRepository()
    private val soundFx = SoundFxManager(application)
    private val userManager = UserManager(application)

    // Radio stream and audio focus
    val radioManager = RadioStreamManager(application)
    val isRadioPlaying: StateFlow<Boolean> = radioManager.isPlaying
    val isRadioLoading: StateFlow<Boolean> = radioManager.isLoading
    val currentRadioStation: StateFlow<RadioStation?> = radioManager.currentStation
    val isRadioDucked: StateFlow<Boolean> = radioManager.isDucked
    val radioStatusDetail: StateFlow<String?> = radioManager.streamStatusDetail

    // 6 Preferred Customizable Podcast Sources
    val podcastPreferences = PodcastPreferences(application)
    val preferredPodcasts: StateFlow<List<RadioStation>> = podcastPreferences.preferredPodcasts

    // Incoming notification queue
    val unreadMessagesCount: StateFlow<Int> = NotificationQueueManager.unreadCount
    val capturedMessages: StateFlow<List<CapturedMessage>> = NotificationQueueManager.messages

    fun clearCapturedMessages() {
        NotificationQueueManager.clearQueue()
    }

    private val audioFocusManager = VoiceAudioFocusManager(
        context = application,
        onDuckChange = { shouldDuck -> radioManager.duck(shouldDuck) },
        onPauseRequested = { isPermanent ->
            radioManager.pause()
            if (isPermanent) {
                _statusText.value = "Playback paused for another audio app."
            }
        },
        onResumeRequested = {
            radioManager.resume()
        }
    )

    // Bluetooth Car Stereo & Audio Routing Manager
    val bluetoothCarManager = BluetoothCarAudioManager(
        context = application,
        onBecomingNoisy = {
            audioFocusManager.onMediaManuallyPaused()
            radioManager.pause()
            voiceSpeaker.stop()
            _statusText.value = "Car audio disconnected. Playback paused."
        }
    )
    val carAudioStatus: StateFlow<CarAudioStatus> = bluetoothCarManager.status

    init {
        RadioServiceBridge.onTogglePlayPause = { toggleRadioPlayPause() }
        RadioServiceBridge.onStopPlayback = { stopRadio() }
    }

    val userProfile: StateFlow<UserProfile> = userManager.userProfile

    private val _showOnboarding = MutableStateFlow(!userManager.userProfile.value.isSignedIn)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _currentQuestion = MutableStateFlow("")
    val currentQuestion: StateFlow<String> = _currentQuestion.asStateFlow()

    private val _spokenAnswer = MutableStateFlow("")
    val spokenAnswer: StateFlow<String> = _spokenAnswer.asStateFlow()

    private val _statusText = MutableStateFlow("Starting VoiceGem…")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ChatMessage>> = _conversationHistory.asStateFlow()

    private val _speechRate = MutableStateFlow(1.05f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _pendingGoogleSearchQuery = MutableStateFlow<String?>(null)
    val pendingGoogleSearchQuery: StateFlow<String?> = _pendingGoogleSearchQuery.asStateFlow()

    private var hasGreeted = false
    private var preferDirectListenOnFirstStart = false
    private var hasDetectedSpeechInTurn = false
    private var listeningWatchdogJob: Job? = null
    private var geminiJob: Job? = null

    private val voiceSpeaker = VoiceSpeaker(application) {
        // When TTS is ready, if permission already granted and hasn't greeted yet
        if (_hasMicPermission.value && !hasGreeted && !_showOnboarding.value) {
            triggerStartupGreeting()
        }
    }

    private val _voiceGender = MutableStateFlow(voiceSpeaker.preferredGender)
    val voiceGender: StateFlow<VoiceGenderPreference> = _voiceGender.asStateFlow()

    private val voiceRecognizer = VoiceRecognizer(
        context = application,
        listener = object : VoiceRecognizer.Callback {
            override fun onReady() {
                _assistantState.value = AssistantState.LISTENING
                _statusText.value = "Listening (5s window)…"
            }

            override fun onSpeechStarted() {
                hasDetectedSpeechInTurn = true
                listeningWatchdogJob?.cancel()
                _statusText.value = "Listening…"
            }

            override fun onRmsChanged(rmsDb: Float) {
                _rmsLevel.value = rmsDb
            }

            override fun onPartialText(partial: String) {
                hasDetectedSpeechInTurn = true
                listeningWatchdogJob?.cancel()
                _liveTranscript.value = partial
            }

            override fun onFinalResult(transcription: String) {
                hasDetectedSpeechInTurn = true
                listeningWatchdogJob?.cancel()
                _rmsLevel.value = 0f
                _liveTranscript.value = ""
                processUserQuestion(transcription)
            }

            override fun onError(errorCode: Int, message: String) {
                listeningWatchdogJob?.cancel()
                _rmsLevel.value = 0f
                if (_assistantState.value == AssistantState.LISTENING) {
                    _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
                    _assistantState.value = AssistantState.IDLE
                    audioFocusManager.abandonVoiceFocus()
                }
            }
        }
    )

    fun onPermissionResult(granted: Boolean) {
        _hasMicPermission.value = granted
        if (granted && !hasGreeted && voiceSpeaker.isReady && !_showOnboarding.value) {
            triggerStartupGreeting()
        }
    }

    /**
     * Handles launching or waking VoiceGem via "Hey Google, open VoiceGem" (voicegem://listen)
     * or Choice B re-open ("Hey Google, open VoiceGemBrowser").
     */
    fun onAssistantWakeRequested(skipGreeting: Boolean, isFromNewIntent: Boolean) {
        if (!isFromNewIntent || !hasGreeted) {
            if (skipGreeting) {
                preferDirectListenOnFirstStart = true
            }
            if (hasGreeted && _hasMicPermission.value && !_showOnboarding.value) {
                startListeningSequence()
            }
            return
        }

        if (_showOnboarding.value || !_hasMicPermission.value) return
        if (_assistantState.value == AssistantState.LISTENING) return

        geminiJob?.cancel()
        geminiJob = null
        voiceSpeaker.stop()
        startListeningSequence()
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val result = userManager.signInWithGoogle(context)
            _showOnboarding.value = false
            if (result.isSuccess && !hasGreeted && _hasMicPermission.value && voiceSpeaker.isReady) {
                triggerStartupGreeting()
            }
        }
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
        if (!hasGreeted && _hasMicPermission.value && voiceSpeaker.isReady) {
            triggerStartupGreeting()
        }
    }

    fun setSubscriber(isSubscriber: Boolean) {
        userManager.setSubscriber(isSubscriber)
    }

    fun signOut() {
        userManager.signOut()
    }

    fun saveCustomApiKey(key: String) {
        userManager.setCustomApiKey(key)
    }

    fun setVoiceGender(gender: VoiceGenderPreference, speakConfirmation: Boolean = false) {
        val result = voiceSpeaker.setVoiceGender(gender)
        _voiceGender.value = gender
        if (speakConfirmation) {
            val confirmation = if (result.foundDistinctVoice) {
                "Switched to the ${gender.displayName} voice. How may I help you?"
            } else {
                "${gender.displayName} selected, though your device currently has only one English US voice installed in system Text to Speech."
            }
            _spokenAnswer.value = confirmation
            speakResponse(confirmation)
        }
    }

    /**
     * Startup instance only: plays startup greeting ("How may I help you today?")
     * then opens the mic once for up to 5 seconds before turning off.
     */
    fun triggerStartupGreeting() {
        if (hasGreeted) return
        hasGreeted = true

        if (preferDirectListenOnFirstStart) {
            preferDirectListenOnFirstStart = false
            startListeningSequence()
            return
        }

        viewModelScope.launch {
            delay(300)
            soundFx.playStartupBeep()
            delay(250)

            _assistantState.value = AssistantState.SPEAKING
            _statusText.value = "How may I help you today?"
            _spokenAnswer.value = "How may I help you today?"

            audioFocusManager.requestVoicePriorityFocus()
            radioManager.duck(true)

            voiceSpeaker.speak(
                text = "How may I help you today?",
                utteranceId = "startup_greeting",
                onStart = {
                    _assistantState.value = AssistantState.SPEAKING
                },
                onDone = {
                    viewModelScope.launch {
                        delay(200)
                        startListeningSequence()
                    }
                },
                onError = {
                    startListeningSequence()
                }
            )
        }
    }

    private fun startListeningSequence() {
        if (!_hasMicPermission.value) return
        listeningWatchdogJob?.cancel()
        hasDetectedSpeechInTurn = false

        voiceSpeaker.stop()
        audioFocusManager.requestVoicePriorityFocus()
        radioManager.duck(true)
        soundFx.playListeningCue()
        _assistantState.value = AssistantState.LISTENING
        _statusText.value = "Listening (5s window)…"
        _liveTranscript.value = ""
        voiceRecognizer.startListening()

        // Hard 5-second silence auto-off timer so the mic never stays open unintentionally
        listeningWatchdogJob = viewModelScope.launch {
            delay(LISTENING_SILENCE_TIMEOUT_MS)
            if (_assistantState.value == AssistantState.LISTENING && !hasDetectedSpeechInTurn) {
                voiceRecognizer.cancel()
                _rmsLevel.value = 0f
                _liveTranscript.value = ""
                _assistantState.value = AssistantState.IDLE
                _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
                audioFocusManager.abandonVoiceFocus()
            }
        }
    }

    fun onGemOrbClicked() {
        when (_assistantState.value) {
            AssistantState.LISTENING -> {
                listeningWatchdogJob?.cancel()
                voiceRecognizer.stopListening()
                if (_liveTranscript.value.isNotBlank()) {
                    processUserQuestion(_liveTranscript.value)
                } else {
                    voiceRecognizer.cancel()
                    _rmsLevel.value = 0f
                    _assistantState.value = AssistantState.IDLE
                    _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
                    audioFocusManager.abandonVoiceFocus()
                }
            }
            AssistantState.SPEAKING -> {
                voiceSpeaker.stop()
                startListeningSequence()
            }
            AssistantState.PROCESSING -> {
                geminiJob?.cancel()
                geminiJob = null
                _assistantState.value = AssistantState.IDLE
                _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
                audioFocusManager.abandonVoiceFocus()
            }
            AssistantState.IDLE, AssistantState.ERROR -> {
                startListeningSequence()
            }
        }
    }

    fun processUserQuestion(question: String) {
        if (question.isBlank()) return

        val normalized = question.trim().lowercase(Locale.US).replace(Regex("""[^a-z0-9\s]"""), "")

        // 0. Handle spoken confirmation for pending Visual Google Search handoff (Approach B)
        val pendingQuery = _pendingGoogleSearchQuery.value
        if (pendingQuery != null) {
            if (normalized == "open google" || normalized == "open google search" ||
                normalized == "google search" || normalized == "yes" ||
                normalized == "yes open google" || normalized == "go ahead" ||
                normalized == "continue"
            ) {
                openPendingGoogleSearch(getApplication())
                return
            } else {
                // Any new distinct query clears the old Google Search prompt
                _pendingGoogleSearchQuery.value = null
            }
        }

        // 1. 100% Local Spoken Exit & Termination commands
        if (LocalCommandParser.isExitCommand(normalized)) {
            endChat(spokenFarewell = true)
            return
        }

        // 2. 100% Local Verbal Voice Selector (American Male / Female)
        val requestedGender = LocalCommandParser.detectVoiceGenderCommand(normalized)
        if (requestedGender != null) {
            voiceRecognizer.stopListening()
            voiceSpeaker.stop()
            _currentQuestion.value = question
            setVoiceGender(requestedGender, speakConfirmation = true)
            return
        }

        // 3. 100% Local On-Device Incoming Messages & Notifications Digest (Zero Cloud Upload)
        if (LocalCommandParser.isMessageSummaryCommand(normalized)) {
            _currentQuestion.value = question
            summarizeIncomingMessages()
            return
        }

        // 4. 100% Local Radio & Podcast Stream Voice Commands
        if (LocalCommandParser.isStopAudioCommand(normalized)) {
            stopRadio()
            _currentQuestion.value = question
            val msg = "Audio playback stopped."
            _spokenAnswer.value = msg
            speakResponse(msg)
            return
        }

        val podcastSlots = podcastPreferences.preferredPodcasts.value
        val presets = DefaultRadioStations.PRESET_STATIONS
        val matchedStation = LocalCommandParser.matchPlayStationCommand(normalized, podcastSlots, presets)
        if (matchedStation != null) {
            _currentQuestion.value = question
            playRadioStation(matchedStation)
            val msg = "Streaming ${matchedStation.name}."
            _spokenAnswer.value = msg
            speakResponse(msg)
            return
        }

        // 5. 100% Local Bluetooth Car Stereo Status Commands
        if (normalized.contains("bluetooth") || normalized.contains("car stereo") || normalized.contains("car audio")) {
            _currentQuestion.value = question
            val status = carAudioStatus.value
            val response = if (status.isConnected) {
                "Your vehicle is connected to ${status.deviceName}. All spoken voice answers and radio streams will play through your car stereo speakers."
            } else {
                "No Bluetooth car stereo is currently connected. VoiceGem is playing through your phone speaker."
            }
            _spokenAnswer.value = response
            speakResponse(response)
            return
        }

        // 6. Explicit Visual Google Search request (Warn user aloud first that it exits the app)
        val explicitGoogleQuery = LocalCommandParser.extractExplicitGoogleSearch(question)
        if (explicitGoogleQuery != null) {
            voiceRecognizer.stopListening()
            voiceSpeaker.stop()
            _currentQuestion.value = question
            _pendingGoogleSearchQuery.value = explicitGoogleQuery
            val warning = "Warning: opening Google Search will exit VoiceGemBrowser and show visual results on your screen. Say open Google to proceed, or ask another question."
            _spokenAnswer.value = warning
            _statusText.value = "Confirm Google Search handoff…"
            speakResponse(warning)
            return
        }

        // 7. Open-ended Q&A -> Gemini API (with Fast-Fail Retry + Jitter) & Hybrid Fallback (Approach A -> Approach B)
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()

        _currentQuestion.value = question
        _assistantState.value = AssistantState.PROCESSING
        _statusText.value = "VoiceGem is thinking…"
        soundFx.playProcessingCue()

        val userMsg = ChatMessage(sender = MessageSender.USER, text = question)
        _conversationHistory.value = _conversationHistory.value + userMsg

        geminiJob?.cancel()
        geminiJob = viewModelScope.launch {
            val effectiveKey = userManager.getEffectiveApiKey()
            val result = repository.getConversationalAnswer(
                userPrompt = question,
                history = _conversationHistory.value,
                apiKeyOverride = effectiveKey
            )
            result.fold(
                onSuccess = { answer ->
                    userManager.recordQueryUsed()
                    _spokenAnswer.value = answer
                    val gemMsg = ChatMessage(sender = MessageSender.VOICE_GEM, text = answer)
                    _conversationHistory.value = _conversationHistory.value + gemMsg
                    speakResponse(answer)
                },
                onFailure = { error ->
                    val geminiErr = error as? GeminiApiException
                    val isOffline = geminiErr?.isNetworkError == true

                    if (!isOffline) {
                        // Approach A: Free spoken instant answer (DuckDuckGo / Wikipedia) stays inside the app
                        _statusText.value = "Checking instant spoken answers…"
                        val instantAnswer = fallbackKnowledge.getInstantSpokenAnswer(question)
                        if (instantAnswer != null) {
                            val spokenFallback = "From ${instantAnswer.source}: ${instantAnswer.spokenText}"
                            _spokenAnswer.value = spokenFallback
                            val gemMsg = ChatMessage(sender = MessageSender.VOICE_GEM, text = spokenFallback)
                            _conversationHistory.value = _conversationHistory.value + gemMsg
                            speakResponse(spokenFallback)
                            return@fold
                        }

                        // Approach B: No spoken instant answer -> warn user aloud before launching visual Google Search
                        soundFx.playErrorCue()
                        _pendingGoogleSearchQuery.value = question
                        val reasonPrefix = when {
                            geminiErr?.isDailyQuotaExhausted == true -> "Your daily Gemini quota is exhausted"
                            geminiErr?.isRateLimit == true -> "Gemini is temporarily busy due to rate limits"
                            else -> "Gemini is currently unavailable"
                        }
                        val warningMessage = "$reasonPrefix and no instant spoken answer was found. I can open Google Search, but please note that doing so will exit VoiceGemBrowser and show visual results on screen. Say open Google to continue, or ask another question."
                        _spokenAnswer.value = warningMessage
                        _statusText.value = "Gemini unavailable • Google Search ready"
                        speakResponse(warningMessage)
                    } else {
                        soundFx.playErrorCue()
                        val offlineMessage = "You appear to be offline, so I cannot reach Gemini or web answers right now. Your unread message digest and local commands are still available."
                        _errorMessage.value = offlineMessage
                        _spokenAnswer.value = offlineMessage
                        _assistantState.value = AssistantState.ERROR
                        _statusText.value = "Offline mode • Local features active"
                        voiceSpeaker.speak(
                            text = offlineMessage,
                            onDone = {
                                _assistantState.value = AssistantState.IDLE
                                audioFocusManager.abandonVoiceFocus()
                            }
                        )
                    }
                }
            )
        }
    }

    fun openPendingGoogleSearch(context: Context) {
        val query = _pendingGoogleSearchQuery.value ?: return
        _pendingGoogleSearchQuery.value = null
        listeningWatchdogJob?.cancel()
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()
        audioFocusManager.abandonVoiceFocus()
        _assistantState.value = AssistantState.IDLE
        _statusText.value = "Opened Google Search"

        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
        } catch (_: Exception) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=$encoded")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                _statusText.value = "No browser or Google Search app found."
            }
        }
    }

    fun dismissPendingGoogleSearch() {
        _pendingGoogleSearchQuery.value = null
    }

    private fun speakResponse(response: String) {
        listeningWatchdogJob?.cancel()
        audioFocusManager.requestVoicePriorityFocus()
        radioManager.duck(true)

        _assistantState.value = AssistantState.SPEAKING
        _statusText.value = "VoiceGem speaking…"

        voiceSpeaker.speak(
            text = response,
            utteranceId = "response_${System.currentTimeMillis()}",
            onStart = {
                _assistantState.value = AssistantState.SPEAKING
            },
            onDone = {
                viewModelScope.launch {
                    _assistantState.value = AssistantState.IDLE
                    _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
                    audioFocusManager.abandonVoiceFocus()
                }
            },
            onError = {
                _assistantState.value = AssistantState.IDLE
                _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
                audioFocusManager.abandonVoiceFocus()
            }
        )
    }

    /**
     * Strictly 100% On-Device Unread Message Synthesis.
     * Private messages from WhatsApp, Gmail, SMS, Telegram, Viber, Slack, and Discord
     * NEVER leave the Android device, even when an active Internet connection is present.
     */
    fun summarizeIncomingMessages() {
        listeningWatchdogJob?.cancel()
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()

        val messages = NotificationQueueManager.getUnreadMessages()
        val localDigest = LocalMessageSynthesizer.synthesizeBriefing(messages)

        if (messages.isNotEmpty()) {
            NotificationQueueManager.clearQueue()
        }

        soundFx.playProcessingCue()
        _spokenAnswer.value = localDigest
        _statusText.value = "Reading local on-device digest…"
        speakResponse(localDigest)
    }

    fun playRadioStation(station: RadioStation) {
        audioFocusManager.requestMediaPlaybackFocus()
        radioManager.playStation(station)
        _statusText.value = "Streaming ${station.name}"
    }

    fun toggleRadioPlayPause() {
        if (radioManager.isPlaying.value) {
            audioFocusManager.onMediaManuallyPaused()
            radioManager.pause()
        } else {
            audioFocusManager.requestMediaPlaybackFocus()
            radioManager.resume()
        }
    }

    fun stopRadio() {
        audioFocusManager.onMediaManuallyPaused()
        radioManager.stop()
    }

    fun updatePodcastSlot(
        slotNumber: Int,
        name: String,
        genre: String,
        streamUrl: String,
        iconEmoji: String
    ) {
        podcastPreferences.updatePodcastSlot(slotNumber, name, genre, streamUrl, iconEmoji)
    }

    fun resetPodcastSlot(slotNumber: Int) {
        podcastPreferences.resetSlotToDefault(slotNumber)
    }

    fun resetAllPodcastSlots() {
        podcastPreferences.resetAllToDefaults()
    }

    fun setCarAudioRoutingMode(mode: CarAudioRoutingMode) {
        bluetoothCarManager.setRoutingMode(mode)
    }

    fun setAutoPauseOnDisconnect(enabled: Boolean) {
        bluetoothCarManager.setAutoPauseOnDisconnect(enabled)
    }

    fun testCarStereoAudio() {
        voiceRecognizer.stopListening()
        val testMsg = if (carAudioStatus.value.isConnected) {
            "VoiceGem is connected to ${carAudioStatus.value.deviceName}. Audio is streaming directly to your vehicle stereo."
        } else {
            "VoiceGem is currently playing through your phone speaker. Connect your car stereo via Bluetooth to hear audio in your vehicle."
        }
        speakResponse(testMsg)
    }

    fun speakTextDirectly(text: String) {
        voiceRecognizer.stopListening()
        speakResponse(text)
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        voiceSpeaker.speechRate = rate
    }

    fun setPitch(pitchValue: Float) {
        _pitch.value = pitchValue
        voiceSpeaker.pitch = pitchValue
    }

    fun testVoice() {
        voiceSpeaker.speak("Hello! I am VoiceGem, speaking with the ${_voiceGender.value.displayName} voice.")
    }

    fun clearHistory() {
        _conversationHistory.value = emptyList()
        _currentQuestion.value = ""
        _spokenAnswer.value = ""
        _pendingGoogleSearchQuery.value = null
        _statusText.value = "History cleared. Tap to speak."
    }

    fun endChat(spokenFarewell: Boolean = true) {
        listeningWatchdogJob?.cancel()
        geminiJob?.cancel()
        geminiJob = null
        voiceRecognizer.cancel()
        voiceSpeaker.stop()

        _liveTranscript.value = ""
        _rmsLevel.value = 0f
        _pendingGoogleSearchQuery.value = null
        _assistantState.value = AssistantState.IDLE
        _statusText.value = "Mic off · Tap Speak, Gem, or Hey Google"
        soundFx.playShutdownCue()

        if (spokenFarewell) {
            audioFocusManager.requestVoicePriorityFocus()
            radioManager.duck(true)
            _assistantState.value = AssistantState.SPEAKING
            voiceSpeaker.speak(
                text = "Goodbye!",
                utteranceId = "farewell_${System.currentTimeMillis()}",
                onDone = {
                    _assistantState.value = AssistantState.IDLE
                    audioFocusManager.abandonVoiceFocus()
                },
                onError = {
                    _assistantState.value = AssistantState.IDLE
                    audioFocusManager.abandonVoiceFocus()
                }
            )
        } else {
            audioFocusManager.abandonVoiceFocus()
        }
    }

    fun exitApplication(activity: android.app.Activity?) {
        listeningWatchdogJob?.cancel()
        geminiJob?.cancel()
        voiceRecognizer.destroy()
        voiceSpeaker.shutdown()
        radioManager.release()
        audioFocusManager.abandonAll()
        bluetoothCarManager.release()
        soundFx.release()
        activity?.finish()
    }

    fun interruptSpeech() {
        voiceSpeaker.stop()
        startListeningSequence()
    }

    override fun onCleared() {
        super.onCleared()
        listeningWatchdogJob?.cancel()
        geminiJob?.cancel()
        RadioServiceBridge.onTogglePlayPause = null
        RadioServiceBridge.onStopPlayback = null
        voiceRecognizer.destroy()
        voiceSpeaker.shutdown()
        radioManager.release()
        audioFocusManager.abandonAll()
        bluetoothCarManager.release()
        soundFx.release()
    }

    companion object {
        const val LISTENING_SILENCE_TIMEOUT_MS = 5_000L
    }
}

/**
 * 100% On-Device Natural Voice Command Parser.
 * Handles exit phrases, voice gender switches, notification digests, and podcast/radio playback
 * strictly locally without needing Gemini.
 */
object LocalCommandParser {

    private val exitPhrases = setOf(
        "exit", "quit", "stop", "bye", "goodbye", "end chat", "stop chat", "terminate",
        "close", "close app", "exit chat", "terminate chat", "stop talking", "good bye",
        "go to sleep", "sleep"
    )

    fun isExitCommand(normalized: String): Boolean {
        return exitPhrases.contains(normalized) ||
            normalized.startsWith("exit ") ||
            normalized.startsWith("quit ") ||
            normalized.startsWith("stop talking")
    }

    fun detectVoiceGenderCommand(normalized: String): VoiceGenderPreference? {
        val hasSwitchVerb = normalized.contains("switch") ||
            normalized.contains("change") ||
            normalized.contains("use") ||
            normalized.contains("set") ||
            normalized.contains("select")

        val isMaleTarget = normalized.contains("male voice") ||
            normalized.contains("man voice") ||
            normalized.contains("mans voice") ||
            normalized.contains("guy voice") ||
            normalized.contains("american male")

        val isFemaleTarget = normalized.contains("female voice") ||
            normalized.contains("woman voice") ||
            normalized.contains("womans voice") ||
            normalized.contains("lady voice") ||
            normalized.contains("american female")

        if (isFemaleTarget && (hasSwitchVerb || normalized == "female voice")) {
            return VoiceGenderPreference.FEMALE
        }
        if (isMaleTarget && (hasSwitchVerb || normalized == "male voice")) {
            return VoiceGenderPreference.MALE
        }
        return null
    }

    fun isMessageSummaryCommand(normalized: String): Boolean {
        val targetWords = listOf(
            "message", "messages", "notification", "notifications",
            "updates", "unread", "texts", "text messages", "emails", "inbox"
        )
        val actionWords = listOf(
            "read", "summarize", "summary", "check", "any", "what",
            "get", "my", "hear", "listen", "tell", "digest"
        )
        val exactShortcuts = setOf("messages", "unread", "notifications", "inbox", "read texts", "summarize messages")
        if (exactShortcuts.contains(normalized)) return true

        val hasTarget = targetWords.any { normalized.contains(it) }
        val hasAction = actionWords.any { normalized.contains(it) }
        return hasTarget && hasAction
    }

    fun isStopAudioCommand(normalized: String): Boolean {
        return normalized.contains("stop radio") ||
            normalized.contains("pause radio") ||
            normalized.contains("turn off radio") ||
            normalized.contains("stop podcast") ||
            normalized.contains("pause podcast") ||
            normalized.contains("turn off podcast") ||
            normalized.contains("stop music") ||
            normalized.contains("stop audio")
    }

    private val genericAudioWords = setOf(
        "podcast", "podcasts", "stream", "radio", "news", "music",
        "audio", "custom", "daily", "world", "global", "slot", "wire"
    )

    fun matchPlayStationCommand(
        normalized: String,
        podcastSlots: List<RadioStation>,
        presets: List<RadioStation>
    ): RadioStation? {
        val playPrefixes = listOf("play ", "listen to ", "stream ", "put on ", "turn on ")
        val isPlayIntent = playPrefixes.any { normalized.startsWith(it) }
        if (!isPlayIntent) return null

        // 1. Explicit preset stations and numbered slots always take priority
        val explicitMatch = when {
            normalized.contains("npr") -> presets.firstOrNull { it.id == "npr_live" }
            normalized.contains("bbc") -> presets.firstOrNull { it.id == "bbc_world" }
            normalized.contains("jazz") -> presets.firstOrNull { it.id == "smooth_jazz" }
            normalized.contains("ambient") || normalized.contains("groove") -> presets.firstOrNull { it.id == "groove_salad" }
            normalized.contains("slot 1") || normalized.contains("podcast 1") || normalized.contains("first podcast") -> podcastSlots.getOrNull(0)
            normalized.contains("slot 2") || normalized.contains("podcast 2") || normalized.contains("second podcast") -> podcastSlots.getOrNull(1)
            normalized.contains("slot 3") || normalized.contains("podcast 3") || normalized.contains("third podcast") -> podcastSlots.getOrNull(2)
            normalized.contains("slot 4") || normalized.contains("podcast 4") || normalized.contains("fourth podcast") -> podcastSlots.getOrNull(3)
            normalized.contains("slot 5") || normalized.contains("podcast 5") || normalized.contains("fifth podcast") -> podcastSlots.getOrNull(4)
            normalized.contains("slot 6") || normalized.contains("podcast 6") || normalized.contains("sixth podcast") -> podcastSlots.getOrNull(5)
            else -> null
        }
        if (explicitMatch != null) return explicitMatch

        // 2. Match distinctive words from user's customized podcast slot titles (excluding generic words)
        val customMatch = podcastSlots.firstOrNull { slot ->
            val words = slot.name.lowercase(Locale.US)
                .replace(Regex("""[^a-z0-9\s]"""), "")
                .split(" ")
                .filter { it.length >= 4 && !genericAudioWords.contains(it) }
            words.any { word -> normalized.contains(word) }
        }
        if (customMatch != null) return customMatch

        // 3. General fallback for "play podcast" / "play radio"
        val isAudioKeyword = normalized.contains("radio") ||
            normalized.contains("news") ||
            normalized.contains("music") ||
            normalized.contains("podcast") ||
            normalized.contains("slot")
        if (!isAudioKeyword) return null

        return if (normalized.contains("podcast")) {
            podcastSlots.getOrNull(0)
        } else {
            presets.firstOrNull { it.id == "npr_live" }
        }
    }

    fun extractExplicitGoogleSearch(rawQuery: String): String? {
        val lower = rawQuery.trim().lowercase(Locale.US)
        val prefixes = listOf("google search for ", "google search ", "search google for ", "search google ")
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val remainder = rawQuery.trim().substring(prefix.length).trim()
                if (remainder.isNotBlank()) return remainder
            }
        }
        return null
    }
}
