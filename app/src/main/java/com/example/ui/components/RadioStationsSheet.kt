package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.DefaultRadioStations
import com.example.media.PodcastEpisode
import com.example.media.PodcastSearchResult
import com.example.media.PodcastSearchService
import com.example.media.RadioStation
import com.example.ui.theme.GemCardBorder
import com.example.ui.theme.GemCyan
import com.example.ui.theme.GemDarkCard
import com.example.ui.theme.GemDarkSurface
import com.example.ui.theme.GemEmerald
import com.example.ui.theme.GemRose
import com.example.ui.theme.GemViolet
import com.example.ui.theme.TextSecondaryLight
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RadioStationsSheet(
    isPlaying: Boolean,
    isLoading: Boolean,
    currentStation: RadioStation?,
    isDucked: Boolean,
    preferredPodcasts: List<RadioStation>,
    episodes: List<PodcastEpisode> = emptyList(),
    currentEpisodeIndex: Int = 0,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    sleepTimerRemainingSec: Int? = null,
    sleepAtEndOfEpisode: Boolean = false,
    onSelectStation: (RadioStation) -> Unit,
    onSelectEpisode: (Int) -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onSetSleepTimerMinutes: (Int) -> Unit = {},
    onSetSleepAtEndOfEpisode: () -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onStopRadio: () -> Unit,
    onUpdatePodcastSlot: (slotNumber: Int, name: String, genre: String, streamUrl: String, iconEmoji: String) -> Boolean,
    onResetPodcastSlot: (slotNumber: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 8 Preferred Podcasts, 1: Presets (NPR/BBC)
    var editingSlotStation by remember { mutableStateOf<RadioStation?>(null) }
    var showEpisodePicker by remember { mutableStateOf(false) }

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
                            text = "8 Preferred Podcast sources + NPR & BBC Presets",
                            fontSize = 12.sp,
                            color = TextSecondaryLight
                        )
                    }
                }

                if (isPlaying || currentStation != null) {
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

            // Currently Playing Card with Skip, Scrubber, Episode Picker & Sleep Timer
            if (currentStation != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = GemDarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GemCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
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
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    val currentEp = episodes.getOrNull(currentEpisodeIndex)
                                    if (episodes.size > 1 && currentEp != null) {
                                        Text(
                                            text = currentEp.title,
                                            fontSize = 11.sp,
                                            color = GemCyan,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                                text = when {
                                                    isLoading -> "Buffering stream…"
                                                    isPlaying -> "Playing • ${currentStation.genre}"
                                                    else -> "Paused • ${currentStation.genre}"
                                                },
                                                fontSize = 11.sp,
                                                color = if (isPlaying) GemEmerald else TextSecondaryLight
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onSeekBackward,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FastRewind,
                                            contentDescription = "Back 15 seconds",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onTogglePlayPause,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = GemCyan,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onSeekForward,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = "Forward 30 seconds",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Progress Scrubber for on-demand podcast episodes (hidden for infinite live radio)
                        if (durationMs > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = currentPositionMs.coerceIn(0L, durationMs).toFloat(),
                                onValueChange = { onSeekTo(it.toLong()) },
                                valueRange = 0f..durationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = GemCyan,
                                    activeTrackColor = GemCyan,
                                    inactiveTrackColor = GemCardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDurationMs(currentPositionMs),
                                    fontSize = 10.sp,
                                    color = TextSecondaryLight
                                )
                                Text(
                                    text = formatDurationMs(durationMs),
                                    fontSize = 10.sp,
                                    color = TextSecondaryLight
                                )
                            }
                        }

                        // Episode picker toggle & Sleep timer quick bar
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (episodes.size > 1) {
                                SmallControlChip(
                                    label = "Episodes (${episodes.size})",
                                    selected = showEpisodePicker,
                                    onClick = { showEpisodePicker = !showEpisodePicker }
                                )
                            }

                            if (sleepTimerRemainingSec != null) {
                                val mins = sleepTimerRemainingSec / 60
                                val secs = sleepTimerRemainingSec % 60
                                SmallControlChip(
                                    label = "🌙 %02d:%02d ✕".format(mins, secs),
                                    selected = true,
                                    onClick = onCancelSleepTimer
                                )
                            } else if (sleepAtEndOfEpisode) {
                                SmallControlChip(
                                    label = "🌙 End of Ep ✕",
                                    selected = true,
                                    onClick = onCancelSleepTimer
                                )
                            } else {
                                SmallControlChip("🌙 15m", false) { onSetSleepTimerMinutes(15) }
                                SmallControlChip("🌙 30m", false) { onSetSleepTimerMinutes(30) }
                                SmallControlChip("🌙 60m", false) { onSetSleepTimerMinutes(60) }
                                if (durationMs > 0L || episodes.size > 1) {
                                    SmallControlChip("🌙 End of Ep", false) { onSetSleepAtEndOfEpisode() }
                                }
                            }
                        }

                        if (showEpisodePicker && episodes.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GemDarkSurface)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Recent Episodes:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GemCyan
                                )
                                episodes.forEachIndexed { idx, ep ->
                                    val isCurrentEp = idx == currentEpisodeIndex
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCurrentEp) GemCyan.copy(alpha = 0.15f) else Color.Transparent)
                                            .clickable {
                                                onSelectEpisode(idx)
                                                showEpisodePicker = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ep.title,
                                                fontSize = 11.sp,
                                                fontWeight = if (isCurrentEp) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrentEp) GemCyan else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (ep.pubDate.isNotBlank()) {
                                                Text(
                                                    text = ep.pubDate,
                                                    fontSize = 10.sp,
                                                    color = TextSecondaryLight
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isCurrentEp && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play episode",
                                            tint = if (isCurrentEp) GemCyan else TextSecondaryLight,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Tabs: 8 Preferred Podcasts vs Presets (NPR/BBC)
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
                            text = "8 Preferred Podcasts",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your 8 Customizable Podcast Sources:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Tap ✏️ to search/edit",
                        fontSize = 11.sp,
                        color = GemCyan
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 270.dp),
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
                        .heightIn(max = 270.dp),
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
                    text = "Tip: Say 'Play NPR', 'Play BBC', 'Play Podcast 1' through 'Play Podcast 8', 'Forward 30 seconds', 'Rewind 15 seconds', 'Next episode', or 'Sleep timer 30 minutes'.",
                    fontSize = 11.sp,
                    color = GemCyan,
                    lineHeight = 15.sp
                )
            }
        }
    }

    editingSlotStation?.let { station ->
        val slotNum = station.slotNumber ?: 1
        EditPodcastSlotDialog(
            slotNumber = slotNum,
            initialName = station.name,
            initialGenre = station.genre,
            initialStreamUrl = station.streamUrl,
            initialEmoji = station.iconEmoji,
            onSave = { name, genre, url, emoji ->
                val ok = onUpdatePodcastSlot(slotNum, name, genre, url, emoji)
                if (ok) {
                    editingSlotStation = null
                }
                ok
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
private fun SmallControlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) GemCyan.copy(alpha = 0.22f) else GemDarkSurface)
            .border(
                1.dp,
                if (selected) GemCyan else GemCardBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) GemCyan else TextSecondaryLight
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${station.genre} • ${if (station.streamUrl.isNotBlank()) "Configured" else "Empty URL"}",
                        fontSize = 11.sp,
                        color = TextSecondaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Slot ${station.slotNumber}",
                        tint = GemCyan.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
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
                        modifier = Modifier.size(36.dp)
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
    onSave: (name: String, genre: String, url: String, emoji: String) -> Boolean,
    onResetToDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var genre by remember { mutableStateOf(initialGenre) }
    var streamUrl by remember { mutableStateOf(initialStreamUrl) }
    var selectedEmoji by remember { mutableStateOf(initialEmoji) }
    var urlError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<PodcastSearchResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val emojis = listOf("🎙️", "📰", "🌍", "💻", "🔬", "📈", "🎧", "☕", "💵", "🧬")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GemDarkSurface,
        title = {
            Text(
                text = "Customize Podcast Slot $slotNumber",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Free Directory Search Section (No Gemini Quota Used)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GemDarkCard)
                        .border(1.dp, GemCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Search Podcasts (Free Directory • No AI Quota)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GemCyan
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search show name…", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GemCyan,
                                unfocusedBorderColor = GemCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (searchQuery.trim().length < 2) return@Button
                                isSearching = true
                                searchError = null
                                scope.launch {
                                    val result = PodcastSearchService.searchPodcasts(searchQuery)
                                    isSearching = false
                                    result.fold(
                                        onSuccess = { list ->
                                            searchResults = list
                                            if (list.isEmpty()) {
                                                searchError = "No HTTPS podcast feeds found for '$searchQuery'."
                                            }
                                        },
                                        onFailure = {
                                            searchError = "Search unavailable right now."
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GemCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.Black,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Podcasts",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    searchError?.let { err ->
                        Text(text = err, fontSize = 11.sp, color = GemRose)
                    }

                    if (searchResults.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            searchResults.take(5).forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GemDarkSurface)
                                        .clickable {
                                            name = result.title
                                            genre = result.genre
                                            streamUrl = result.feedUrl
                                            urlError = null
                                            searchResults = emptyList()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${result.author} • ${result.genre}",
                                            fontSize = 10.sp,
                                            color = TextSecondaryLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "Select",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GemCyan
                                    )
                                }
                            }
                        }
                    }
                }

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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) GemCyan.copy(alpha = 0.3f) else GemDarkCard)
                                .border(1.dp, if (selectedEmoji == emoji) GemCyan else Color.Transparent, CircleShape)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 16.sp)
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
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) GemCyan.copy(alpha = 0.3f) else GemDarkCard)
                                .border(1.dp, if (selectedEmoji == emoji) GemCyan else Color.Transparent, CircleShape)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 16.sp)
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
                    onValueChange = {
                        streamUrl = it
                        urlError = null
                    },
                    isError = urlError != null,
                    label = { Text("HTTPS RSS Feed or Audio Stream URL") },
                    placeholder = { Text("https://example.com/feed.xml or direct .mp3") },
                    supportingText = {
                        Text(
                            text = urlError ?: "Supports HTTPS RSS feeds (.xml), direct MP3s, and live streams",
                            fontSize = 10.sp,
                            color = if (urlError != null) GemRose else TextSecondaryLight
                        )
                    },
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
                    val saved = onSave(name, genre, streamUrl, selectedEmoji)
                    if (!saved) {
                        urlError = "Enter a valid public https:// podcast RSS or stream URL."
                    }
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

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0) {
        "%d:%02d:%02d".format(hrs, mins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}
