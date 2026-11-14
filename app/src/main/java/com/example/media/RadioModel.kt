package com.example.media

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val streamUrl: String,
    val iconEmoji: String,
    val slotNumber: Int? = null,
    val isCustomizable: Boolean = false
)

object DefaultRadioStations {
    // Permanent Built-in Presets (NPR, BBC, and Music Streams)
    val PRESET_STATIONS = listOf(
        RadioStation(
            id = "npr_live",
            name = "NPR News & Talk",
            genre = "Public Radio / News",
            streamUrl = "https://npr-ice.streamguys1.com/live.mp3",
            iconEmoji = "🎙️",
            isCustomizable = false
        ),
        RadioStation(
            id = "bbc_world",
            name = "BBC World Service",
            genre = "Global News / Talk",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            iconEmoji = "🌍",
            isCustomizable = false
        ),
        RadioStation(
            id = "smooth_jazz",
            name = "Classic Jazz 24/7",
            genre = "Smooth Jazz",
            streamUrl = "https://icecast.walmradio.com:8443/jazz",
            iconEmoji = "🎷",
            isCustomizable = false
        ),
        RadioStation(
            id = "groove_salad",
            name = "Groove Salad Ambient",
            genre = "Chill / Ambient",
            streamUrl = "https://ice.somafm.com/groovesalad-128-mp3",
            iconEmoji = "☕",
            isCustomizable = false
        )
    )

    // User's 6 Preferred Customizable Podcast Sources
    val DEFAULT_SIX_PODCASTS = listOf(
        RadioStation(
            id = "podcast_slot_1",
            name = "Daily News & Briefing",
            genre = "News / Politics",
            streamUrl = "https://npr-ice.streamguys1.com/live.mp3",
            iconEmoji = "📰",
            slotNumber = 1,
            isCustomizable = true
        ),
        RadioStation(
            id = "podcast_slot_2",
            name = "Global Perspectives & World",
            genre = "International / Culture",
            streamUrl = "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service",
            iconEmoji = "🌐",
            slotNumber = 2,
            isCustomizable = true
        ),
        RadioStation(
            id = "podcast_slot_3",
            name = "Tech Wire & Innovation",
            genre = "Technology / AI",
            streamUrl = "https://ice.somafm.com/defcon-128-mp3",
            iconEmoji = "💻",
            slotNumber = 3,
            isCustomizable = true
        ),
        RadioStation(
            id = "podcast_slot_4",
            name = "Science & Nature Dispatch",
            genre = "Science / Discovery",
            streamUrl = "https://ice.somafm.com/missioncontrol-128-mp3",
            iconEmoji = "🔬",
            slotNumber = 4,
            isCustomizable = true
        ),
        RadioStation(
            id = "podcast_slot_5",
            name = "Business, Finance & Markets",
            genre = "Economics / Money",
            streamUrl = "https://icecast.walmradio.com:8443/jazz",
            iconEmoji = "📈",
            slotNumber = 5,
            isCustomizable = true
        ),
        RadioStation(
            id = "podcast_slot_6",
            name = "My Custom Podcast Stream",
            genre = "User Preferred Audio",
            streamUrl = "https://classicalmusic.streamguys1.com/classical-mp3",
            iconEmoji = "🎧",
            slotNumber = 6,
            isCustomizable = true
        )
    )

    val STATIONS = PRESET_STATIONS
}
