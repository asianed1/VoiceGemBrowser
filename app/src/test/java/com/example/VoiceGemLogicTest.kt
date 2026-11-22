package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiResponse
import com.example.data.model.MessageSender
import com.example.data.repository.GeminiRepository
import com.example.data.user.UserManager
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceGemLogicTest {

    private val repository = GeminiRepository()

    @Test
    fun testCleanTextForSpeech_stripsMarkdownAndUrls() {
        val input = "Here is **important** information. Check [Google](https://google.com) or visit https://example.com/guide.\n* First bullet\n* Second bullet\n# Big Header"
        val cleaned = repository.cleanTextForSpeech(input)

        // Must not contain markdown formatting or raw URLs
        assertFalse("Cleaned text should not contain asterisks", cleaned.contains("*"))
        assertFalse("Cleaned text should not contain hashes", cleaned.contains("#"))
        assertFalse("Cleaned text should not contain raw URLs", cleaned.contains("https://"))
        // Link anchor text "Google" should be retained
        assertTrue("Cleaned text should retain anchor text", cleaned.contains("Google"))
        assertTrue("Cleaned text should retain normal text", cleaned.contains("Here is important information."))
    }

    @Test
    fun testCleanTextForSpeech_normalizesWhitespace() {
        val input = "Hello       there.\n\n\n\nHow    are you?"
        val cleaned = repository.cleanTextForSpeech(input)
        assertEquals("Hello there. How are you?", cleaned)
    }

    @Test
    fun testMoshi_serializesAndDeserializesGeminiResponse() {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(GeminiResponse::class.java)

        val json = """
            {
                "candidates": [
                    {
                        "content": {
                            "parts": [
                                { "text": "The sky is blue because of Rayleigh scattering." }
                            ],
                            "role": "model"
                        },
                        "finishReason": "STOP"
                    }
                ]
            }
        """.trimIndent()

        val response = adapter.fromJson(json)
        assertNotNull("Response should parse", response)
        val candidate = response?.candidates?.firstOrNull()
        assertNotNull("Candidate should not be null", candidate)
        val text = candidate?.content?.parts?.firstOrNull()?.text
        assertEquals("The sky is blue because of Rayleigh scattering.", text)
        assertEquals("STOP", candidate?.finishReason)
    }

    @Test
    fun testChatMessage_historyCreation() {
        val userMsg = ChatMessage(sender = MessageSender.USER, text = "What is the speed of light?")
        val modelMsg = ChatMessage(sender = MessageSender.VOICE_GEM, text = "The speed of light in vacuum is approximately three hundred thousand kilometers per second.")

        assertEquals(MessageSender.USER, userMsg.sender)
        assertEquals("What is the speed of light?", userMsg.text)
        assertNotNull(userMsg.id)

        assertEquals(MessageSender.VOICE_GEM, modelMsg.sender)
        assertTrue(userMsg.timestamp <= modelMsg.timestamp)
    }

    @Test
    fun testUserManager_apiKeySettingsAndQueries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val userManager = UserManager(context)

        // Reset state for test
        userManager.signOut()
        userManager.clearCustomApiKey()

        assertTrue("User can make queries based on Gemini API quota", userManager.canMakeQuery())
        assertFalse("Initially should use default app key", userManager.userProfile.value.hasCustomApiKey)

        // Set custom Gemini API key
        userManager.setCustomApiKey("AIzaSyTestCustomKey123")
        assertTrue("Custom API key should be active", userManager.userProfile.value.hasCustomApiKey)
        assertEquals("AIzaSyTestCustomKey123", userManager.getEffectiveApiKey())

        // Clear custom key returns to default
        userManager.clearCustomApiKey()
        assertFalse("Should return to default key", userManager.userProfile.value.hasCustomApiKey)
    }

    @Test
    fun testExitPhrases_detectsVoiceTerminationCommands() {
        val exitPhrases = setOf(
            "exit", "quit", "stop", "bye", "goodbye", "end chat", "stop chat", "terminate",
            "close", "close app", "exit chat", "terminate chat", "stop talking", "good bye"
        )

        fun isExitCommand(raw: String): Boolean {
            val normalized = raw.trim().lowercase(java.util.Locale.US).replace(Regex("""[^a-z\s]"""), "")
            return exitPhrases.contains(normalized) || normalized.startsWith("exit ") || normalized.startsWith("quit ") || normalized.startsWith("stop ")
        }

        assertTrue("Should detect 'Goodbye'", isExitCommand("Goodbye"))
        assertTrue("Should detect 'bye'", isExitCommand("bye"))
        assertTrue("Should detect 'exit'", isExitCommand("exit"))
        assertTrue("Should detect 'stop chat'", isExitCommand("stop chat"))
        assertTrue("Should detect 'quit please'", isExitCommand("quit please"))
        assertFalse("Should not flag regular query", isExitCommand("Why is the sky blue?"))
        assertFalse("Should not flag query about stopping", isExitCommand("How do cars stop?"))
    }

    @Test
    fun testDefaultPresets_containsNprAndBbc() {
        val presets = com.example.media.DefaultRadioStations.PRESET_STATIONS
        assertTrue("Presets should have at least NPR and BBC", presets.size >= 2)
        val hasNpr = presets.any { it.name.contains("NPR", ignoreCase = true) }
        val hasBbc = presets.any { it.name.contains("BBC", ignoreCase = true) }
        assertTrue("NPR should be in presets", hasNpr)
        assertTrue("BBC should be in presets", hasBbc)
    }

    @Test
    fun testPreferredPodcastSlots_exactlyEightSlotsAndCustomizable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.media.PodcastPreferences(context)
        val slots = prefs.preferredPodcasts.value
        assertEquals("Must provide exactly 8 preferred podcast slots", 8, slots.size)
        slots.forEachIndexed { index, slot ->
            assertEquals("Slot number should match index + 1", index + 1, slot.slotNumber)
            assertTrue("Podcast slot should be marked customizable", slot.isCustomizable)
            assertNotNull("Slot ID must not be null", slot.id)
        }
    }

    @Test
    fun testPodcastPreferences_updateAndResetSlots() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.media.PodcastPreferences(context)

        // Customize Slot 1
        prefs.updatePodcastSlot(
            slotNumber = 1,
            name = "My Custom Tech Podcast",
            genre = "Tech",
            streamUrl = "https://custom.podcast.com/stream.mp3",
            iconEmoji = "💻"
        )

        val updatedSlots = prefs.preferredPodcasts.value
        val slot1 = updatedSlots.first { it.slotNumber == 1 }
        assertEquals("My Custom Tech Podcast", slot1.name)
        assertEquals("Tech", slot1.genre)
        assertEquals("https://custom.podcast.com/stream.mp3", slot1.streamUrl)

        // Reset Slot 1 back to default
        prefs.resetSlotToDefault(1)
        val resetSlots = prefs.preferredPodcasts.value
        val resetSlot1 = resetSlots.first { it.slotNumber == 1 }
        assertEquals("Daily News & Briefing", resetSlot1.name)
    }

    @Test
    fun testRssPodcastResolver_parsesEnclosureUrl() {
        val sampleXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Sample Podcast Show</title>
                    <item>
                        <title>Episode 42: The Future of Voice Browsing</title>
                        <enclosure url="https://cdn.example.com/podcasts/episode42.mp3" length="35000000" type="audio/mpeg" />
                    </item>
                    <item>
                        <title>Episode 41: Older Episode</title>
                        <enclosure url="https://cdn.example.com/podcasts/episode41.mp3" length="32000000" type="audio/mpeg" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val parsedUrl = com.example.media.RssPodcastResolver.parseLatestEnclosure(sampleXml)
        assertEquals("https://cdn.example.com/podcasts/episode42.mp3", parsedUrl)
    }
}
