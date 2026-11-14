package com.example

import com.example.data.repository.FreeKnowledgeFallbackRepository
import com.example.data.repository.GeminiApiException
import com.example.data.repository.GeminiRepository
import com.example.media.DefaultRadioStations
import com.example.notification.CapturedMessage
import com.example.notification.LocalMessageSynthesizer
import com.example.ui.LocalCommandParser
import com.example.voice.VoiceGenderPreference
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class VoiceGemV7UnitTest {

    @Test
    fun testLocalMessageSynthesizer_groupsByAppAndSenderAndCleansUrls() {
        val messages = listOf(
            CapturedMessage(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                sender = "Sarah",
                text = "Hey! Are we meeting at 7:30? Check https://maps.example.com/123"
            ),
            CapturedMessage(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                sender = "Sarah",
                text = "Also bring your jacket!"
            ),
            CapturedMessage(
                packageName = "com.google.android.gm",
                appName = "Gmail",
                sender = "David",
                text = "**Q4 Proposal** attached for review."
            )
        )

        val digest = LocalMessageSynthesizer.synthesizeBriefing(messages)
        assertTrue(digest.startsWith("You have 3 unread messages."))
        assertTrue(digest.contains("On WhatsApp, Sarah says:"))
        assertTrue(digest.contains("a web link"))
        assertFalse(digest.contains("https://"))
        assertFalse(digest.contains("**"))
        assertTrue(digest.contains("On Gmail, David wrote: Q4 Proposal attached for review."))
    }

    @Test
    fun testLocalCommandParser_detectsVoiceGenderCommandsWithoutHijackingQa() {
        assertEquals(
            VoiceGenderPreference.MALE,
            LocalCommandParser.detectVoiceGenderCommand("switch to male voice")
        )
        assertEquals(
            VoiceGenderPreference.FEMALE,
            LocalCommandParser.detectVoiceGenderCommand("use american female voice")
        )
        assertNull(
            LocalCommandParser.detectVoiceGenderCommand("compare male and female voice actors")
        )
    }

    @Test
    fun testLocalCommandParser_handlesPodcastAndMessageCommandsLocally() {
        assertTrue(LocalCommandParser.isMessageSummaryCommand("summarize my messages"))
        assertTrue(LocalCommandParser.isMessageSummaryCommand("read unread texts"))
        assertFalse(LocalCommandParser.isMessageSummaryCommand("translate hotel near eiffel tower to french"))

        val bbc = LocalCommandParser.matchPlayStationCommand(
            normalized = "play bbc podcast",
            podcastSlots = DefaultRadioStations.DEFAULT_SIX_PODCASTS,
            presets = DefaultRadioStations.PRESET_STATIONS
        )
        assertNotNull(bbc)
        assertEquals("bbc_world", bbc?.id)

        val customSlots = DefaultRadioStations.DEFAULT_SIX_PODCASTS.map {
            if (it.slotNumber == 1) it.copy(name = "Almirante Montojo") else it
        }
        val customMatch = LocalCommandParser.matchPlayStationCommand(
            normalized = "listen to almirante",
            podcastSlots = customSlots,
            presets = DefaultRadioStations.PRESET_STATIONS
        )
        assertNotNull(customMatch)
        assertEquals(1, customMatch?.slotNumber)
    }

    @Test
    fun testFastFailRetry_retriesTransient429WithBoundedJitter() = runBlocking {
        val repo = GeminiRepository()
        var attempts = 0
        val recordedDelays = mutableListOf<Long>()

        val result = repo.executeWithFastFailRetry(
            maxRetries = 2,
            baseDelayMs = 1100L,
            maxJitterMs = 450L,
            delayFn = { recordedDelays.add(it) }
        ) {
            attempts++
            if (attempts < 3) {
                throw HttpException(
                    Response.error<String>(
                        429,
                        """{"error":{"message":"Rate limit exceeded"}}""".toResponseBody("application/json".toMediaType())
                    )
                )
            }
            "Recovered answer"
        }

        assertEquals("Recovered answer", result)
        assertEquals(3, attempts)
        assertEquals(2, recordedDelays.size)
        assertTrue(recordedDelays[0] in 1100L..1550L)
        assertTrue(recordedDelays[1] in 2200L..2800L)
    }

    @Test
    fun testFastFailRetry_doesNotRetryDailyQuotaOrUnauthorized() = runBlocking {
        val repo = GeminiRepository()
        var attempts = 0
        var threw = false

        try {
            repo.executeWithFastFailRetry(
                maxRetries = 2,
                delayFn = {}
            ) {
                attempts++
                throw HttpException(
                    Response.error<String>(
                        429,
                        """{"error":{"message":"Quota exceeded per day"}}""".toResponseBody("application/json".toMediaType())
                    )
                )
            }
        } catch (e: GeminiApiException) {
            threw = true
            assertTrue(e.isDailyQuotaExhausted)
            assertTrue(e.isRateLimit)
        }

        assertTrue(threw)
        assertEquals(1, attempts)
    }

    @Test
    fun testFreeKnowledgeFallback_extractsTopicsCleanly() {
        val fallback = FreeKnowledgeFallbackRepository()
        assertEquals("photosynthesis", fallback.extractSearchTopic("What is photosynthesis?"))
        assertEquals("Marie Curie", fallback.extractSearchTopic("Who was Marie Curie?"))
        assertEquals("Eiffel Tower", fallback.extractSearchTopic("Tell me about Eiffel Tower"))
    }
}
