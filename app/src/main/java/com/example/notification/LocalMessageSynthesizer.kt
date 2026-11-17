package com.example.notification

/**
 * Strictly 100% on-device message synthesizer.
 * Notification contents are grouped, cleaned, and formatted locally in memory
 * so private messages from WhatsApp, SMS, Gmail, Telegram, Viber, Slack, and Discord
 * NEVER leave the Android device or get uploaded to any cloud AI server.
 */
object LocalMessageSynthesizer {

    fun synthesizeBriefing(messages: List<CapturedMessage>): String {
        if (messages.isEmpty()) {
            return "You have no new messages from WhatsApp, Gmail, Telegram, Viber, Slack, or SMS."
        }

        // Defense-in-depth: never read OTP / 2FA passcodes aloud even if passed directly
        val safeMessages = messages.filterNot { msg ->
            LocalSpamFilter.isSensitiveOtpOr2fa(msg.sender, msg.text)
        }

        if (safeMessages.isEmpty()) {
            return "You have no non-sensitive unread messages right now."
        }

        val totalCount = safeMessages.size
        val header = if (totalCount == 1) {
            "You have 1 unread message."
        } else {
            "You have $totalCount unread messages."
        }

        // Group by appName preserving order of arrival, then by sender within each app
        val groupedByApp = linkedMapOf<String, LinkedHashMap<String, MutableList<String>>>()
        for (msg in safeMessages) {
            val cleanText = cleanMessageText(msg.text)
            if (cleanText.isBlank()) continue

            val appSenders = groupedByApp.getOrPut(msg.appName) { linkedMapOf() }
            val senderTexts = appSenders.getOrPut(msg.sender.ifBlank { "Someone" }) { mutableListOf() }
            // Avoid reading exact duplicate notification text twice
            if (!senderTexts.contains(cleanText)) {
                senderTexts.add(cleanText)
            }
        }

        if (groupedByApp.isEmpty()) {
            return "You have $totalCount unread notification${if (totalCount > 1) "s" else ""}, but no readable message text."
        }

        val appBriefings = mutableListOf<String>()
        for ((appName, sendersMap) in groupedByApp) {
            val senderParts = mutableListOf<String>()
            for ((sender, texts) in sendersMap) {
                val combinedText = if (texts.size == 1) {
                    texts.first()
                } else {
                    texts.takeLast(3).joinToString(". Then: ")
                }
                val cappedText = if (combinedText.length > 200) {
                    combinedText.take(197).trimEnd() + "…"
                } else {
                    combinedText
                }
                val verb = if (appName.contains("mail", ignoreCase = true) || appName.equals("Gmail", ignoreCase = true)) {
                    "wrote"
                } else {
                    "says"
                }
                senderParts.add("$sender $verb: $cappedText")
            }
            appBriefings.add("On $appName, ${senderParts.joinToString(". Also, ")}")
        }

        return "$header ${appBriefings.joinToString(". ")}"
    }

    internal fun cleanMessageText(raw: String): String {
        return raw
            .replace(Regex("""https?://\S+"""), "a web link")
            .replace(Regex("""[*_`~#>]+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
