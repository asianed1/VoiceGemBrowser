package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notification.CapturedMessage
import com.example.notification.NotificationQueueManager
import com.example.ui.theme.GemCardBorder
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemRose
import com.example.ui.theme.GemViolet
import com.example.ui.theme.TextSecondaryLight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessagesSummarySheet(
    messages: List<CapturedMessage>,
    filteredMessages: List<CapturedMessage> = emptyList(),
    spamFilterEnabled: Boolean = true,
    mutedSenders: Set<String> = emptySet(),
    onSummarizeAndSpeak: () -> Unit,
    onClearMessages: () -> Unit,
    onMuteSender: (String) -> Unit = {},
    onUnmuteSender: (String) -> Unit = {},
    onToggleSpamFilter: (Boolean) -> Unit = {},
    onRestoreFilteredMessage: (String) -> Unit = {},
    onClearFilteredMessages: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFilteredSection by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .testTag("messages_summary_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GemViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailUnread,
                            contentDescription = null,
                            tint = GemViolet,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Message Inbox & Summarizer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${messages.size} unread • 100% on-device (never sent to cloud)",
                            fontSize = 11.sp,
                            color = GemEmerald
                        )
                    }
                }

                if (messages.isNotEmpty()) {
                    IconButton(onClick = onClearMessages) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Queue",
                            tint = TextSecondaryLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // On-Device Privacy & Spam Guard Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GemDarkCard)
                    .border(1.dp, GemEmerald.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = GemEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "On-Device Spam & Promo Filter",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "OTP / 2FA codes are always blocked silently",
                            fontSize = 10.sp,
                            color = GemEmerald
                        )
                    }
                }
                Switch(
                    checked = spamFilterEnabled,
                    onCheckedChange = onToggleSpamFilter,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = GemEmerald,
                        uncheckedTrackColor = GemDarkSurface
                    ),
                    modifier = Modifier.testTag("spam_filter_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button: Read & Digest Locally
            if (messages.isNotEmpty()) {
                Button(
                    onClick = onSummarizeAndSpeak,
                    colors = ButtonDefaults.buttonColors(containerColor = GemViolet),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("summarize_speak_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Read Local Message Digest Aloud",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (messages.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = GemCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No unread messages right now",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Messages from WhatsApp, Gmail, Telegram, Viber, or SMS appear here. Promos & OTP codes stay out of your voice digest.",
                            fontSize = 12.sp,
                            color = TextSecondaryLight,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                NotificationQueueManager.addMessage(
                                    CapturedMessage(
                                        packageName = "com.whatsapp",
                                        appName = "WhatsApp",
                                        sender = "Sarah Connor",
                                        text = "Hey! Are you still joining dinner tonight at 7:30?",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                NotificationQueueManager.addMessage(
                                    CapturedMessage(
                                        packageName = "com.google.android.gm",
                                        appName = "Gmail",
                                        sender = "David Miller",
                                        text = "Project Proposal: Updated Q4 contract attached for review.",
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GemCyan.copy(alpha = 0.2f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GemCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Simulate Test Messages",
                                color = GemCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 230.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageItemCard(
                            message = msg,
                            onMuteSender = { onMuteSender(msg.sender) }
                        )
                    }
                }
            }

            // Collapsible Filtered Spam & Muted Senders section
            if (filteredMessages.isNotEmpty() || mutedSenders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GemDarkCard)
                        .border(1.dp, GemCardBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFilteredSection = !showFilteredSection }
                            .testTag("filtered_spam_toggle"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Filtered Spam (${filteredMessages.size}) & Muted (${mutedSenders.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (showFilteredSection) "Tap to collapse" else "Tap to inspect or unmute senders",
                                fontSize = 11.sp,
                                color = TextSecondaryLight
                            )
                        }
                        Icon(
                            imageVector = if (showFilteredSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showFilteredSection) "Collapse" else "Expand",
                            tint = GemCyan
                        )
                    }

                    if (showFilteredSection) {
                        Spacer(modifier = Modifier.height(10.dp))

                        if (mutedSenders.isNotEmpty()) {
                            Text(
                                text = "Muted Senders (tap × to unmute):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondaryLight
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                mutedSenders.forEach { sender ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(GemRose.copy(alpha = 0.16f))
                                            .border(1.dp, GemRose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            .clickable { onUnmuteSender(sender) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = sender,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Unmute $sender",
                                            tint = GemRose,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        if (filteredMessages.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Filtered Notifications:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryLight
                                )
                                TextButton(onClick = onClearFilteredMessages) {
                                    Text("Clear Filtered", color = GemRose, fontSize = 11.sp)
                                }
                            }
                            filteredMessages.take(8).forEach { spamMsg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(GemDarkSurface)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${spamMsg.sender} • ${spamMsg.filterReason ?: "Filtered"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GemRose,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = spamMsg.text,
                                            fontSize = 11.sp,
                                            color = TextSecondaryLight,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(onClick = { onRestoreFilteredMessage(spamMsg.id) }) {
                                        Text("Allow", color = GemCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notification Access Settings link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GemDarkCard)
                    .border(1.dp, GemCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = TextSecondaryLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android Notification Access",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Text("Open Settings", color = GemCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MessageItemCard(
    message: CapturedMessage,
    onMuteSender: () -> Unit
) {
    val badgeColor = when {
        message.appName.contains("whatsapp", ignoreCase = true) -> Color(0xFF25D366)
        message.appName.contains("gmail", ignoreCase = true) -> Color(0xFFEA4335)
        message.appName.contains("telegram", ignoreCase = true) -> Color(0xFF0088CC)
        message.appName.contains("viber", ignoreCase = true) -> Color(0xFF7360F2)
        else -> GemCyan
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GemDarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, GemCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = message.appName,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GemRose.copy(alpha = 0.14f))
                        .clickable { onMuteSender() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = "Mute ${message.sender}",
                        tint = GemRose,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Mute",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GemRose
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.text,
                fontSize = 12.sp,
                color = TextSecondaryLight,
                lineHeight = 16.sp
            )
        }
    }
}
