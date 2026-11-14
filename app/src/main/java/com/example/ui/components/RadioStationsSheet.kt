package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.DefaultRadioStations
import com.example.media.RadioStation
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
fun RadioStationsSheet(
    isPlaying: Boolean,
    isLoading: Boolean,
    currentStation: RadioStation?,
    isDucked: Boolean,
    preferredPodcasts: List<RadioStation>,
    onSelectStation: (RadioStation) -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopRadio: () -> Unit,
    onUpdatePodcastSlot: (slotNumber: Int, name: String, genre: String, streamUrl: String, iconEmoji: String) -> Unit,
    onResetPodcastSlot: (slotNumber: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 6 Preferred Podcasts, 1: Presets (NPR/BBC)
    var editingSlotStation by remember { mutableStateOf<RadioStation?>(null) }

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
                .testTag("radio_stations_sheet")
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GemCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = GemCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Podcasts & Live Radio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "6 Preferred Podcast sources + NPR & BBC Presets",
                            fontSize = 12.sp,
                            color = TextSecondaryLight
                        )
                    }
                }

                if (isPlaying) {
                    IconButton(
                        onClick = onStopRadio,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GemRose.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Audio",
                            tint = GemRose,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Currently Playing Card
            if (currentStation != null && (isPlaying || isLoading)) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentStation.iconEmoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentStation.slotNumber != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GemCyan.copy(alpha = 0.25f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Slot ${currentStation.slotNumber}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GemCyan
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = currentStation.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isDucked) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                            contentDescription = null,
                                            tint = GemViolet,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Audio Ducked (Voice Active)",
                                            fontSize = 11.sp,
                                            color = GemViolet,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "Streaming Live • ${currentStation.genre}",
                                            fontSize = 11.sp,
                                            color = GemEmerald
                                        )
                                    }
                                }
                            }
                        }

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = GemCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = onTogglePlayPause) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = GemCyan
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Tabs: 6 Preferred Podcasts vs Presets (NPR/BBC)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = GemDarkCard,
                contentColor = GemCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = GemCyan,
                        height = 2.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "6 Preferred Podcasts",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (selectedTab == 0) GemCyan else TextSecondaryLight
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Presets (NPR & BBC)",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (selectedTab == 1) GemCyan else TextSecondaryLight
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // 6 Preferred Podcasts List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your 6 Customizable Podcast Sources:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Tap ✏️ to customize",
                        fontSize = 11.sp,
                        color = GemCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(preferredPodcasts, key = { it.id }) { station ->
                        val isCurrent = currentStation?.id == station.id
                        PodcastSlotItemCard(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isLoading = isCurrent && isLoading,
                            onClick = { onSelectStation(station) },
                            onEdit = { editingSlotStation = station }
                        )
                    }
                }
            } else {
                // Preset Radio Stations (NPR, BBC, Jazz, Ambient)
                Text(
                    text = "Permanent Preset Stations:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DefaultRadioStations.PRESET_STATIONS, key = { it.id }) { station ->
                        val isCurrent = currentStation?.id == station.id
                        PresetStationItemCard(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            isLoading = isCurrent && isLoading,
                            onClick = { onSelectStation(station) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GemDarkCard)
                    .padding(10.dp)
            ) {
                Text(
                    text = "Tip: Say 'Play NPR', 'Play BBC', or 'Play Podcast 1' through 'Play Podcast 6'. Music/podcast automatically ducks when you converse with Gemini.",
                    fontSize = 12.sp,
                    color = GemCyan,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // Dialog to customize podcast slot
    editingSlotStation?.let { station ->
        val slotNum = station.slotNumber ?: 1
        EditPodcastSlotDialog(
            slotNumber = slotNum,
            initialName = station.name,
            initialGenre = station.genre,
            initialStreamUrl = station.streamUrl,
            initialEmoji = station.iconEmoji,
            onSave = { name, genre, url, emoji ->
                onUpdatePodcastSlot(slotNum, name, genre, url, emoji)
                editingSlotStation = null
            },
            onResetToDefault = {
                onResetPodcastSlot(slotNum)
                editingSlotStation = null
            },
            onDismiss = { editingSlotStation = null }
        )
    }
}

@Composable
private fun PodcastSlotItemCard(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) GemCyan.copy(alpha = 0.12f) else GemDarkCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) GemCyan else GemCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GemDarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(station.iconEmoji, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GemCyan.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Slot ${station.slotNumber ?: 1}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GemCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = station.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${station.genre} • ${if (station.streamUrl.isNotBlank()) "Configured" else "Empty URL"}",
                        fontSize = 11.sp,
                        color = TextSecondaryLight,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Slot ${station.slotNumber}",
                        tint = GemCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = GemCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (isCurrent) GemCyan else TextSecondaryLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetStationItemCard(
    station: RadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) GemCyan.copy(alpha = 0.12f) else GemDarkCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) GemCyan else GemCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(station.iconEmoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = station.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${station.genre} • Preset",
                        fontSize = 11.sp,
                        color = TextSecondaryLight
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = GemCyan,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent) GemCyan else TextSecondaryLight,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun EditPodcastSlotDialog(
    slotNumber: Int,
    initialName: String,
    initialGenre: String,
    initialStreamUrl: String,
    initialEmoji: String,
    onSave: (name: String, genre: String, url: String, emoji: String) -> Unit,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var genre by remember { mutableStateOf(initialGenre) }
    var streamUrl by remember { mutableStateOf(initialStreamUrl) }
    var selectedEmoji by remember { mutableStateOf(initialEmoji) }

    val emojis = listOf("🎙️", "📰", "🌍", "💻", "🔬", "📈", "🎧", "☕", "🎷", "🎻")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GemDarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Customize Podcast Slot $slotNumber",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configure your preferred podcast feed or audio stream URL for Slot $slotNumber.",
                    fontSize = 12.sp,
                    color = TextSecondaryLight
                )

                // Emoji Picker Row
                Text(
                    text = "Icon Emoji:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.take(5).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) GemCyan.copy(alpha = 0.3f) else GemDarkCard)
                                .border(1.dp, if (selectedEmoji == emoji) GemCyan else Color.Transparent, CircleShape)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.drop(5).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) GemCyan.copy(alpha = 0.3f) else GemDarkCard)
                                .border(1.dp, if (selectedEmoji == emoji) GemCyan else Color.Transparent, CircleShape)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Podcast / Feed Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GemCyan,
                        unfocusedBorderColor = GemCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = GemCyan,
                        unfocusedLabelColor = TextSecondaryLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Category / Genre (e.g. Tech, News)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GemCyan,
                        unfocusedBorderColor = GemCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = GemCyan,
                        unfocusedLabelColor = TextSecondaryLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Podcast RSS Feed or Stream URL") },
                    placeholder = { Text("https://example.com/feed.xml or direct .mp3") },
                    supportingText = { Text("Supports RSS feeds (.xml), direct MP3s, and radio streams", fontSize = 10.sp, color = TextSecondaryLight) },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GemCyan,
                        unfocusedBorderColor = GemCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = GemCyan,
                        unfocusedLabelColor = TextSecondaryLight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, genre, streamUrl, selectedEmoji)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GemCyan)
            ) {
                Text("Save Slot $slotNumber", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onResetToDefault) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = TextSecondaryLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", color = TextSecondaryLight)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondaryLight)
                }
            }
        }
    )
}
