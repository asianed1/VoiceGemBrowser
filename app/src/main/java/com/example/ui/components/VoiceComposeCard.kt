package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.messaging.ComposeStage
import com.example.messaging.MessagingChannel
import com.example.messaging.MessagingDraft
import com.example.messaging.MessagingMode
import com.example.messaging.QuickContact
import com.example.ui.theme.GemCardBorder
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemRose
import com.example.ui.theme.TextSecondaryLight

@Composable
fun VoiceComposeCard(
    draft: MessagingDraft,
    quickContacts: List<QuickContact>,
    isListening: Boolean = false,
    onChannelSelected: (MessagingChannel) -> Unit,
    onRecipientChanged: (name: String, phone: String, email: String) -> Unit,
    onQuickContactSelected: (QuickContact) -> Unit,
    onBodyChanged: (String) -> Unit,
    onDictateClick: () -> Unit,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSilentReply = draft.mode == MessagingMode.SILENT_REPLY && draft.hasDirectRemoteInput
    val accentColor = if (isSilentReply) GemEmerald else GemCyan
    val modeBadgeText = when {
        isSilentReply -> "IN-APP SILENT REPLY"
        draft.mode == MessagingMode.SILENT_REPLY -> "REPLY VIA ${draft.channel.displayName.uppercase()}"
        else -> "VOICE COMPOSE (0-GEMINI)"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GemDarkCard),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.7f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_compose_draft_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: badge + stage + close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = modeBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = when {
                            isListening -> "Listening…"
                            draft.stage == ComposeStage.NEEDS_RECIPIENT -> "Say or enter recipient"
                            draft.stage == ComposeStage.DICTATING_BODY -> "Speak or type message"
                            else -> "Say Send, Edit, or Cancel"
                        },
                        fontSize = 11.sp,
                        color = if (isListening) GemEmerald else TextSecondaryLight,
                        fontWeight = if (isListening) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("compose_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Discard Draft",
                        tint = TextSecondaryLight
                    )
                }
            }

            // Channel selector chips (for new compose)
            if (draft.mode == MessagingMode.NEW_MESSAGE) {
                val channels = listOf(
                    MessagingChannel.SMS,
                    MessagingChannel.WHATSAPP,
                    MessagingChannel.TELEGRAM,
                    MessagingChannel.VIBER,
                    MessagingChannel.GMAIL
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    channels.forEach { ch ->
                        val selected = draft.channel == ch
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) GemCyan.copy(alpha = 0.22f) else GemDarkSurface)
                                .border(
                                    1.dp,
                                    if (selected) GemCyan else GemCardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onChannelSelected(ch) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("channel_chip_${ch.name.lowercase()}")
                        ) {
                            Text(
                                text = ch.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) GemCyan else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Original message context snippet for replies
            if (draft.mode == MessagingMode.SILENT_REPLY && !draft.originalMessageSnippet.isNullOrBlank()) {
                Text(
                    text = "Replying to ${draft.recipientName}: \"${draft.originalMessageSnippet}\"",
                    fontSize = 11.sp,
                    color = TextSecondaryLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Recipient picker / input for New Message
            if (draft.mode == MessagingMode.NEW_MESSAGE) {
                if (quickContacts.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickContacts.take(8).forEach { qc ->
                            val isSelected = draft.recipientName.equals(qc.displayName, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) GemEmerald.copy(alpha = 0.2f) else GemDarkSurface)
                                    .border(
                                        1.dp,
                                        if (isSelected) GemEmerald else GemCardBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onQuickContactSelected(qc) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = qc.displayName,
                                    fontSize = 11.sp,
                                    color = if (isSelected) GemEmerald else Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                val isEmailMode = draft.channel == MessagingChannel.GMAIL
                if (draft.recipientName.isNotBlank() &&
                    ((isEmailMode && draft.recipientEmail.isNotBlank()) ||
                        (!isEmailMode && draft.recipientPhone.isNotBlank() && draft.recipientName != draft.recipientPhone))
                ) {
                    Text(
                        text = "Recipient: ${draft.recipientName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GemEmerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val currentTargetValue = if (isEmailMode) {
                    draft.recipientEmail
                } else {
                    draft.recipientPhone.ifBlank { draft.recipientName }
                }

                OutlinedTextField(
                    value = currentTargetValue,
                    onValueChange = { value ->
                        val cleaned = value.trim()
                        if (isEmailMode || cleaned.contains("@")) {
                            val preservedName = draft.recipientName.ifBlank { cleaned }
                            onRecipientChanged(preservedName, draft.recipientPhone, cleaned)
                        } else {
                            onRecipientChanged(cleaned, cleaned, draft.recipientEmail)
                        }
                    },
                    label = {
                        Text(
                            text = if (isEmailMode) "To Email (e.g., john.smith@gmail.com)" else "To Phone or Name",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = GemCardBorder,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = TextSecondaryLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_recipient_input")
                )
            }

            // Message Body field (always editable if speech needs minor correction)
            OutlinedTextField(
                value = draft.messageBody,
                onValueChange = onBodyChanged,
                label = { Text("Message (${draft.channel.displayName})", fontSize = 11.sp) },
                placeholder = { Text("Speak or type your message…", fontSize = 12.sp, color = TextSecondaryLight) },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = GemCardBorder,
                    focusedLabelColor = accentColor,
                    unfocusedLabelColor = TextSecondaryLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compose_body_input")
            )

            // Tactile Actions: Dictate | Send / Open App | Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val micTint = if (isListening) GemEmerald else GemCyan
                OutlinedButton(
                    onClick = onDictateClick,
                    border = BorderStroke(1.dp, micTint),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("compose_dictate_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isListening) "Listening" else "Dictate Message",
                        tint = micTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isListening) "Listening" else "Speak",
                        color = micTint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onSendClick,
                    enabled = draft.messageBody.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        disabledContainerColor = GemCardBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("compose_confirm_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSilentReply) "Confirm & Send" else "Open ${draft.channel.displayName}",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onCancelClick,
                    border = BorderStroke(1.dp, GemRose.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("compose_cancel_button")
                ) {
                    Text("Cancel", color = GemRose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
