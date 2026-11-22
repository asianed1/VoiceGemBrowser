package com.example

import com.example.data.model.GeminiContent
import com.example.data.model.GeminiPart
import com.example.data.model.GeminiRequest
import com.example.data.model.GeminiResponse
import com.example.messaging.ConfirmationAction
import com.example.messaging.LocalMessagingParser
import com.example.messaging.MessagingChannel
import com.example.messaging.MessagingDraft
import com.example.messaging.MessagingIntentLauncher
import com.example.messaging.MessagingMode
import com.example.messaging.ComposeStage
import com.example.messaging.QuickContact
import com.example.messaging.QuickContactsRepository
import com.example.notification.CapturedMessage
import com.example.notification.NotificationQueueManager
import com.example.notification.NotificationReplyStore
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGemV12UnitTest {

    private val sampleContacts = listOf(
        QuickContact(
            id = "c1",
            displayName = "John Smith",
            spokenAlias = "John",
            phoneNumber = "+15551234567",
            emailAddress = "john.smith@gmail.com",
            preferredChannel = MessagingChannel.WHATSAPP
        ),
        QuickContact(
            id = "c2",
            displayName = "Sarah Connor",
            spokenAlias = "Boss",
            phoneNumber = "09175559876",
            emailAddress = "sarah@skynet.example",
            preferredChannel = MessagingChannel.TELEGRAM
        )
    )

    @Test
    fun testLocalMessagingParser_parsesReplyCommandsWithoutHijackingQa() {
        val bareReply = LocalMessagingParser.parseReplyCommand("reply back")
        assertNotNull(bareReply)
        assertNull(bareReply?.targetQuery)
        assertNull(bareReply?.messageBody)

        val directedReply = LocalMessagingParser.parseReplyCommand(
            "reply to Sarah on WhatsApp saying I will arrive in 10 minutes"
        )
        assertNotNull(directedReply)
        assertEquals("Sarah", directedReply?.targetQuery)
        assertEquals(MessagingChannel.WHATSAPP, directedReply?.channel)
        assertEquals("I will arrive in 10 minutes", directedReply?.messageBody)

        val quickAnswer = LocalMessagingParser.parseReplyCommand("answer David saying got it thanks")
        assertNotNull(quickAnswer)
        assertEquals("David", quickAnswer?.targetQuery)
        assertEquals("got it thanks", quickAnswer?.messageBody)

        assertNull(LocalMessagingParser.parseReplyCommand("what is the reply to that riddle"))
        assertNull(LocalMessagingParser.parseReplyCommand("how do you reply politely in French"))
    }

    @Test
    fun testLocalMessagingParser_parsesNewMessageWithPhoneEmailAndContacts() {
        // 1. Name + literal telephone digits
        val smsPhone = LocalMessagingParser.parseNewMessageCommand(
            "send sms to John Smith telephone 123456 saying meet me at noon",
            contacts = emptyList()
        )
        assertNotNull(smsPhone)
        assertEquals(MessagingChannel.SMS, smsPhone?.channel)
        assertEquals("John Smith", smsPhone?.recipientName)
        assertEquals("123456", smsPhone?.recipientPhone)
        assertEquals("meet me at noon", smsPhone?.messageBody)

        // 2. Spoken email normalization
        val emailCmd = LocalMessagingParser.parseNewMessageCommand(
            "email john dot smith at gmail dot com saying attached is the report",
            contacts = emptyList()
        )
        assertNotNull(emailCmd)
        assertEquals(MessagingChannel.GMAIL, emailCmd?.channel)
        assertEquals("john.smith@gmail.com", emailCmd?.recipientEmail)
        assertEquals("attached is the report", emailCmd?.messageBody)

        // 3. QuickContact fuzzy/alias matching
        val waFuzzy = LocalMessagingParser.parseNewMessageCommand(
            "whatsapp Jon Smith saying running 5 minutes late",
            contacts = sampleContacts
        )
        assertNotNull(waFuzzy)
        assertEquals(MessagingChannel.WHATSAPP, waFuzzy?.channel)
        assertEquals("John Smith", waFuzzy?.recipientName)
        assertEquals("+15551234567", waFuzzy?.recipientPhone)
        assertEquals("running 5 minutes late", waFuzzy?.messageBody)

        val aliasMatch = LocalMessagingParser.parseNewMessageCommand(
            "text boss saying report uploaded",
            contacts = sampleContacts
        )
        assertNotNull(aliasMatch)
        assertEquals(MessagingChannel.SMS, aliasMatch?.channel)
        assertEquals("Sarah Connor", aliasMatch?.recipientName)
        assertEquals("09175559876", aliasMatch?.recipientPhone)

        // 4. Negative checks: must NOT hijack Q&A or summaries
        assertNull(LocalMessagingParser.parseNewMessageCommand("what is whatsapp", sampleContacts))
        assertNull(LocalMessagingParser.parseNewMessageCommand("summarize my messages", sampleContacts))
        assertNull(LocalMessagingParser.parseNewMessageCommand("email etiquette rules", sampleContacts))
    }

    @Test
    fun testLocalMessagingParser_parsesSpokenPhoneNumbersAndEmails() {
        assertEquals("john.smith@gmail.com", LocalMessagingParser.parseSpokenEmail("john dot smith at gmail dot com"))
        assertEquals("alice_dev@outlook.com", LocalMessagingParser.parseSpokenEmail("alice underscore dev at outlook dot com"))
        assertNull(LocalMessagingParser.parseSpokenEmail("hello world"))

        assertEquals("09175551234", LocalMessagingParser.parseSpokenPhoneNumber("zero nine one seven five five five one two three four"))
        assertEquals("+15551234567", LocalMessagingParser.parseSpokenPhoneNumber("plus 1 555 123 4567"))
        assertNull(LocalMessagingParser.parseSpokenPhoneNumber("play channel 5"))
    }

    @Test
    fun testLocalMessagingParser_confirmationActions() {
        assertEquals(ConfirmationAction.SEND, LocalMessagingParser.detectConfirmationAction("yes send it"))
        assertEquals(ConfirmationAction.SEND, LocalMessagingParser.detectConfirmationAction("confirm"))
        assertEquals(ConfirmationAction.EDIT, LocalMessagingParser.detectConfirmationAction("edit message"))
        assertEquals(ConfirmationAction.EDIT, LocalMessagingParser.detectConfirmationAction("rewrite"))
        assertEquals(ConfirmationAction.CANCEL, LocalMessagingParser.detectConfirmationAction("cancel"))
        assertEquals(ConfirmationAction.CANCEL, LocalMessagingParser.detectConfirmationAction("never mind"))
        assertNull(LocalMessagingParser.detectConfirmationAction("actually make it 6pm"))
    }

    @Test
    fun testQuickContactsRepository_serializationAndFuzzyMatching() {
        val json = QuickContactsRepository.serializeContacts(sampleContacts)
        val restored = QuickContactsRepository.deserializeContacts(json)
        assertEquals(2, restored.size)
        assertEquals("John Smith", restored[0].displayName)
        assertEquals(MessagingChannel.WHATSAPP, restored[0].preferredChannel)
        assertEquals("Sarah Connor", restored[1].displayName)

        assertEquals("c1", QuickContactsRepository.findBestMatch("john", restored)?.id)
        assertEquals("c2", QuickContactsRepository.findBestMatch("boss", restored)?.id)
        assertEquals("c1", QuickContactsRepository.findBestMatch("Jhon Smith", restored)?.id)
        assertNull(QuickContactsRepository.findBestMatch("Completely Unknown Person", restored))
    }

    @Test
    fun testMessagingIntentLauncher_formatsE164WhatsAppNumbers() {
        assertEquals("639175551234", MessagingIntentLauncher.formatForWhatsAppUri("0917 555 1234", "PH"))
        assertEquals("15551234567", MessagingIntentLauncher.formatForWhatsAppUri("(555) 123-4567", "US"))
        assertEquals("447700900077", MessagingIntentLauncher.formatForWhatsAppUri("+44 7700 900077", "US"))
        assertNull(MessagingIntentLauncher.formatForWhatsAppUri("123", "US"))
    }

    @Test
    fun testNotificationReplyStore_blocksOtpAndResolvesRecentReplyTargets() {
        NotificationReplyStore.clearAll()
        NotificationQueueManager.clearQueue()

        // OTP notifications must never enter NotificationReplyStore
        val otpMsg = CapturedMessage(
            id = "otp-1",
            packageName = "com.android.mms",
            appName = "SMS",
            sender = "Bank",
            text = "Your verification code is 992817. Never share it."
        )
        NotificationReplyStore.recordReplyTarget(otpMsg, "key-otp", null)
        assertNull(NotificationReplyStore.getEntry("otp-1"))

        val waMsg = CapturedMessage(
            id = "wa-1",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            sender = "Maria Clara",
            text = "Are we still on for lunch?"
        )
        NotificationQueueManager.addMessage(waMsg)
        assertEquals(1, NotificationQueueManager.unreadCount.value)

        val resolved = NotificationReplyStore.findReplyTarget("Maria", "WhatsApp")
        assertNotNull(resolved)
        assertEquals("wa-1", resolved?.message?.id)

        // Removing a single replied message keeps remaining items intact
        NotificationQueueManager.removeMessageById("wa-1")
        assertEquals(0, NotificationQueueManager.unreadCount.value)

        NotificationReplyStore.clearAll()
        NotificationQueueManager.clearQueue()
    }

    @Test
    fun testMessagingDraft_readinessAndTargetChecks() {
        val silentDraft = MessagingDraft(
            mode = MessagingMode.SILENT_REPLY,
            stage = ComposeStage.AWAITING_CONFIRMATION,
            channel = MessagingChannel.WHATSAPP,
            recipientName = "Maria",
            messageBody = "Yes, see you at 12!",
            replyMessageId = "wa-1",
            hasDirectRemoteInput = true
        )
        assertTrue(silentDraft.hasRecipientInfo())
        assertTrue(silentDraft.hasSendTarget())

        val gmailMissingEmail = MessagingDraft(
            mode = MessagingMode.NEW_MESSAGE,
            stage = ComposeStage.NEEDS_RECIPIENT,
            channel = MessagingChannel.GMAIL,
            recipientName = "John",
            recipientEmail = "",
            messageBody = "Hello"
        )
        assertFalse(gmailMissingEmail.hasSendTarget())
    }

    @Test
    fun testKspGeneratedMoshiAdapters_workWithoutReflectionFactory() {
        val moshi = Moshi.Builder().build()
        val reqAdapter = moshi.adapter(GeminiRequest::class.java)
        val respAdapter = moshi.adapter(GeminiResponse::class.java)

        val requestJson = reqAdapter.toJson(
            GeminiRequest(
                contents = listOf(
                    GeminiContent(role = "user", parts = listOf(GeminiPart(text = "Hello")))
                )
            )
        )
        assertTrue(requestJson.contains("\"Hello\""))

        val parsed = respAdapter.fromJson(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"Hi there"}]},"finishReason":"STOP"}]}"""
        )
        assertEquals("Hi there", parsed?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text)
    }

    @Test
    fun testNotificationReplyStore_survivesDigestQueueClearForImmediateVoiceReply() {
        NotificationReplyStore.clearAll()
        NotificationQueueManager.clearQueue()

        val tgMsg = CapturedMessage(
            id = "tg-1",
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            sender = "Elena",
            text = "See you at the studio at 4 PM!"
        )
        NotificationQueueManager.addMessage(tgMsg)
        // Simulates summarizeIncomingMessages() clearing the unread badge queue
        NotificationQueueManager.clearQueue()
        assertEquals(0, NotificationQueueManager.unreadCount.value)

        // Immediate verbal "reply" must still find Elena
        val replyTarget = NotificationReplyStore.findReplyTarget(senderQuery = null)
        assertNotNull(replyTarget)
        assertEquals("tg-1", replyTarget?.message?.id)
        assertEquals("Elena", replyTarget?.message?.sender)

        NotificationReplyStore.clearAll()
    }

    @Test
    fun testLocalMessagingParser_telegramViberAndNonHijackGuards() {
        val tg = LocalMessagingParser.parseNewMessageCommand(
            "send telegram to Sarah Connor saying check the new build",
            contacts = sampleContacts
        )
        assertNotNull(tg)
        assertEquals(MessagingChannel.TELEGRAM, tg?.channel)
        assertEquals("Sarah Connor", tg?.recipientName)
        assertEquals("check the new build", tg?.messageBody)

        val viber = LocalMessagingParser.parseNewMessageCommand(
            "viber John Smith saying call me back",
            contacts = sampleContacts
        )
        assertNotNull(viber)
        assertEquals(MessagingChannel.VIBER, viber?.channel)
        assertEquals("John Smith", viber?.recipientName)
        assertEquals("call me back", viber?.messageBody)

        // General web/Gemini queries starting with messaging brand nouns must NOT be hijacked
        assertNull(LocalMessagingParser.parseNewMessageCommand("telegram founder biography", sampleContacts))
        assertNull(LocalMessagingParser.parseNewMessageCommand("text book summary of photosynthesis", sampleContacts))
        assertNull(LocalMessagingParser.parseNewMessageCommand("whatsapp privacy policy update", sampleContacts))

        // Corrupt JSON resilience in QuickContactsRepository
        assertTrue(QuickContactsRepository.deserializeContacts("{not valid json").isEmpty())
    }
}
