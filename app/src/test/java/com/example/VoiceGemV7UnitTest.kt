package com.example

import com.example.data.repository.FreeKnowledgeFallbackRepository
import com.example.data.repository.GeminiApiException
import com.example.data.repository.GeminiRepository
import com.example.media.DefaultRadioStations
import com.example.media.PodcastSearchService
import com.example.media.RssPodcastResolver
import com.example.notification.CapturedMessage
import com.example.notification.FilterVerdict
import com.example.notification.LocalMessageSynthesizer
import com.example.notification.LocalSpamFilter
import com.example.notification.NotificationQueueManager
import com.example.security.UrlSecurityGuard
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
    fun testLocalSpamFilter_blocksOtpAnd2faEvenIfPromoFilterDisabled() {
        val otpSamples = listOf(
            "Google" to "G-482910 is your Google verification code.",
            "Chase" to "Use passcode 918273 to complete sign-in. Do not share this code.",
            "Auth" to "Your 2FA code is 445120. Valid for 5 minutes.",
            "Bank" to "Your OTP is 7812. Never share this OTP with anyone."
        )

        otpSamples.forEach { (sender, text) ->
            assertEquals(
                "Must strictly block OTP/2FA even if promo filter off: $text",
                FilterVerdict.BLOCK_OTP_SENSITIVE,
                LocalSpamFilter.evaluate(sender, text, promoFilterEnabled = false)
            )
        }

        // Defense-in-depth: synthesizer also strips any OTP passed directly
        val safeMsg = CapturedMessage(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            sender = "Sarah",
            text = "Running 10 minutes late for lunch!"
        )
        val leakedOtp = CapturedMessage(
            packageName = "com.android.mms",
            appName = "SMS",
            sender = "Bank",
            text = "Your verification code is 654321. Do not share."
        )
        val digest = LocalMessageSynthesizer.synthesizeBriefing(listOf(safeMsg, leakedOtp))
        assertTrue(digest.contains("Sarah"))
        assertFalse(digest.contains("654321"))
        assertFalse(digest.contains("verification code"))
    }

    @Test
    fun testLocalSpamFilter_filtersPromosAndMutedSendersWithoutBlockingRealMessages() {
        assertEquals(
            FilterVerdict.SPAM_PROMO,
            LocalSpamFilter.evaluate("RetailDeals", "FLASH SALE! 50% off everything today. Reply STOP to unsubscribe.")
        )
        assertEquals(
            FilterVerdict.USER_MUTED,
            LocalSpamFilter.evaluate(
                sender = "NoisyGroup",
                text = "Anyone up for trivia?",
                mutedSenders = setOf("NoisyGroup")
            )
        )
        assertEquals(
            FilterVerdict.ALLOW,
            LocalSpamFilter.evaluate("Sarah", "Hey! Dinner tonight at 7:30?")
        )
        assertEquals(
            FilterVerdict.ALLOW,
            LocalSpamFilter.evaluate("Delta", "Flight 1420 departs Gate B12 at 9:15 AM.")
        )
    }

    @Test
    fun testNotificationQueueManager_neverStoresOtpAndMovesMutedMessages() {
        NotificationQueueManager.clearQueue()
        NotificationQueueManager.clearFilteredQueue()

        NotificationQueueManager.addMessage(
            CapturedMessage(packageName = "sms", appName = "SMS", sender = "Auth", text = "Your OTP is 839201.")
        )
        assertEquals(0, NotificationQueueManager.unreadCount.value)

        NotificationQueueManager.addMessage(
            CapturedMessage(packageName = "wa", appName = "WhatsApp", sender = "Alex", text = "See you at 5!")
        )
        assertEquals(1, NotificationQueueManager.unreadCount.value)

        NotificationQueueManager.muteSenderFromQueue("Alex")
        assertEquals(0, NotificationQueueManager.unreadCount.value)
        assertEquals(1, NotificationQueueManager.filteredCount.value)

        NotificationQueueManager.clearQueue()
        NotificationQueueManager.clearFilteredQueue()
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
    fun testLocalCommandParser_handles8PodcastsSeekEpisodeAndSleepCommands() {
        assertTrue(LocalCommandParser.isMessageSummaryCommand("summarize my messages"))
        assertTrue(LocalCommandParser.isMessageSummaryCommand("read unread texts"))
        assertFalse(LocalCommandParser.isMessageSummaryCommand("translate hotel near eiffel tower to french"))

        val bbc = LocalCommandParser.matchPlayStationCommand(
            normalized = "play bbc podcast",
            podcastSlots = DefaultRadioStations.DEFAULT_EIGHT_PODCASTS,
            presets = DefaultRadioStations.PRESET_STATIONS
        )
        assertNotNull(bbc)
        assertEquals("bbc_world", bbc?.id)

        val slot7 = LocalCommandParser.matchPlayStationCommand(
            normalized = "play podcast 7",
            podcastSlots = DefaultRadioStations.DEFAULT_EIGHT_PODCASTS,
            presets = DefaultRadioStations.PRESET_STATIONS
        )
        assertNotNull(slot7)
        assertEquals(7, slot7?.slotNumber)

        val slot8 = LocalCommandParser.matchPlayStationCommand(
            normalized = "play slot 8",
            podcastSlots = DefaultRadioStations.DEFAULT_EIGHT_PODCASTS,
            presets = DefaultRadioStations.PRESET_STATIONS
        )
        assertNotNull(slot8)
        assertEquals(8, slot8?.slotNumber)

        val customSlots = DefaultRadioStations.DEFAULT_EIGHT_PODCASTS.map {
            if (it.slotNumber == 1) it.copy(name = "Almirante Montojo") else it
        }
        val customMatch = LocalCommandParser.matchPlayStationCommand(
            normalized = "listen to almirante",
            podcastSlots = customSlots,
            presets = DefaultRadioStations.PRESET_STATIONS
        )
        assertNotNull(customMatch)
        assertEquals(1, customMatch?.slotNumber)

        assertEquals(30_000L, LocalCommandParser.detectSeekDeltaMs("skip forward 30 seconds"))
        assertEquals(-15_000L, LocalCommandParser.detectSeekDeltaMs("rewind 15 seconds"))
        assertEquals(1, LocalCommandParser.detectEpisodeNavCommand("next episode"))
        assertEquals(-1, LocalCommandParser.detectEpisodeNavCommand("previous episode"))
        assertEquals(30, LocalCommandParser.detectSleepTimerCommand("set sleep timer for 30 minutes"))
        assertEquals(-1, LocalCommandParser.detectSleepTimerCommand("sleep timer end of episode"))
        assertEquals(0, LocalCommandParser.detectSleepTimerCommand("cancel sleep timer"))
    }

    @Test
    fun testUrlSecurityGuard_blocksCleartextAndPrivateNetworkTargets() {
        assertTrue(UrlSecurityGuard.isSafeHttpsUrl("https://feeds.npr.org/510289/podcast.xml"))
        assertTrue(UrlSecurityGuard.isSafeHttpsUrl("https://cdn.example.com/audio/ep1.mp3"))

        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("http://feeds.npr.org/510289/podcast.xml"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("file:///etc/passwd"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("https://localhost/feed.xml"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("https://127.0.0.1/feed.xml"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("https://192.168.1.50/stream.mp3"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("https://10.0.0.1/admin"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("https://169.254.169.254/latest/meta-data/"))
        assertFalse(UrlSecurityGuard.isSafeHttpsUrl("https://[::1]/feed.xml"))
    }

    @Test
    fun testRssPodcastResolver_parsesMultipleEpisodesAndFiltersUnsafeEnclosures() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Show</title>
                    <item>
                        <title>Unsafe Cleartext Episode</title>
                        <enclosure url="http://insecure.example.com/ep0.mp3" type="audio/mpeg" />
                    </item>
                    <item>
                        <title>Episode 42</title>
                        <pubDate>Mon, 16 Nov 2026 10:00:00 +0000</pubDate>
                        <itunes:duration>12:30</itunes:duration>
                        <enclosure url="https://cdn.example.com/ep42.mp3" type="audio/mpeg" />
                    </item>
                    <item>
                        <title>Episode 41</title>
                        <pubDate>Sun, 15 Nov 2026 10:00:00 +0000</pubDate>
                        <itunes:duration>600</itunes:duration>
                        <enclosure url="https://cdn.example.com/ep41.mp3" type="audio/mpeg" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val episodes = RssPodcastResolver.parseEpisodes(xml, maxEpisodes = 5)
        assertEquals(2, episodes.size)
        assertEquals("Episode 42", episodes[0].title)
        assertEquals("https://cdn.example.com/ep42.mp3", episodes[0].audioUrl)
        assertEquals("16 Nov 2026", episodes[0].pubDate)
        assertEquals(750_000L, episodes[0].durationMs)
        assertEquals("Episode 41", episodes[1].title)
        assertEquals(600_000L, episodes[1].durationMs)
    }

    @Test
    fun testPodcastSearchService_parsesHttpsResultsOnly() {
        val json = """
            {
              "resultCount": 2,
              "results": [
                {
                  "collectionId": 101,
                  "collectionName": "Planet Money",
                  "artistName": "NPR",
                  "primaryGenreName": "Business",
                  "feedUrl": "https://feeds.npr.org/510289/podcast.xml"
                },
                {
                  "collectionId": 102,
                  "collectionName": "Legacy Insecure Show",
                  "artistName": "OldHost",
                  "primaryGenreName": "News",
                  "feedUrl": "http://legacy.example.com/feed.xml"
                }
              ]
            }
        """.trimIndent()

        val results = PodcastSearchService.parseItunesJson(json)
        assertEquals(1, results.size)
        assertEquals("Planet Money", results[0].title)
        assertEquals("https://feeds.npr.org/510289/podcast.xml", results[0].feedUrl)
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

    @Test
    fun testRadioStations_eightSlotsAreCustomizableWithValidHttpsUrls() {
        assertEquals(8, DefaultRadioStations.DEFAULT_EIGHT_PODCASTS.size)
        assertTrue(DefaultRadioStations.DEFAULT_EIGHT_PODCASTS.all { it.isCustomizable })
        assertTrue(DefaultRadioStations.DEFAULT_EIGHT_PODCASTS.all { it.streamUrl.startsWith("https://") })
        assertEquals("Up First from NPR", DefaultRadioStations.DEFAULT_EIGHT_PODCASTS[5].name)
        assertEquals("Planet Money from NPR", DefaultRadioStations.DEFAULT_EIGHT_PODCASTS[6].name)
        assertEquals("https://feeds.npr.org/510289/podcast.xml", DefaultRadioStations.DEFAULT_EIGHT_PODCASTS[6].streamUrl)
        assertEquals("Short Wave from NPR", DefaultRadioStations.DEFAULT_EIGHT_PODCASTS[7].name)
        assertEquals("https://feeds.npr.org/510351/podcast.xml", DefaultRadioStations.DEFAULT_EIGHT_PODCASTS[7].streamUrl)

        assertEquals(4, DefaultRadioStations.PRESET_STATIONS.size)
        assertTrue(DefaultRadioStations.PRESET_STATIONS.none { it.isCustomizable })
        assertTrue(DefaultRadioStations.PRESET_STATIONS.any { it.id == "npr_live" })
        assertTrue(DefaultRadioStations.PRESET_STATIONS.any { it.id == "bbc_world" })
    }

    @Test
    fun testMediaFocusCoordinator_autoResumesAfterMessageDigestAndVoiceTurn() {
        val coordinator = com.example.media.MediaFocusCoordinator()
        coordinator.onMediaStarted()

        coordinator.onVoiceTurnStarted()
        val transientLoss = coordinator.onMediaFocusLostTransient(canDuck = false)
        assertEquals(com.example.media.MediaFocusCoordinator.FocusAction.PAUSE_TRANSIENT, transientLoss)

        assertEquals(com.example.media.MediaFocusCoordinator.FocusAction.NONE, coordinator.onMediaFocusRegained())

        assertEquals(
            com.example.media.MediaFocusCoordinator.FocusAction.RESUME_AND_UNDUCK,
            coordinator.onVoiceTurnEnded()
        )
    }

    @Test
    fun testMediaFocusCoordinator_respectsManualPauseAndStop() {
        val coordinator = com.example.media.MediaFocusCoordinator()
        coordinator.onMediaStarted()
        coordinator.onMediaManuallyPaused()

        assertEquals(
            com.example.media.MediaFocusCoordinator.FocusAction.NONE,
            coordinator.onMediaFocusLostTransient(canDuck = false)
        )
        assertEquals(
            com.example.media.MediaFocusCoordinator.FocusAction.NONE,
            coordinator.onVoiceTurnEnded()
        )
    }

    @Test
    fun testStreamRecoveryPolicy_retriesTransientSignalDropsAndStopsAtMax() {
        assertTrue(com.example.media.StreamRecoveryPolicy.shouldReconnectAfterError(shouldBePlaying = true, attemptCount = 0))
        assertTrue(com.example.media.StreamRecoveryPolicy.shouldReconnectAfterError(shouldBePlaying = true, attemptCount = 2))
        assertFalse(com.example.media.StreamRecoveryPolicy.shouldReconnectAfterError(shouldBePlaying = true, attemptCount = 3))
        assertFalse(com.example.media.StreamRecoveryPolicy.shouldReconnectAfterError(shouldBePlaying = false, attemptCount = 0))

        assertEquals(2_000L, com.example.media.StreamRecoveryPolicy.nextRetryDelayMs(0))
        assertEquals(4_000L, com.example.media.StreamRecoveryPolicy.nextRetryDelayMs(1))
        assertEquals(6_000L, com.example.media.StreamRecoveryPolicy.nextRetryDelayMs(2))
    }
}
