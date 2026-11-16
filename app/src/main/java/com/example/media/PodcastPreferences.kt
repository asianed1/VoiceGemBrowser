package com.example.media

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PodcastPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("voicegem_podcasts_prefs", Context.MODE_PRIVATE)

    private val _preferredPodcasts = MutableStateFlow<List<RadioStation>>(loadStations())
    val preferredPodcasts: StateFlow<List<RadioStation>> = _preferredPodcasts.asStateFlow()

    private fun loadStations(): List<RadioStation> {
        val jsonString = prefs.getString(KEY_PODCAST_SLOTS, null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<RadioStation>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val slotNum = obj.optInt("slotNumber", i + 1)
                    val rawUrl = obj.optString("streamUrl", "")
                    val isBrokenLegacySlot6 = slotNum == 6 &&
                        rawUrl == "https://classicalmusic.streamguys1.com/classical-mp3"
                    if (isBrokenLegacySlot6) {
                        list.add(DefaultRadioStations.DEFAULT_SIX_PODCASTS[5])
                    } else {
                        list.add(
                            RadioStation(
                                id = obj.optString("id", "podcast_slot_$slotNum"),
                                name = obj.optString("name", "Podcast Slot $slotNum"),
                                genre = obj.optString("genre", "Podcast"),
                                streamUrl = rawUrl,
                                iconEmoji = obj.optString("iconEmoji", "🎙️"),
                                slotNumber = slotNum,
                                isCustomizable = true
                            )
                        )
                    }
                }
                if (list.size == 6) {
                    return list
                }
            } catch (e: Exception) {
                // fallback to defaults
            }
        }
        return DefaultRadioStations.DEFAULT_SIX_PODCASTS
    }

    private fun saveStations(stations: List<RadioStation>) {
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
        _preferredPodcasts.value = stations
    }

    fun updatePodcastSlot(
        slotNumber: Int,
        name: String,
        genre: String,
        streamUrl: String,
        iconEmoji: String
    ) {
        val current = _preferredPodcasts.value.toMutableList()
        val index = (slotNumber - 1).coerceIn(0, current.size - 1)
        val updated = RadioStation(
            id = "podcast_slot_$slotNumber",
            name = name.ifBlank { "Podcast Slot $slotNumber" },
            genre = genre.ifBlank { "Podcast" },
            streamUrl = streamUrl.trim(),
            iconEmoji = iconEmoji.ifBlank { "🎙️" },
            slotNumber = slotNumber,
            isCustomizable = true
        )
        current[index] = updated
        saveStations(current)
    }

    fun resetSlotToDefault(slotNumber: Int) {
        val defaults = DefaultRadioStations.DEFAULT_SIX_PODCASTS
        val index = (slotNumber - 1).coerceIn(0, defaults.size - 1)
        val current = _preferredPodcasts.value.toMutableList()
        current[index] = defaults[index]
        saveStations(current)
    }

    fun resetAllToDefaults() {
        saveStations(DefaultRadioStations.DEFAULT_SIX_PODCASTS)
    }

    companion object {
        private const val KEY_PODCAST_SLOTS = "pref_podcast_slots_json"
    }
}
