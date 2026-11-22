package com.example.ui

import android.app.Application
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
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
import com.example.media.PodcastEpisode
import com.example.media.PodcastPreferences
import com.example.media.RadioServiceBridge
import com.example.media.RadioStation
import com.example.media.RadioStreamManager
import com.example.media.VoiceAudioFocusManager
import com.example.messaging.ComposeStage
import com.example.messaging.ConfirmationAction
import com.example.messaging.LocalMessagingParser
import com.example.messaging.MessagingChannel
import com.example.messaging.MessagingDraft
import com.example.messaging.MessagingIntentLauncher
import com.example.messaging.MessagingMode
import com.example.messaging.NewMessageParseResult
import com.example.messaging.QuickContact
import com.example.messaging.QuickContactsRepository
import com.example.messaging.ReplyCommandParseResult
import com.example.notification.CapturedMessage
import com.example.notification.DirectReplyOutcome
import com.example.notification.LocalMessageSynthesizer
import com.example.notification.NotificationQueueManager
import com.example.notification.NotificationReplyStore
import com.example.notification.SpamFilterPreferences
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

    // 8 Preferred Customizable Podcast Sources & Resume State
    val podcastPreferences = PodcastPreferences(application)
    val preferredPodcasts: StateFlow<List<RadioStation>> = podcastPreferences.preferredPodcasts

    // Radio stream and audio focus
    val radioManager = RadioStreamManager(application, podcastPreferences)
    val isRadioPlaying: StateFlow<Boolean> = radioManager.isPlaying
    val isRadioLoading: StateFlow<Boolean> = radioManager.isLoading
    val currentRadioStation: StateFlow<RadioStation?> = radioManager.currentStation
    val isRadioDucked: StateFlow<Boolean> = radioManager.isDucked
    val radioStatusDetail: StateFlow<String?> = radioManager.streamStatusDetail
    val podcastEpisodes: StateFlow<List<PodcastEpisode>> = radioManager.episodes
    val currentEpisodeIndex: StateFlow<Int> = radioManager.currentEpisodeIndex
    val currentEpisodeTitle: StateFlow<String?> = radioManager.currentEpisodeTitle
    val currentPositionMs: StateFlow<Long> = radioManager.currentPositionMs
    val durationMs: StateFlow<Long> = radioManager.durationMs
    val sleepTimerRemainingSec: StateFlow<Int?> = radioManager.sleepTimerRemainingSec
    val sleepAtEndOfEpisode: StateFlow<Boolean> = radioManager.sleepAtEndOfEpisode

    // Incoming notification queue & on-device spam/OTP protection
    private val spamFilterPrefs = SpamFilterPreferences(application)
    val unreadMessagesCount: StateFlow<Int> = NotificationQueueManager.unreadCount
    val capturedMessages: StateFlow<List<CapturedMessage>> = NotificationQueueManager.messages
    val filteredMessages: StateFlow<List<CapturedMessage>> = NotificationQueueManager.filteredMessages
    val spamFilterEnabled: StateFlow<Boolean> = spamFilterPrefs.spamFilterEnabled
    val mutedSenders: StateFlow<Set<String>> = spamFilterPrefs.mutedSenders

    // Zero-Gemini Voice Reply & New Message Composer state
    private val quickContactsRepo = QuickContactsRepository(application)
    val quickContacts: StateFlow<List<QuickContact>> = quickContactsRepo.contacts
    private val _messagingDraft = MutableStateFlow<MessagingDraft?>(null)
    val messagingDraft: StateFlow<MessagingDraft?> = _messagingDraft.asStateFlow()

    fun clearCapturedMessages() {
        NotificationQueueManager.clearQueue()
    }

    fun setSpamFilterEnabled(enabled: Boolean) {
        spamFilterPrefs.setSpamFilterEnabled(enabled)
    }

    fun muteMessageSender(sender: String) {
        spamFilterPrefs.muteSender(sender)
        NotificationQueueManager.muteSenderFromQueue(sender)
    }

    fun unmuteMessageSender(sender: String) {
        spamFilterPrefs.unmuteSender(sender)
    }

    fun restoreFilteredMessage(messageId: String) {
        NotificationQueueManager.restoreFilteredMessage(messageId)
    }

    fun clearFilteredMessages() {
        NotificationQueueManager.clearFilteredQueue()
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
        RetrofitClient.configureAppIdentity(application)
        RadioServiceBridge.onTogglePlayPause = { toggleRadioPlayPause() }
        RadioServiceBridge.onPlay = { resumeRadio() }
        RadioServiceBridge.onPause = { pauseRadio() }
        RadioServiceBridge.onStopPlayback = { stopRadio() }
        RadioServiceBridge.onSkipBackward = { seekRadioBy(-15_000L) }
        RadioServiceBridge.onSkipForward = { seekRadioBy(30_000L) }
        RadioServiceBridge.onNextEpisode = { nextPodcastEpisode() }
        RadioServiceBridge.onPreviousEpisode = { previousPodcastEpisode() }
        RadioServiceBridge.onSeekTo = { posMs -> seekRadioTo(posMs) }
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

    private fun startListeningSequence(dictationMode: Boolean = false) {
        if (!_hasMicPermission.value) return
        listeningWatchdogJob?.cancel()
        hasDetectedSpeechInTurn = false

        voiceSpeaker.stop()
        audioFocusManager.requestVoicePriorityFocus()
        radioManager.duck(true)
        soundFx.playListeningCue()
        _assistantState.value = AssistantState.LISTENING
        _statusText.value = if (dictationMode) "Dictating message…" else "Listening (5s window)…"
        _liveTranscript.value = ""
        voiceRecognizer.startListening(dictationMode = dictationMode)

        val timeoutMs = if (dictationMode) DICTATION_SILENCE_TIMEOUT_MS else LISTENING_SILENCE_TIMEOUT_MS
        listeningWatchdogJob = viewModelScope.launch {
            delay(timeoutMs)
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

    fun processUserQuestion(rawQuestion: String) {
        val question = rawQuestion.trim().take(MAX_QUERY_LENGTH)
        if (question.isBlank()) return

        val normalized = question.lowercase(Locale.US).replace(Regex("""[^a-z0-9\s]"""), "")

        // 0a. Active Multi-Turn Voice Messaging / Reply Draft (100% On-Device, Zero Gemini)
        val activeDraft = _messagingDraft.value
        if (activeDraft != null) {
            handleSpokenDraftTurn(activeDraft, question, normalized)
            return
        }

        // 0b. Handle spoken confirmation for pending Visual Google Search handoff (Approach B)
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

        // 3b. 100% Local Spoken Reply to Notification (RemoteInput silent reply or pre-filled fallback)
        val replyCommand = LocalMessagingParser.parseReplyCommand(question)
        if (replyCommand != null) {
            _currentQuestion.value = question
            startReplyFromVoice(replyCommand)
            return
        }

        // 3c. 100% Local Spoken New Message Compose (SMS, WhatsApp, Telegram, Viber, Gmail)
        val composeCommand = LocalMessagingParser.parseNewMessageCommand(question, quickContacts.value)
        if (composeCommand != null) {
            _currentQuestion.value = question
            startNewMessageFromVoice(composeCommand)
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

        // 4b. 100% Local Podcast Seek, Episode Step & Sleep Timer Voice Commands
        val seekDelta = LocalCommandParser.detectSeekDeltaMs(normalized)
        if (seekDelta != null && currentRadioStation.value != null) {
            _currentQuestion.value = question
            seekRadioBy(seekDelta)
            val seconds = kotlin.math.abs(seekDelta / 1000L)
            val dir = if (seekDelta >= 0) "forward" else "back"
            val msg = "Skipped $dir $seconds seconds."
            _spokenAnswer.value = msg
            speakResponse(msg)
            return
        }

        val episodeStep = LocalCommandParser.detectEpisodeNavCommand(normalized)
        if (episodeStep != null && currentRadioStation.value != null) {
            _currentQuestion.value = question
            val switched = if (episodeStep > 0) nextPodcastEpisode() else previousPodcastEpisode()
            val msg = if (switched) {
                currentEpisodeTitle.value?.let { "Playing $it." } ?: "Switched episode."
            } else {
                "No other episode available in this feed."
            }
            _spokenAnswer.value = msg
            speakResponse(msg)
            return
        }

        val sleepCmd = LocalCommandParser.detectSleepTimerCommand(normalized)
        if (sleepCmd != null) {
            _currentQuestion.value = question
            val msg = when {
                sleepCmd == 0 -> {
                    cancelSleepTimer()
                    "Sleep timer cancelled."
                }
                sleepCmd < 0 -> {
                    setSleepAtEndOfEpisode()
                    "Audio will stop at the end of this episode."
                }
                else -> {
                    setSleepTimerMinutes(sleepCmd)
                    "Sleep timer set for $sleepCmd minutes."
                }
            }
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

    private fun speakAndListen(text: String, dictationMode: Boolean = false) {
        listeningWatchdogJob?.cancel()
        audioFocusManager.requestVoicePriorityFocus()
        radioManager.duck(true)

        _assistantState.value = AssistantState.SPEAKING
        _spokenAnswer.value = text
        _statusText.value = "VoiceGem speaking…"

        voiceSpeaker.speak(
            text = text,
            utteranceId = "prompt_${System.currentTimeMillis()}",
            onStart = {
                _assistantState.value = AssistantState.SPEAKING
            },
            onDone = {
                viewModelScope.launch {
                    delay(180)
                    if (_hasMicPermission.value) {
                        startListeningSequence(dictationMode = dictationMode)
                    } else {
                        _assistantState.value = AssistantState.IDLE
                        audioFocusManager.abandonVoiceFocus()
                    }
                }
            },
            onError = {
                viewModelScope.launch {
                    _assistantState.value = AssistantState.IDLE
                    audioFocusManager.abandonVoiceFocus()
                }
            }
        )
    }

    // ==================== Zero-Gemini Spoken Reply & Compose Actions ====================

    private fun startReplyFromVoice(parsed: ReplyCommandParseResult) {
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()

        val entry = NotificationReplyStore.findReplyTarget(
            senderQuery = parsed.targetQuery,
            appFilter = parsed.channel?.displayName
        )

        if (entry == null) {
            val missingMsg = if (!parsed.targetQuery.isNullOrBlank()) {
                "I couldn't find a recent notification from ${parsed.targetQuery} to reply to. Say send a message to ${parsed.targetQuery} instead."
            } else {
                "There are no recent notifications to reply to right now."
            }
            _spokenAnswer.value = missingMsg
            speakResponse(missingMsg)
            return
        }

        val targetMsg = entry.message
        val channel = MessagingChannel.fromAppNameOrPackage(targetMsg.appName, targetMsg.packageName)
        val hasRemoteInput = entry.replyAction != null
        val body = parsed.messageBody?.trim().orEmpty()

        val draft = MessagingDraft(
            mode = MessagingMode.SILENT_REPLY,
            stage = if (body.isNotBlank()) ComposeStage.AWAITING_CONFIRMATION else ComposeStage.DICTATING_BODY,
            channel = channel,
            recipientName = targetMsg.sender,
            messageBody = body,
            replyMessageId = targetMsg.id,
            replyPackageName = targetMsg.packageName,
            hasDirectRemoteInput = hasRemoteInput,
            originalMessageSnippet = targetMsg.text
        )
        _messagingDraft.value = draft

        if (draft.stage == ComposeStage.AWAITING_CONFIRMATION) {
            speakAndListen(buildConfirmationPrompt(draft), dictationMode = false)
        } else {
            val modeHint = if (hasRemoteInput) "directly in VoiceGem" else "via ${channel.displayName}"
            speakAndListen(
                "Replying to ${targetMsg.sender} on ${targetMsg.appName} $modeHint. What would you like to say?",
                dictationMode = true
            )
        }
    }

    private fun startNewMessageFromVoice(parsed: NewMessageParseResult) {
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()

        val hasAnyRecipient = parsed.recipientPhone.isNotBlank() ||
            parsed.recipientEmail.isNotBlank() ||
            parsed.recipientName.isNotBlank()

        val stage = when {
            !hasAnyRecipient -> ComposeStage.NEEDS_RECIPIENT
            parsed.messageBody.isBlank() -> ComposeStage.DICTATING_BODY
            else -> ComposeStage.AWAITING_CONFIRMATION
        }

        val draft = MessagingDraft(
            mode = MessagingMode.NEW_MESSAGE,
            stage = stage,
            channel = parsed.channel,
            recipientName = parsed.recipientName,
            recipientPhone = parsed.recipientPhone,
            recipientEmail = parsed.recipientEmail,
            messageBody = parsed.messageBody
        )
        _messagingDraft.value = draft

        when (stage) {
            ComposeStage.NEEDS_RECIPIENT -> {
                val prompt = if (parsed.channel == MessagingChannel.GMAIL) {
                    "Who should I email? Speak their name from Quick Contacts or their email address."
                } else {
                    "Who would you like to message on ${parsed.channel.displayName}? Speak a Quick Contact name or phone number."
                }
                speakAndListen(prompt, dictationMode = false)
            }
            ComposeStage.DICTATING_BODY -> {
                val targetLabel = draft.recipientName.ifBlank {
                    draft.recipientEmail.ifBlank { draft.recipientPhone }
                }
                speakAndListen(
                    "Composing ${draft.channel.displayName} message to $targetLabel. What should it say?",
                    dictationMode = true
                )
            }
            ComposeStage.AWAITING_CONFIRMATION -> {
                speakAndListen(buildConfirmationPrompt(draft), dictationMode = false)
            }
        }
    }

    private fun handleSpokenDraftTurn(
        draft: MessagingDraft,
        rawUtterance: String,
        normalized: String
    ) {
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()
        _currentQuestion.value = rawUtterance

        when (draft.stage) {
            ComposeStage.AWAITING_CONFIRMATION -> {
                val switchedChannel = detectDraftChannelSwitch(normalized)
                if (switchedChannel != null && draft.mode == MessagingMode.NEW_MESSAGE) {
                    val updated = draft.copy(channel = switchedChannel)
                    _messagingDraft.value = updated
                    speakAndListen(buildConfirmationPrompt(updated), dictationMode = false)
                    return
                }
                when (LocalMessagingParser.detectConfirmationAction(normalized)) {
                    ConfirmationAction.SEND -> confirmAndSendDraft(getApplication())
                    ConfirmationAction.EDIT -> {
                        val updated = draft.copy(stage = ComposeStage.DICTATING_BODY, messageBody = "")
                        _messagingDraft.value = updated
                        speakAndListen("Okay, go ahead and dictate your new message.", dictationMode = true)
                    }
                    ConfirmationAction.CANCEL -> {
                        _messagingDraft.value = null
                        _spokenAnswer.value = "Message cancelled."
                        speakResponse("Message cancelled.")
                    }
                    null -> {
                        if (LocalCommandParser.isExitCommand(normalized)) {
                            _messagingDraft.value = null
                            endChat(spokenFarewell = true)
                        } else {
                            speakAndListen(
                                "Say Send to deliver, Edit to rewrite, or Cancel to discard.",
                                dictationMode = false
                            )
                        }
                    }
                }
            }
            ComposeStage.DICTATING_BODY -> {
                val action = LocalMessagingParser.detectConfirmationAction(normalized)
                if (action == ConfirmationAction.CANCEL) {
                    _messagingDraft.value = null
                    _spokenAnswer.value = "Message cancelled."
                    speakResponse("Message cancelled.")
                    return
                }
                if (normalized == "exit" || normalized == "quit" || normalized == "end chat") {
                    _messagingDraft.value = null
                    endChat(spokenFarewell = true)
                    return
                }
                val updated = draft.copy(
                    stage = ComposeStage.AWAITING_CONFIRMATION,
                    messageBody = rawUtterance.trim()
                )
                _messagingDraft.value = updated
                speakAndListen(buildConfirmationPrompt(updated), dictationMode = false)
            }
            ComposeStage.NEEDS_RECIPIENT -> {
                if (LocalMessagingParser.detectConfirmationAction(normalized) == ConfirmationAction.CANCEL) {
                    _messagingDraft.value = null
                    _spokenAnswer.value = "Message cancelled."
                    speakResponse("Message cancelled.")
                    return
                }
                val parsedEmail = LocalMessagingParser.parseSpokenEmail(rawUtterance).orEmpty()
                val parsedPhone = if (parsedEmail.isEmpty()) {
                    LocalMessagingParser.parseSpokenPhoneNumber(rawUtterance).orEmpty()
                } else ""
                val matchedContact = if (parsedEmail.isEmpty() && parsedPhone.isEmpty()) {
                    QuickContactsRepository.findBestMatch(rawUtterance, quickContacts.value)
                } else null

                val updatedChannel = when {
                    parsedEmail.isNotBlank() -> MessagingChannel.GMAIL
                    matchedContact != null && draft.channel == MessagingChannel.SMS -> matchedContact.preferredChannel
                    else -> draft.channel
                }

                val updated = draft.copy(
                    channel = updatedChannel,
                    recipientName = matchedContact?.displayName
                        ?: parsedEmail.ifBlank { parsedPhone.ifBlank { rawUtterance.trim() } },
                    recipientPhone = parsedPhone.ifBlank { matchedContact?.phoneNumber.orEmpty() },
                    recipientEmail = parsedEmail.ifBlank { matchedContact?.emailAddress.orEmpty() },
                    stage = if (draft.messageBody.isBlank()) ComposeStage.DICTATING_BODY else ComposeStage.AWAITING_CONFIRMATION
                )
                _messagingDraft.value = updated

                if (updated.stage == ComposeStage.DICTATING_BODY) {
                    speakAndListen(
                        "Got it, ${updated.recipientName}. What would you like to say?",
                        dictationMode = true
                    )
                } else {
                    speakAndListen(buildConfirmationPrompt(updated), dictationMode = false)
                }
            }
        }
    }

    private fun buildConfirmationPrompt(draft: MessagingDraft): String {
        val target = draft.recipientName.ifBlank {
            draft.recipientEmail.ifBlank { draft.recipientPhone.ifBlank { "your contact" } }
        }
        val deliveryNote = if (draft.mode == MessagingMode.SILENT_REPLY && draft.hasDirectRemoteInput) {
            "Send reply to $target on ${draft.channel.displayName} saying: ${draft.messageBody}. Say Send, Edit, or Cancel."
        } else {
            "Prepare ${draft.channel.displayName} message to $target saying: ${draft.messageBody}. Say Send to open and confirm, Edit, or Cancel."
        }
        return deliveryNote
    }

    fun startVoiceReplyForMessage(message: CapturedMessage) {
        val entry = NotificationReplyStore.getEntry(message.id)
        val hasDirect = entry?.replyAction != null
        val channel = MessagingChannel.fromAppNameOrPackage(message.appName, message.packageName)
        val draft = MessagingDraft(
            mode = MessagingMode.SILENT_REPLY,
            stage = ComposeStage.DICTATING_BODY,
            channel = channel,
            recipientName = message.sender,
            messageBody = "",
            replyMessageId = message.id,
            replyPackageName = message.packageName,
            hasDirectRemoteInput = hasDirect,
            originalMessageSnippet = message.text
        )
        _messagingDraft.value = draft
        if (_hasMicPermission.value && voiceSpeaker.isReady) {
            speakAndListen(
                "Dictate your reply to ${message.sender} on ${message.appName}.",
                dictationMode = true
            )
        }
    }

    fun startNewMessageCompose(prefillContact: QuickContact? = null) {
        val draft = if (prefillContact != null) {
            MessagingDraft(
                mode = MessagingMode.NEW_MESSAGE,
                stage = ComposeStage.DICTATING_BODY,
                channel = prefillContact.preferredChannel,
                recipientName = prefillContact.displayName,
                recipientPhone = prefillContact.phoneNumber,
                recipientEmail = prefillContact.emailAddress
            )
        } else {
            MessagingDraft(
                mode = MessagingMode.NEW_MESSAGE,
                stage = ComposeStage.DICTATING_BODY,
                channel = MessagingChannel.SMS
            )
        }
        _messagingDraft.value = draft
        _statusText.value = "Composing message…"
    }

    fun updateMessagingDraftChannel(channel: MessagingChannel) {
        val current = _messagingDraft.value ?: return
        _messagingDraft.value = current.copy(channel = channel)
    }

    fun updateMessagingDraftRecipient(name: String, phone: String, email: String) {
        val current = _messagingDraft.value ?: return
        _messagingDraft.value = current.copy(
            recipientName = name,
            recipientPhone = phone,
            recipientEmail = email
        )
    }

    fun selectQuickContactForDraft(contact: QuickContact) {
        val current = _messagingDraft.value ?: return
        _messagingDraft.value = current.copy(
            channel = contact.preferredChannel,
            recipientName = contact.displayName,
            recipientPhone = contact.phoneNumber,
            recipientEmail = contact.emailAddress,
            stage = if (current.messageBody.isBlank()) ComposeStage.DICTATING_BODY else ComposeStage.AWAITING_CONFIRMATION
        )
    }

    fun updateMessagingDraftBody(body: String) {
        val current = _messagingDraft.value ?: return
        _messagingDraft.value = current.copy(
            messageBody = body,
            stage = if (body.isNotBlank()) ComposeStage.AWAITING_CONFIRMATION else ComposeStage.DICTATING_BODY
        )
    }

    private fun detectDraftChannelSwitch(normalized: String): MessagingChannel? {
        val switchPrefixes = listOf("use ", "switch to ", "send via ", "via ", "using ", "change to ")
        if (switchPrefixes.none { normalized.startsWith(it) || normalized.contains(it) }) return null
        return when {
            normalized.contains("whatsapp") -> MessagingChannel.WHATSAPP
            normalized.contains("telegram") -> MessagingChannel.TELEGRAM
            normalized.contains("viber") -> MessagingChannel.VIBER
            normalized.contains("gmail") || normalized.contains("email") -> MessagingChannel.GMAIL
            normalized.contains("sms") || normalized.contains("text") -> MessagingChannel.SMS
            else -> null
        }
    }

    fun dictateMessagingDraftBody() {
        val current = _messagingDraft.value ?: return
        if (_assistantState.value == AssistantState.LISTENING) {
            listeningWatchdogJob?.cancel()
            voiceRecognizer.stopListening()
            return
        }
        _messagingDraft.value = current.copy(stage = ComposeStage.DICTATING_BODY)
        if (_hasMicPermission.value) {
            startListeningSequence(dictationMode = true)
        }
    }

    fun confirmAndSendDraft(context: Context) {
        val draft = _messagingDraft.value ?: return
        listeningWatchdogJob?.cancel()
        voiceRecognizer.stopListening()
        voiceSpeaker.stop()

        val cleanBody = draft.messageBody.trim()
        if (cleanBody.isBlank()) {
            val prompt = "Your message is empty. Please speak or type a message first."
            _spokenAnswer.value = prompt
            speakResponse(prompt)
            return
        }

        if (draft.mode == MessagingMode.SILENT_REPLY && !draft.replyMessageId.isNullOrBlank()) {
            when (NotificationReplyStore.sendDirectReply(context, draft.replyMessageId, cleanBody)) {
                DirectReplyOutcome.Sent -> {
                    NotificationQueueManager.removeMessageById(draft.replyMessageId)
                    _messagingDraft.value = null
                    val confirmation = "Reply sent to ${draft.recipientName} on ${draft.channel.displayName}."
                    _spokenAnswer.value = confirmation
                    _statusText.value = confirmation
                    speakResponse(confirmation)
                    return
                }
                DirectReplyOutcome.Expired, DirectReplyOutcome.NoDirectReplyAction -> {
                    // Fall through to zero-permission pre-filled Intent handoff
                }
            }
        }

        val launchResult = MessagingIntentLauncher.launchPreFilledCompose(
            context = context,
            draft = draft.copy(messageBody = cleanBody)
        )
        _messagingDraft.value = null
        _spokenAnswer.value = launchResult.feedbackMessage
        _statusText.value = launchResult.feedbackMessage
        speakResponse(launchResult.feedbackMessage)
    }

    fun cancelMessagingDraft() {
        listeningWatchdogJob?.cancel()
        voiceRecognizer.cancel()
        voiceSpeaker.stop()
        _rmsLevel.value = 0f
        _liveTranscript.value = ""
        _messagingDraft.value = null
        _assistantState.value = AssistantState.IDLE
        _statusText.value = "Message draft discarded."
        audioFocusManager.abandonVoiceFocus()
    }

    fun addQuickContact(contact: QuickContact): Boolean {
        return quickContactsRepo.addOrUpdateContact(contact)
    }

    fun deleteQuickContact(contactId: String) {
        quickContactsRepo.deleteContact(contactId)
    }

    fun importPickedQuickContact(context: Context, uri: Uri): QuickContact? {
        val extracted = QuickContactsRepository.extractFromPickedUri(context, uri) ?: return null
        quickContactsRepo.addOrUpdateContact(extracted)
        return extracted
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

    fun resumeRadio() {
        audioFocusManager.requestMediaPlaybackFocus()
        radioManager.resume()
    }

    fun pauseRadio() {
        audioFocusManager.onMediaManuallyPaused()
        radioManager.pause()
    }

    fun toggleRadioPlayPause() {
        if (radioManager.isPlaying.value) {
            pauseRadio()
        } else {
            resumeRadio()
        }
    }

    fun seekRadioBy(deltaMs: Long) {
        radioManager.seekBy(deltaMs)
    }

    fun seekRadioTo(targetMs: Long) {
        radioManager.seekTo(targetMs)
    }

    fun selectPodcastEpisode(index: Int) {
        audioFocusManager.requestMediaPlaybackFocus()
        radioManager.selectEpisode(index)
    }

    fun nextPodcastEpisode(): Boolean {
        audioFocusManager.requestMediaPlaybackFocus()
        return radioManager.nextEpisode()
    }

    fun previousPodcastEpisode(): Boolean {
        audioFocusManager.requestMediaPlaybackFocus()
        return radioManager.previousEpisode()
    }

    fun setSleepTimerMinutes(minutes: Int) {
        radioManager.setSleepTimerMinutes(minutes)
    }

    fun setSleepAtEndOfEpisode() {
        radioManager.setSleepAtEndOfEpisode()
    }

    fun cancelSleepTimer() {
        radioManager.cancelSleepTimer()
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
    ): Boolean {
        return podcastPreferences.updatePodcastSlot(slotNumber, name, genre, streamUrl, iconEmoji)
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
        _messagingDraft.value = null
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
        RadioServiceBridge.onPlay = null
        RadioServiceBridge.onPause = null
        RadioServiceBridge.onStopPlayback = null
        RadioServiceBridge.onSkipBackward = null
        RadioServiceBridge.onSkipForward = null
        RadioServiceBridge.onNextEpisode = null
        RadioServiceBridge.onPreviousEpisode = null
        RadioServiceBridge.onSeekTo = null
        voiceRecognizer.destroy()
        voiceSpeaker.shutdown()
        radioManager.release()
        audioFocusManager.abandonAll()
        bluetoothCarManager.release()
        soundFx.release()
    }

    companion object {
        const val LISTENING_SILENCE_TIMEOUT_MS = 5_000L
        const val DICTATION_SILENCE_TIMEOUT_MS = 8_000L
        const val MAX_QUERY_LENGTH = 1500
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
            normalized.contains("slot 7") || normalized.contains("podcast 7") || normalized.contains("seventh podcast") -> podcastSlots.getOrNull(6)
            normalized.contains("slot 8") || normalized.contains("podcast 8") || normalized.contains("eighth podcast") -> podcastSlots.getOrNull(7)
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

    fun detectSeekDeltaMs(normalized: String): Long? {
        val forwardHints = listOf("skip forward", "fast forward", "jump forward", "forward 30", "ahead 30", "forward 15", "skip ahead")
        if (forwardHints.any { normalized.contains(it) }) {
            return if (normalized.contains("15")) 15_000L else 30_000L
        }
        val backHints = listOf("rewind", "skip back", "jump back", "go back 15", "back 15", "back 30")
        if (backHints.any { normalized.contains(it) }) {
            return if (normalized.contains("30")) -30_000L else -15_000L
        }
        return null
    }

    fun detectEpisodeNavCommand(normalized: String): Int? {
        return when {
            normalized == "next episode" || normalized == "play next episode" || normalized == "older episode" -> 1
            normalized == "previous episode" || normalized == "play previous episode" || normalized == "latest episode" -> -1
            else -> null
        }
    }

    /**
     * Returns:
     * - positive minutes (1..180) for timed sleep
     * - -1 for "end of episode"
     * - 0 for "cancel sleep timer"
     * - null if unrelated query
     */
    fun detectSleepTimerCommand(normalized: String): Int? {
        if (!normalized.contains("sleep") && !normalized.contains("stop audio in")) return null
        if (normalized.contains("cancel sleep") || normalized.contains("turn off sleep") || normalized.contains("stop sleep timer")) {
            return 0
        }
        if (normalized.contains("end of episode") || normalized.contains("after this episode")) {
            return -1
        }
        val match = Regex("""\b(\d{1,3})\s*(?:minute|minutes|min|mins)\b""").find(normalized)
        val mins = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (mins != null && mins in 1..180) mins else null
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
