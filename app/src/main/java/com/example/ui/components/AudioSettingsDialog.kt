package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemViolet
import com.example.ui.theme.TextSecondaryLight
import com.example.voice.VoiceGenderPreference

@Composable
fun AudioSettingsDialog(
    autoListen: Boolean,
    onAutoListenChange: (Boolean) -> Unit,
    voiceGender: VoiceGenderPreference,
    onVoiceGenderChange: (VoiceGenderPreference) -> Unit,
    speechRate: Float,
    onSpeechRateChange: (Float) -> Unit,
    pitch: Float,
    onPitchChange: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GemDarkSurface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice & Speech Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondaryLight
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("audio_settings_dialog"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Auto-listen toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hands-Free Dialogue",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Automatically listens for your follow-up question after speaking answers",
                            color = TextSecondaryLight,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoListen,
                        onCheckedChange = onAutoListenChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = GemCyan,
                            uncheckedThumbColor = TextSecondaryLight,
                            uncheckedTrackColor = GemDarkCard
                        ),
                        modifier = Modifier.testTag("auto_listen_switch")
                    )
                }

                // American Voice Selector (Male vs. Female)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = GemCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Spoken Voice (American English)",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isFemale = voiceGender == VoiceGenderPreference.FEMALE
                        OutlinedButton(
                            onClick = { onVoiceGenderChange(VoiceGenderPreference.FEMALE) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFemale) GemCyan.copy(alpha = 0.2f) else GemDarkCard
                            ),
                            border = BorderStroke(
                                width = if (isFemale) 1.5.dp else 1.dp,
                                color = if (isFemale) GemCyan else Color.White.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("voice_gender_female")
                        ) {
                            Text(
                                text = "Female",
                                color = if (isFemale) GemCyan else Color.White,
                                fontWeight = if (isFemale) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        val isMale = voiceGender == VoiceGenderPreference.MALE
                        OutlinedButton(
                            onClick = { onVoiceGenderChange(VoiceGenderPreference.MALE) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isMale) GemViolet.copy(alpha = 0.22f) else GemDarkCard
                            ),
                            border = BorderStroke(
                                width = if (isMale) 1.5.dp else 1.dp,
                                color = if (isMale) GemViolet else Color.White.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("voice_gender_male")
                        ) {
                            Text(
                                text = "Male",
                                color = if (isMale) GemViolet else Color.White,
                                fontWeight = if (isMale) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text(
                        text = "Tip: You can also say \"Switch to male voice\" or \"Switch to female voice\" anytime.",
                        color = TextSecondaryLight,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                // Speech Rate Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speech Rate",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = String.format("%.2fx", speechRate),
                            color = GemCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.75f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = GemCyan,
                            activeTrackColor = GemCyan,
                            inactiveTrackColor = GemDarkCard
                        ),
                        modifier = Modifier.testTag("speech_rate_slider")
                    )
                }

                // Pitch Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Voice Pitch",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = String.format("%.2fx", pitch),
                            color = GemViolet,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Slider(
                        value = pitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.8f..1.3f,
                        colors = SliderDefaults.colors(
                            thumbColor = GemViolet,
                            activeTrackColor = GemViolet,
                            inactiveTrackColor = GemDarkCard
                        ),
                        modifier = Modifier.testTag("speech_pitch_slider")
                    )
                }

                Button(
                    onClick = onTestVoice,
                    colors = ButtonDefaults.buttonColors(containerColor = GemDarkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("test_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = GemCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Test Spoken Voice", color = Color.White)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GemCyan),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("close_settings_button")
            ) {
                Text(text = "Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}
