package com.example.ui.components

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.messaging.MessagingChannel
import com.example.messaging.QuickContact
import com.example.messaging.QuickContactsRepository
import com.example.ui.theme.GemCardBorder
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemRose
import com.example.ui.theme.TextSecondaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickContactsSheet(
    contacts: List<QuickContact>,
    onAddContact: (QuickContact) -> Boolean,
    onDeleteContact: (String) -> Unit,
    onComposeToContact: (QuickContact) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val extracted = QuickContactsRepository.extractFromPickedUri(context, uri)
        if (extracted != null && onAddContact(extracted)) {
            statusNotice = "Saved ${extracted.displayName} locally (AES-256 encrypted)."
        } else {
            statusNotice = "Could not extract phone or email from selected contact. Use Add Manually instead."
        }
    }

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
                .padding(bottom = 32.dp)
                .testTag("quick_contacts_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GemCyan.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = null,
                        tint = GemCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Voice Quick Contacts",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Zero Contacts permission · Encrypted locally on device",
                        fontSize = 11.sp,
                        color = GemEmerald
                    )
                }
            }

            if (!statusNotice.isNullOrBlank()) {
                Text(
                    text = statusNotice.orEmpty(),
                    fontSize = 11.sp,
                    color = GemCyan
                )
            }

            // Pickers + Add Manually row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_PICK).apply {
                                type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                            }
                            pickContactLauncher.launch(intent)
                        } catch (_: Exception) {
                            statusNotice = "No contact picker available; use Add Manually."
                        }
                    },
                    border = BorderStroke(1.dp, GemCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pick_phone_contact_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = null,
                        tint = GemCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick Phone", color = GemCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_PICK).apply {
                                type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
                            }
                            pickContactLauncher.launch(intent)
                        } catch (_: Exception) {
                            statusNotice = "No email contact picker available; use Add Manually."
                        }
                    },
                    border = BorderStroke(1.dp, GemEmerald),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pick_email_contact_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactMail,
                        contentDescription = null,
                        tint = GemEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick Email", color = GemEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GemCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_manual_contact_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Contact Manually",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Add", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (contacts.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = BorderStroke(1.dp, GemCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No Quick Contacts saved yet. Pick from your phonebook or tap Add so you can say \"WhatsApp John Smith saying hello\" hands-free.",
                        fontSize = 12.sp,
                        color = TextSecondaryLight,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                            border = BorderStroke(1.dp, GemCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = contact.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(GemCyan.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = contact.preferredChannel.displayName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GemCyan
                                            )
                                        }
                                    }
                                    val detailLine = listOfNotNull(
                                        contact.spokenAlias.takeIf { it.isNotBlank() }?.let { "Alias: $it" },
                                        contact.phoneNumber.takeIf { it.isNotBlank() },
                                        contact.emailAddress.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    if (detailLine.isNotBlank()) {
                                        Text(
                                            text = detailLine,
                                            fontSize = 11.sp,
                                            color = TextSecondaryLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onComposeToContact(contact) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Compose to ${contact.displayName}",
                                            tint = GemEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteContact(contact.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete ${contact.displayName}",
                                            tint = GemRose,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddQuickContactDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newContact ->
                val saved = onAddContact(newContact)
                if (saved) {
                    statusNotice = "Saved ${newContact.displayName}."
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun AddQuickContactDialog(
    onDismiss: () -> Unit,
    onSave: (QuickContact) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var spokenAlias by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf(MessagingChannel.SMS) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = GemCyan,
        unfocusedBorderColor = GemCardBorder,
        focusedLabelColor = GemCyan,
        unfocusedLabelColor = TextSecondaryLight
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GemDarkSurface,
        title = {
            Text(
                text = "Add Voice Quick Contact",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name (e.g., John Smith)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = spokenAlias,
                    onValueChange = { spokenAlias = it },
                    label = { Text("Voice Alias (optional, e.g., Boss)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (for SMS/WhatsApp)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address (for Gmail)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text("Default App:", fontSize = 11.sp, color = TextSecondaryLight)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        MessagingChannel.SMS,
                        MessagingChannel.WHATSAPP,
                        MessagingChannel.TELEGRAM,
                        MessagingChannel.VIBER,
                        MessagingChannel.GMAIL
                    ).forEach { ch ->
                        val selected = channel == ch
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) GemCyan.copy(alpha = 0.22f) else GemDarkCard)
                                .border(1.dp, if (selected) GemCyan else GemCardBorder, RoundedCornerShape(8.dp))
                                .clickable { channel = ch }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = ch.displayName,
                                fontSize = 11.sp,
                                color = if (selected) GemCyan else Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        QuickContact(
                            displayName = displayName.trim(),
                            spokenAlias = spokenAlias.trim(),
                            phoneNumber = phone.trim(),
                            emailAddress = email.trim(),
                            preferredChannel = channel
                        )
                    )
                },
                enabled = displayName.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = GemCyan)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondaryLight)
            }
        }
    )
}
