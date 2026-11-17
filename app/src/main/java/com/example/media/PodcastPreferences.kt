package com.example.media

import android.content.Context
import android.content.SharedPreferences
import com.example.security.UrlSecurityGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PodcastPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("voicegem_podcasts_prefs", Context.MODE_PRIVATE)

    private val _preferredPodcasts = MutableStateFlow<List<RadioStation>>(loadStations())
    val preferredPodcasts: StateFlow<List<RadioStation>> = _preferredPodcasts.asStateFlow()

    private fun loadStations(): List<RadioStation> {
        val defaults = DefaultRadioStations.DEFAULT_EIGHT_PODCASTS
        val jsonString = prefs.getString(KEY_PODCAST_SLOTS, null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<RadioStation>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val slotNum = obj.optInt("slotNumber", i + 1)
                    val defaultForSlot = defaults.getOrNull(slotNum - 1) ?: defaults[0]
                    val rawUrl = obj.optString("streamUrl", "")
                    val isBrokenLegacySlot6 = slotNum == 6 &&
                        rawUrl == "https://classicalmusic.streamguys1.com/classical-mp3"
                    val safeUrl = UrlSecurityGuard.sanitizeHttpsUrlOrNull(rawUrl)

                    if (isBrokenLegacySlot6 || safeUrl == null) {
                        list.add(defaultForSlot)
                    } else {
                        list.add(
                            RadioStation(
                                id = obj.optString("id", "podcast_slot_$slotNum"),
                                name = obj.optString("name", defaultForSlot.name),
                                genre = obj.optString("genre", defaultForSlot.genre),
                                streamUrl = safeUrl,
                                iconEmoji = obj.optString("iconEmoji", defaultForSlot.iconEmoji),
                                slotNumber = slotNum,
                                isCustomizable = true
                            )
                        )
                    }
                }
                // Seamless v10 -> v11 migration: preserve user's 6 slots & append new Slots 7 & 8
                if (list.size == 6) {
                    val migrated = list + listOf(defaults[6], defaults[7])
                    persistStationsJson(migrated)
                    return migrated
                }
                if (list.size == 8) {
                    return list
                }
            } catch (_: Exception) {
                // fallback to defaults
            }
        }
        return defaults
    }

    private fun persistStationsJson(stations: List<RadioStation>) {
        val jsonArray = JSONArray()
        stations.forEach { station ->
            val obj = JSONObject().apply {
                put("id", station.id)
                put("name", station.name)
                put("genre", station.genre)
                put("streamUrl", station.streamUrl)
                put("iconEmoji", station.iconEmoji)
                put("slotNumber", station.slotNumber ?: 1)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_PODCAST_SLOTS, jsonArray.toString()).apply()
    }

    private fun saveStations(stations: List<RadioStation>) {
        persistStationsJson(stations)
        _preferredPodcasts.value = stations
    }

    fun updatePodcastSlot(
        slotNumber: Int,
        name: String,
        genre: String,
        streamUrl: String,
        iconEmoji: String
    ): Boolean {
        val safeUrl = UrlSecurityGuard.sanitizeHttpsUrlOrNull(streamUrl) ?: return false
        val current = _preferredPodcasts.value.toMutableList()
        val index = (slotNumber - 1).coerceIn(0, current.size - 1)
        val updated = RadioStation(
            id = "podcast_slot_$slotNumber",
            name = name.trim().ifBlank { "Podcast Slot $slotNumber" },
            genre = genre.trim().ifBlank { "Podcast" },
            streamUrl = safeUrl,
            iconEmoji = iconEmoji.ifBlank { "🎙️" },
            slotNumber = slotNumber,
            isCustomizable = true
        )
        current[index] = updated
        saveStations(current)
        return true
    }

    fun resetSlotToDefault(slotNumber: Int) {
        val defaults = DefaultRadioStations.DEFAULT_EIGHT_PODCASTS
        val index = (slotNumber - 1).coerceIn(0, defaults.size - 1)
        val current = _preferredPodcasts.value.toMutableList()
        if (index < current.size) {
            current[index] = defaults[index]
            saveStations(current)
        }
    }

    fun resetAllToDefaults() {
        saveStations(DefaultRadioStations.DEFAULT_EIGHT_PODCASTS)
    }

    fun savePlaybackPosition(stationId: String, audioUrl: String, positionMs: Long) {
        if (stationId.isBlank() || audioUrl.isBlank()) return
        val key = resumeKey(stationId, audioUrl)
        if (positionMs < 3_000L) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putLong(key, positionMs).apply()
        }
    }

    fun getSavedPlaybackPosition(stationId: String, audioUrl: String): Long {
        if (stationId.isBlank() || audioUrl.isBlank()) return 0L
        return prefs.getLong(resumeKey(stationId, audioUrl), 0L).coerceAtLeast(0L)
    }

    fun clearPlaybackPosition(stationId: String, audioUrl: String) {
        if (stationId.isBlank() || audioUrl.isBlank()) return
        prefs.edit().remove(resumeKey(stationId, audioUrl)).apply()
    }

    private fun resumeKey(stationId: String, audioUrl: String): String {
        return "${KEY_RESUME_PREFIX}${stationId}_${audioUrl.hashCode()}"
    }

    companion object {
        private const val KEY_PODCAST_SLOTS = "pref_podcast_slots_json"
        private const val KEY_RESUME_PREFIX = "resume_pos_ms_"
    }
}
