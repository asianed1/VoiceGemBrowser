package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import android.app.Activity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.ui.components.AccountSubscriptionSheet
import com.example.ui.components.AudioSettingsDialog
import com.example.ui.components.CarStereoAudioSheet
import com.example.ui.components.ConversationHistorySheet
import com.example.ui.components.GoogleAuthOnboardingDialog
import com.example.ui.components.MessagesSummarySheet
import com.example.ui.components.RadioStationsSheet
import com.example.ui.components.TextInputFallbackDialog
import com.example.ui.components.VoiceGemOrb
import com.example.ui.theme.GemCardBorder
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemDeepNavy
import com.example.ui.theme.GemElectricBlue
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemRose
import com.example.ui.theme.GemViolet
import com.example.ui.theme.TextSecondaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceGemScreen(
    viewModel: VoiceGemViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by viewModel.assistantState.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val currentQuestion by viewModel.currentQuestion.collectAsState()
    val spokenAnswer by viewModel.spokenAnswer.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val history by viewModel.conversationHistory.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val hasMicPermission by viewModel.hasMicPermission.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val showOnboarding by viewModel.showOnboarding.collectAsState()
    val isRadioPlaying by viewModel.isRadioPlaying.collectAsState()
    val isRadioLoading by viewModel.isRadioLoading.collectAsState()
    val currentRadioStation by viewModel.currentRadioStation.collectAsState()
    val isRadioDucked by viewModel.isRadioDucked.collectAsState()
    val radioStatusDetail by viewModel.radioStatusDetail.collectAsState()
    val preferredPodcasts by viewModel.preferredPodcasts.collectAsState()
    val unreadMessagesCount by viewModel.unreadMessagesCount.collectAsState()
    val capturedMessages by viewModel.capturedMessages.collectAsState()
    val carAudioStatus by viewModel.carAudioStatus.collectAsState()
    val voiceGender by viewModel.voiceGender.collectAsState()
    val pendingGoogleSearchQuery by viewModel.pendingGoogleSearchQuery.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showRadioSheet by remember { mutableStateOf(false) }
    var showMessagesSheet by remember { mutableStateOf(false) }
    var showCarAudioSheet by remember { mutableStateOf(false) }

    // Audio recording permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    // Android 13+ playback notification permission launcher (requested politely when starting radio/podcast)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensurePlaybackNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.onPermissionResult(currentPermission)
        if (!currentPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, GemCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.example.R.drawable.vgb_launcher_asset),
                                contentDescription = "VoiceGemBrowser Logo",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "VoiceGem",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GemCyan.copy(alpha = 0.16f))
                                        .border(1.dp, GemCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("app_version_badge")
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GemCyan,
                                        maxLines = 1
                                    )
                                }
                            }
                            Text(
                                text = "Voice-First Browser",
                                fontSize = 11.sp,
                                color = GemCyan.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Gemini API Key & Account settings pill
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (userProfile.hasCustomApiKey) GemEmerald.copy(alpha = 0.15f) else GemDarkCard)
                            .border(
                                1.dp,
                                if (userProfile.hasCustomApiKey) GemEmerald.copy(alpha = 0.6f) else GemCardBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showAccountSheet = true
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("account_limit_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Gemini API Settings",
                                tint = if (userProfile.hasCustomApiKey) GemEmerald else GemCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (userProfile.hasCustomApiKey) "Custom API" else "Gemini API",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (userProfile.hasCustomApiKey) GemEmerald else Color.White,
                                maxLines = 1
                            )
                        }
                    }

                    // History sheet button (only secondary icon kept in TopBar)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showHistorySheet = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Conversation History",
                            tint = if (history.isNotEmpty()) GemCyan else TextSecondaryLight
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GemDeepNavy
                )
            )
        },
        containerColor = GemDeepNavy,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flexible Middle Hero Zone: scrolls cleanly on compact screens while keeping bottom cockpit decks pinned!
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
            // Permission request banner if microphone access is needed
            if (!hasMicPermission) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemRose.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("permission_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = GemRose,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Microphone Access Required",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "VoiceGem is a 100% voice-first spoken browser. Please allow microphone access to speak your queries.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = TextSecondaryLight
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = GemRose),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("Enable Voice Microphone", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // State Pill Badge
            StateIndicatorBadge(state = state, statusText = statusText)

            // Car Stereo Connected Pill
            if (carAudioStatus.isConnected) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(GemEmerald.copy(alpha = 0.12f))
                        .border(1.dp, GemEmerald.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { showCarAudioSheet = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("car_stereo_status_pill"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = GemEmerald,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Car Audio: ${carAudioStatus.deviceName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GemEmerald
                    )
                }
            }

            // Active Radio Stream Mini Pill
            if (currentRadioStation != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isRadioDucked) GemViolet.copy(alpha = 0.8f) else GemCyan.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRadioSheet = true }
                        .testTag("active_radio_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = if (isRadioDucked) GemViolet else GemCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = currentRadioStation?.name ?: "Public Radio",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = when {
                                        !radioStatusDetail.isNullOrBlank() -> radioStatusDetail!!
                                        isRadioDucked -> "Voice Priority: Ducked to 15%"
                                        isRadioLoading -> "Buffering stream…"
                                        isRadioPlaying -> "Live Stream (100% Vol)"
                                        else -> "Paused — tap ▶ to resume"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isRadioDucked) GemViolet else GemCyan.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.toggleRadioPlayPause() },
                                modifier = Modifier.size(32.dp).testTag("radio_banner_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isRadioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isRadioPlaying) "Pause" else "Play",
                                    tint = GemCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.stopRadio() },
                                modifier = Modifier.size(32.dp).testTag("radio_banner_stop")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Radio",
                                    tint = GemRose,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Unread Messages Spoken Summary Banner
            if (unreadMessagesCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemRose.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMessagesSheet = true }
                        .testTag("unread_messages_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = GemRose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "$unreadMessagesCount New Message${if (unreadMessagesCount > 1) "s" else ""}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "WhatsApp / Gmail / SMS waiting",
                                    fontSize = 11.sp,
                                    color = GemRose.copy(alpha = 0.9f)
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.summarizeIncomingMessages() },
                            colors = ButtonDefaults.buttonColors(containerColor = GemRose.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GemRose),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("banner_speak_summary_button")
                        ) {
                            Text("Hear Summary", color = GemRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // The Glowing Centerpiece Voice Gem Orb
            VoiceGemOrb(
                state = state,
                rmsLevel = rmsLevel,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.onGemOrbClicked()
                },
                containerSize = 196.dp,
                bodySize = 148.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Spoken Transcription / Live Voice Card
            VoiceConversationHUD(
                state = state,
                liveTranscript = liveTranscript,
                currentQuestion = currentQuestion,
                spokenAnswer = spokenAnswer,
                onInterrupt = { viewModel.interruptSpeech() },
                onReplay = {
                    if (spokenAnswer.isNotBlank()) {
                        viewModel.speakTextDirectly(spokenAnswer)
                    }
                }
            )

            // Approach B: Explicit Exit Warning Card before opening visual Google Search
            val activeGoogleQuery = pendingGoogleSearchQuery
            if (!activeGoogleQuery.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_search_warning_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Open Visual Google Search?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Warning: Employing Google Search will exit VoiceGemBrowser and display visual text results in the Google/Chrome app.",
                            fontSize = 12.sp,
                            color = TextSecondaryLight,
                            lineHeight = 16.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openPendingGoogleSearch(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = GemCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("open_google_search_button")
                            ) {
                                Text(
                                    text = "Open Google Search",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.dismissPendingGoogleSearch() },
                                border = androidx.compose.foundation.BorderStroke(1.dp, GemCardBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("dismiss_google_search_button")
                            ) {
                                Text(
                                    text = "Stay Here",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            } // End scrollable Hero Zone

            // Lower Cockpit Thumb Zone: 2 Fixed-Position Tactile Decks (Never Shift!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Deck 1: 4 Large Equal Feature Tiles (Messages, Radio, Car BT, Settings)
                CockpitFeatureTilesRow(
                    unreadMessagesCount = unreadMessagesCount,
                    isRadioActive = isRadioPlaying || isRadioLoading,
                    isCarConnected = carAudioStatus.isConnected,
                    onOpenMessages = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMessagesSheet = true
                    },
                    onOpenRadio = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showRadioSheet = true
                    },
                    onOpenCarAudio = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCarAudioSheet = true
                    },
                    onOpenSettings = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSettingsDialog = true
                    }
                )

                // Deck 2: Anchored Clean 3-Button Action & Hang-Up Dock (Type | Tap to Speak | End)
                BottomControlsBar(
                    state = state,
                    onOpenKeyboard = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showTextInputDialog = true
                    },
                    onEndChat = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.endChat(spokenFarewell = true)
                    },
                    onMainActionClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.onGemOrbClicked()
                    }
                )
            }
        }
    }

    // Modal Sheets and Dialogs
    if (showHistorySheet) {
        ConversationHistorySheet(
            messages = history,
            onDismiss = { showHistorySheet = false },
            onSpeakMessage = { text -> viewModel.speakTextDirectly(text) },
            onClearHistory = { viewModel.clearHistory() }
        )
    }

    if (showSettingsDialog) {
        AudioSettingsDialog(
            voiceGender = voiceGender,
            onVoiceGenderChange = { viewModel.setVoiceGender(it, speakConfirmation = false) },
            speechRate = speechRate,
            onSpeechRateChange = { viewModel.setSpeechRate(it) },
            pitch = pitch,
            onPitchChange = { viewModel.setPitch(it) },
            onTestVoice = { viewModel.testVoice() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showTextInputDialog) {
        TextInputFallbackDialog(
            onDismiss = { showTextInputDialog = false },
            onSubmit = { query ->
                showTextInputDialog = false
                viewModel.processUserQuestion(query)
            }
        )
    }

    if (showOnboarding) {
        GoogleAuthOnboardingDialog(
            onSignInWithGoogle = { viewModel.signInWithGoogle(context) },
            onContinueAsGuest = { viewModel.dismissOnboarding() }
        )
    }

    if (showAccountSheet) {
        AccountSubscriptionSheet(
            profile = userProfile,
            onDismiss = { showAccountSheet = false },
            onToggleSubscriber = { viewModel.setSubscriber(it) },
            onSignOut = {
                viewModel.signOut()
                showAccountSheet = false
            },
            onSaveCustomApiKey = { viewModel.saveCustomApiKey(it) }
        )
    }

    if (showRadioSheet) {
        RadioStationsSheet(
            isPlaying = isRadioPlaying,
            isLoading = isRadioLoading,
            currentStation = currentRadioStation,
            isDucked = isRadioDucked,
            preferredPodcasts = preferredPodcasts,
            onSelectStation = { station ->
                ensurePlaybackNotificationPermission()
                viewModel.playRadioStation(station)
            },
            onTogglePlayPause = {
                ensurePlaybackNotificationPermission()
                viewModel.toggleRadioPlayPause()
            },
            onStopRadio = { viewModel.stopRadio() },
            onUpdatePodcastSlot = { slotNum, name, genre, url, emoji ->
                viewModel.updatePodcastSlot(slotNum, name, genre, url, emoji)
            },
            onResetPodcastSlot = { slotNum ->
                viewModel.resetPodcastSlot(slotNum)
            },
            onDismiss = { showRadioSheet = false }
        )
    }

    if (showMessagesSheet) {
        MessagesSummarySheet(
            messages = capturedMessages,
            onSummarizeAndSpeak = {
                showMessagesSheet = false
                viewModel.summarizeIncomingMessages()
            },
            onClearMessages = { viewModel.clearCapturedMessages() },
            onDismiss = { showMessagesSheet = false }
        )
    }

    if (showCarAudioSheet) {
        CarStereoAudioSheet(
            status = carAudioStatus,
            onSetRoutingMode = { mode -> viewModel.setCarAudioRoutingMode(mode) },
            onToggleAutoPause = { enabled -> viewModel.setAutoPauseOnDisconnect(enabled) },
            onTestAudio = { viewModel.testCarStereoAudio() },
            onDismiss = { showCarAudioSheet = false }
        )
    }
}

@Composable
private fun StateIndicatorBadge(
    state: AssistantState,
    statusText: String
) {
    val (badgeBg, dotColor, label) = when (state) {
        AssistantState.LISTENING -> Triple(GemEmerald.copy(alpha = 0.15f), GemEmerald, "Listening")
        AssistantState.PROCESSING -> Triple(GemCyan.copy(alpha = 0.15f), GemCyan, "Thinking")
        AssistantState.SPEAKING -> Triple(GemViolet.copy(alpha = 0.15f), GemViolet, "Speaking")
        AssistantState.IDLE -> Triple(GemDarkCard, GemCyan.copy(alpha = 0.7f), "Ready")
        AssistantState.ERROR -> Triple(GemRose.copy(alpha = 0.15f), GemRose, "Alert")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(badgeBg)
                .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("state_indicator_badge"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = dotColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusText,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun VoiceConversationHUD(
    state: AssistantState,
    liveTranscript: String,
    currentQuestion: String,
    spokenAnswer: String,
    onInterrupt: () -> Unit,
    onReplay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GemDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, GemCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = Color.Black)
            .testTag("conversation_hud_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp, max = 148.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // Live speech transcription or active question
            if (liveTranscript.isNotBlank() || currentQuestion.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = GemCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state == AssistantState.LISTENING && liveTranscript.isNotBlank()) "Hearing you:" else "Your Question:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GemCyan
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (liveTranscript.isNotBlank()) liveTranscript else currentQuestion,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // VoiceGem Spoken Response section
            if (spokenAnswer.isNotBlank()) {
                if (liveTranscript.isNotBlank() || currentQuestion.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(GemCardBorder.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = GemViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Spoken Answer:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GemViolet
                        )
                    }

                    Row {
                        if (state == AssistantState.SPEAKING) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GemRose.copy(alpha = 0.2f))
                                    .clickable { onInterrupt() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = GemRose,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Stop", color = GemRose, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            IconButton(
                                onClick = onReplay,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Hear answer again",
                                    tint = GemCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = spokenAnswer,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = Color.White.copy(alpha = 0.95f)
                )
            } else if (state == AssistantState.IDLE) {
                Text(
                    text = "Mic stays off to protect privacy and save battery. Tap Speak, tap the Gem, or say \"Hey Google, open VoiceGem\" when ready.",
                    fontSize = 13.sp,
                    color = TextSecondaryLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CockpitFeatureTilesRow(
    unreadMessagesCount: Int,
    isRadioActive: Boolean,
    isCarConnected: Boolean,
    onOpenMessages: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenCarAudio: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val messagesColor = if (unreadMessagesCount > 0) GemRose else TextSecondaryLight
        CockpitTileButton(
            icon = Icons.Default.Email,
            label = "Messages",
            tint = messagesColor,
            isHighlighted = unreadMessagesCount > 0,
            badgeCount = unreadMessagesCount,
            contentDescription = "Message Summaries",
            onClick = onOpenMessages,
            modifier = Modifier
                .weight(1f)
                .testTag("open_messages_button")
        )

        val radioColor = if (isRadioActive) GemCyan else TextSecondaryLight
        CockpitTileButton(
            icon = Icons.Default.Radio,
            label = "Radio",
            tint = radioColor,
            isHighlighted = isRadioActive,
            badgeCount = 0,
            contentDescription = "Radio & Podcasts",
            onClick = onOpenRadio,
            modifier = Modifier
                .weight(1f)
                .testTag("open_radio_button")
        )

        val carColor = if (isCarConnected) GemEmerald else TextSecondaryLight
        CockpitTileButton(
            icon = if (isCarConnected) Icons.Default.DirectionsCar else Icons.Default.Bluetooth,
            label = "Car BT",
            tint = carColor,
            isHighlighted = isCarConnected,
            badgeCount = 0,
            contentDescription = "Car Stereo Playback",
            onClick = onOpenCarAudio,
            modifier = Modifier
                .weight(1f)
                .testTag("open_car_audio_button")
        )

        CockpitTileButton(
            icon = Icons.Default.Settings,
            label = "Settings",
            tint = TextSecondaryLight,
            isHighlighted = false,
            badgeCount = 0,
            contentDescription = "Voice Settings",
            onClick = onOpenSettings,
            modifier = Modifier
                .weight(1f)
                .testTag("open_settings_button")
        )
    }
}

@Composable
private fun CockpitTileButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    isHighlighted: Boolean,
    badgeCount: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isHighlighted) tint.copy(alpha = 0.14f) else GemDarkSurface
    val borderColor = if (isHighlighted) tint.copy(alpha = 0.7f) else GemCardBorder

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier.height(68.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isHighlighted) tint else Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isHighlighted) tint else Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 8.dp)
                        .clip(CircleShape)
                        .background(GemRose)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomControlsBar(
    state: AssistantState,
    onOpenKeyboard: () -> Unit,
    onEndChat: () -> Unit,
    onMainActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Bottom-Left: Type / Keyboard fallback button (68dp)
        Surface(
            onClick = onOpenKeyboard,
            shape = RoundedCornerShape(16.dp),
            color = GemDarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GemCardBorder),
            modifier = Modifier
                .weight(0.95f)
                .height(68.dp)
                .testTag("keyboard_input_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Type Question",
                    tint = GemCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Type",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // 2. Wide Center Primary Voice Action Button (68dp)
        val (actionIcon, actionLabel, actionColor) = when (state) {
            AssistantState.LISTENING -> Triple(Icons.Default.Stop, "Stop Mic", GemEmerald)
            AssistantState.SPEAKING -> Triple(Icons.Default.Stop, "Interrupt", GemRose)
            AssistantState.PROCESSING -> Triple(Icons.Default.AutoAwesome, "Cancel", GemCyan)
            AssistantState.IDLE, AssistantState.ERROR -> Triple(Icons.Default.Mic, "Tap to Speak", GemCyan)
        }

        Surface(
            onClick = onMainActionClick,
            shape = RoundedCornerShape(18.dp),
            color = actionColor.copy(alpha = 0.18f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, actionColor),
            modifier = Modifier
                .weight(2.15f)
                .height(68.dp)
                .testTag("main_action_button")
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionLabel,
                    tint = actionColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = actionLabel,
                    color = actionColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }

        // 3. Bottom-Right Corner: Immediate Crimson Hang-Up / End button (68dp, no confirm popup)
        Surface(
            onClick = onEndChat,
            shape = RoundedCornerShape(16.dp),
            color = GemRose.copy(alpha = 0.24f),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GemRose),
            modifier = Modifier
                .weight(0.95f)
                .height(68.dp)
                .testTag("end_chat_bottom_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Conversation / Turn Off Mic",
                    tint = GemRose,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "End",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GemRose,
                    maxLines = 1
                )
            }
        }
    }
}
