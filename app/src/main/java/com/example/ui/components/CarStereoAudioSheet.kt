package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.AudioDeviceCategory
import com.example.media.CarAudioRoutingMode
import com.example.media.CarAudioStatus
import com.example.ui.theme.GemCardBorder
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemRose
import com.example.ui.theme.GemViolet
import com.example.ui.theme.TextSecondaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarStereoAudioSheet(
    status: CarAudioStatus,
    onSetRoutingMode: (CarAudioRoutingMode) -> Unit,
    onToggleAutoPause: (Boolean) -> Unit,
    onTestAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GemDarkSurface,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState())
                .testTag("car_stereo_audio_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (status.isConnected) GemEmerald.copy(alpha = 0.2f) else GemCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (status.isConnected) Icons.Default.DirectionsCar else Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = if (status.isConnected) GemEmerald else GemCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Car Stereo & Bluetooth",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "In-Vehicle Audio & Hands-Free Playback",
                            fontSize = 11.sp,
                            color = TextSecondaryLight
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (status.isConnected) GemEmerald.copy(alpha = 0.15f) else GemDarkCard)
                        .border(1.dp, if (status.isConnected) GemEmerald else GemCardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (status.isConnected) "Connected" else "Speaker",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (status.isConnected) GemEmerald else TextSecondaryLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Current Device Status Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (status.isConnected) GemEmerald.copy(alpha = 0.6f) else GemCardBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val deviceIcon = when (status.category) {
                        AudioDeviceCategory.CAR_STEREO -> Icons.Default.DirectionsCar
                        AudioDeviceCategory.BLUETOOTH_HEADSET -> Icons.Default.Headphones
                        AudioDeviceCategory.WIRED_AUX -> Icons.Default.Headphones
                        AudioDeviceCategory.PHONE_SPEAKER -> Icons.Default.PhoneAndroid
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (status.isConnected) GemEmerald.copy(alpha = 0.2f) else GemCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = if (status.isConnected) GemEmerald else GemCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = status.deviceName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (status.isConnected) "Voice questions, spoken answers, and public radio will play through this audio device." else "No Bluetooth car stereo connected. Connect in Android Bluetooth settings.",
                            fontSize = 12.sp,
                            color = TextSecondaryLight,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Audio Routing Mode Title
            Text(
                text = "Audio Routing Protocol",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GemCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Option 1: A2DP Media Stereo
            RoutingModeOption(
                title = "Bluetooth Stereo (A2DP Media)",
                subtitle = "High-fidelity stereo audio. Recommended for modern car stereos and Android Auto.",
                isSelected = status.routingMode == CarAudioRoutingMode.A2DP_MEDIA,
                onClick = { onSetRoutingMode(CarAudioRoutingMode.A2DP_MEDIA) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 2: Bluetooth SCO Hands-Free
            RoutingModeOption(
                title = "Hands-Free Protocol (Bluetooth SCO)",
                subtitle = "Telephone-grade audio channel. For older vehicles without Bluetooth media streaming support.",
                isSelected = status.routingMode == CarAudioRoutingMode.BLUETOOTH_SCO,
                onClick = { onSetRoutingMode(CarAudioRoutingMode.BLUETOOTH_SCO) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Option 3: Phone Internal Speaker
            RoutingModeOption(
                title = "Phone Speaker Only",
                subtitle = "Routes voice and radio directly to phone speaker even when Bluetooth is connected.",
                isSelected = status.routingMode == CarAudioRoutingMode.PHONE_INTERNAL,
                onClick = { onSetRoutingMode(CarAudioRoutingMode.PHONE_INTERNAL) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Auto-pause when car disconnects
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, GemCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Pause on Car Disconnect",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Immediately pauses radio and standby mode when you exit the car or turn off the ignition.",
                            fontSize = 11.sp,
                            color = TextSecondaryLight
                        )
                    }
                    Switch(
                        checked = status.autoPauseOnDisconnect,
                        onCheckedChange = { onToggleAutoPause(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GemCyan,
                            uncheckedThumbColor = TextSecondaryLight,
                            uncheckedTrackColor = GemDarkSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTestAudio,
                    colors = ButtonDefaults.buttonColors(containerColor = GemCyan),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_car_audio_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Car Audio", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GemCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_bluetooth_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = GemCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bluetooth Settings", color = GemCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RoutingModeOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GemCyan.copy(alpha = 0.12f) else GemDarkCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) GemCyan else GemCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (isSelected) GemCyan else TextSecondaryLight, CircleShape)
                    .background(if (isSelected) GemCyan else Color.Transparent)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) GemCyan else Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondaryLight,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
