package com.example.messaging

import java.util.Locale

enum class ConfirmationAction {
    SEND,
    EDIT,
    CANCEL
}

data class ReplyCommandParseResult(
    val targetQuery: String? = null,
    val channel: MessagingChannel? = null,
    val messageBody: String? = null
)

data class NewMessageParseResult(
    val channel: MessagingChannel,
    val explicitChannelRequested: Boolean,
    val matchedContact: QuickContact? = null,
    val recipientName: String = "",
    val recipientPhone: String = "",
    val recipientEmail: String = "",
    val messageBody: String = ""
)

/**
 * 100% On-Device Deterministic Voice Messaging Parser.
 * Parses spoken replies, new message compose requests, phone numbers, email addresses,
 * and verbal confirmations strictly locally without calling Gemini.
 */
object LocalMessagingParser {

    private val wordDigitMap = mapOf(
        "zero" to "0", "oh" to "0", "o" to "0",
        "one" to "1", "won" to "1",
        "two" to "2", "to" to "2", "too" to "2",
        "three" to "3",
        "four" to "4", "for" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8", "ate" to "8",
        "nine" to "9"
    )

    fun detectConfirmationAction(normalized: String): ConfirmationAction? {
        val clean = normalized.trim()
        val cancelPhrases = setOf(
            "cancel", "cancel it", "discard", "discard it", "never mind", "nevermind",
            "forget it", "abort", "dont send", "do not send", "no cancel", "no", "nope"
        )
        if (cancelPhrases.contains(clean) || clean.startsWith("cancel ") || clean.startsWith("discard ")) {
            return ConfirmationAction.CANCEL
        }

        val editPhrases = setOf(
            "edit", "edit it", "change", "change it", "change message", "re record",
            "rerecord", "rewrite", "try again", "start over", "redictate"
        )
        if (editPhrases.contains(clean) || clean.startsWith("edit ") || clean.startsWith("change message")) {
            return ConfirmationAction.EDIT
        }

        val sendPhrases = setOf(
            "send", "send it", "yes", "yes send", "yes send it", "confirm", "confirm send",
            "go ahead", "ship it", "ok send", "okay send", "send now", "do it"
        )
        if (sendPhrases.contains(clean) || clean.startsWith("yes send")) {
            return ConfirmationAction.SEND
        }

        return null
    }

    /**
     * Parses spoken emails such as:
     * - "john dot smith at gmail dot com" -> "john.smith@gmail.com"
     * - "john.smith@gmail.com" -> "john.smith@gmail.com"
     */
    fun parseSpokenEmail(raw: String): String? {
        val trimmed = raw.trim().lowercase(Locale.US)
        if (trimmed.isBlank()) return null

        val directMatch = Regex("""[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}""").find(trimmed)
        if (directMatch != null) {
            return directMatch.value
        }

        if (!trimmed.contains(" at ") && !trimmed.contains("@")) return null

        var converted = " $trimmed "
            .replace(" at sign ", "@")
            .replace(" at the rate ", "@")
            .replace(" at ", "@")
            .replace(" dot ", ".")
            .replace(" period ", ".")
            .replace(" point ", ".")
            .replace(" underscore ", "_")
            .replace(" under score ", "_")
            .replace(" dash ", "-")
            .replace(" hyphen ", "-")
            .trim()

        // Collapse spaces only around email punctuation / inside the address candidate
        converted = converted.replace(Regex("""\s*@\s*"""), "@")
            .replace(Regex("""\s*\.\s*"""), ".")
            .replace(Regex("""\s*_\s*"""), "_")
            .replace(Regex("""\s*-\s*"""), "-")

        // Remove any remaining spaces if the whole string represents a spoken email
        val compact = converted.replace(" ", "")
        return Regex("""^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$""").find(compact)?.value
    }

    /**
     * Parses spoken or typed phone numbers such as:
     * - "123456", "0917 555 1234", "plus 63 917 555 1234", "one two three four five six"
     */
    fun parseSpokenPhoneNumber(raw: String): String? {
        val trimmed = raw.trim().lowercase(Locale.US)
            .replace(Regex("""^\s*plus\s+"""), "+")
        if (trimmed.isBlank()) return null

        // Direct digit pattern check first (supports +, spaces, hyphens, parentheses)
        val digitCountRaw = trimmed.count { it.isDigit() }
        if (digitCountRaw >= 5) {
            val cleaned = buildString {
                trimmed.forEachIndexed { idx, ch ->
                    when {
                        ch.isDigit() -> append(ch)
                        ch == '+' && isEmpty() -> append('+')
                        ch == '+' && idx > 0 && trimmed[idx - 1] == ' ' && isEmpty() -> append('+')
                    }
                }
            }
            if (cleaned.count { it.isDigit() } >= 5) return cleaned
        }

        // Convert spoken word digits (e.g., "plus one two three four five six")
        val tokens = trimmed.replace("-", " ").split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val builder = StringBuilder()
        var recognizedTokens = 0
        for (token in tokens) {
            when {
                token == "plus" && builder.isEmpty() -> {
                    builder.append('+')
                    recognizedTokens++
                }
                token.all { it.isDigit() } -> {
                    builder.append(token)
                    recognizedTokens++
                }
                wordDigitMap.containsKey(token) -> {
                    builder.append(wordDigitMap[token])
                    recognizedTokens++
                }
                else -> {
                    // Stop if unrelated trailing words exist before enough digits accumulated
                    if (builder.count { it.isDigit() } < 5) {
                        builder.clear()
                        recognizedTokens = 0
                    }
                }
            }
        }
        val candidate = builder.toString()
        return if (candidate.count { it.isDigit() } >= 5 && recognizedTokens >= 2) candidate else null
    }

    fun parseReplyCommand(rawUtterance: String): ReplyCommandParseResult? {
        val trimmed = rawUtterance.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase(Locale.US)

        // Avoid hijacking informational questions like "what is the reply to..."
        val questionStarters = listOf("what ", "why ", "how ", "when ", "who ", "explain ", "define ")
        if (questionStarters.any { lower.startsWith(it) }) return null

        val isReplyIntent = lower == "reply" ||
            lower == "reply back" ||
            lower == "reply to message" ||
            lower == "reply to last message" ||
            lower == "reply to that" ||
            lower.startsWith("reply ") ||
            lower.startsWith("reply back ") ||
            lower.startsWith("answer ")

        if (!isReplyIntent) return null

        val bodySplit = splitBodyClause(trimmed)
        val headerPart = bodySplit.first
        val bodyPart = bodySplit.second

        val channel = extractChannelMention(headerPart)
        var target = headerPart
            .replace(Regex("""^(?:reply\s+back|reply|answer)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:via|using|through|over)\s+(?:sms|text|whatsapp|telegram|viber|gmail|email)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bon\s+(?:sms|text|whatsapp|telegram|viber|gmail|email)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*(?:to|back to)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:the\s+last\s+message|last\s+message|the\s+message|message|that)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim(',', '.', ':')

        if (target.equals("back", ignoreCase = true)) target = ""

        return ReplyCommandParseResult(
            targetQuery = target.ifBlank { null },
            channel = channel,
            messageBody = bodyPart?.ifBlank { null }
        )
    }

    fun parseNewMessageCommand(
        rawUtterance: String,
        contacts: List<QuickContact>
    ): NewMessageParseResult? {
        val trimmed = rawUtterance.trim()
        if (trimmed.isBlank()) return null
        val lower = trimmed.lowercase(Locale.US)

        val questionStarters = listOf("what ", "why ", "how ", "when ", "who ", "explain ", "define ", "summarize ", "read ", "check ")
        if (questionStarters.any { lower.startsWith(it) }) return null

        val explicitComposeTriggers = listOf(
            "send a message", "send message", "compose a message", "compose message", "new message",
            "send an sms", "send sms", "send a text", "send text",
            "send a whatsapp", "send whatsapp",
            "send a telegram", "send telegram",
            "send a viber", "send viber",
            "send an email", "send email", "send a gmail", "send gmail", "compose email",
            "text to ", "whatsapp to ", "telegram to ", "viber to ", "email to ", "gmail to ", "message to "
        )

        val (headerPart, bodyPart) = splitBodyClause(trimmed)
        val shorthandMessagingStart = listOf("text ", "whatsapp ", "telegram ", "viber ", "email ", "gmail ", "message ")
            .any { lower.startsWith(it) } && (
            !bodyPart.isNullOrBlank() ||
                parseSpokenEmail(headerPart) != null ||
                parseSpokenPhoneNumber(headerPart) != null
            )

        val matchesTrigger = explicitComposeTriggers.any { prefix ->
            lower == prefix.trim() || lower.startsWith(prefix)
        } || shorthandMessagingStart
        if (!matchesTrigger) return null

        val explicitChannel = extractChannelMention(headerPart)

        var recipientClause = headerPart
            .replace(
                Regex(
                    """^(?:send\s+(?:a|an)?\s*(?:new\s+)?(?:whatsapp|telegram|viber|gmail|email|sms|text)\s*(?:message)?|compose\s+(?:a|an)?\s*(?:new\s+)?(?:email|message)|new\s+message|send\s+message|whatsapp|telegram|viber|gmail|email|message|text)\b""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(Regex("""\b(?:via|using|through|on|over)\s+(?:sms|text|whatsapp|telegram|viber|gmail|email)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*(?:to|for)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim(',', '.', ':')

        // Check for embedded "telephone 123456" / "phone 123456" / "number 123456" alongside a name
        var extractedPhone = ""
        var extractedEmail = ""
        val phoneMarkerMatch = Regex("""\b(?:telephone|phone|number|mobile|cell)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(recipientClause)
        if (phoneMarkerMatch != null) {
            val possiblePhone = parseSpokenPhoneNumber(phoneMarkerMatch.groupValues[1])
            if (possiblePhone != null) {
                extractedPhone = possiblePhone
                recipientClause = recipientClause.substring(0, phoneMarkerMatch.range.first).trim()
            }
        }

        if (extractedEmail.isEmpty()) {
            extractedEmail = parseSpokenEmail(recipientClause).orEmpty()
        }
        if (extractedPhone.isEmpty() && extractedEmail.isEmpty()) {
            extractedPhone = parseSpokenPhoneNumber(recipientClause).orEmpty()
        }

        val matchedContact = if (recipientClause.isNotBlank() && extractedEmail.isEmpty() && extractedPhone.isEmpty()) {
            QuickContactsRepository.findBestMatch(recipientClause, contacts)
        } else if (recipientClause.isNotBlank() && contacts.isNotEmpty()) {
            QuickContactsRepository.findBestMatch(recipientClause, contacts)
        } else {
            null
        }

        val finalChannel = when {
            explicitChannel != null -> explicitChannel
            extractedEmail.isNotBlank() -> MessagingChannel.GMAIL
            matchedContact != null -> matchedContact.preferredChannel
            else -> MessagingChannel.SMS
        }

        val finalPhone = extractedPhone.ifBlank { matchedContact?.phoneNumber.orEmpty() }
        val finalEmail = extractedEmail.ifBlank { matchedContact?.emailAddress.orEmpty() }
        val finalName = when {
            matchedContact != null -> matchedContact.displayName
            extractedEmail.isNotBlank() -> extractedEmail
            extractedPhone.isNotBlank() -> recipientClause.ifBlank { extractedPhone }
            else -> recipientClause
        }

        return NewMessageParseResult(
            channel = finalChannel,
            explicitChannelRequested = explicitChannel != null,
            matchedContact = matchedContact,
            recipientName = finalName,
            recipientPhone = finalPhone,
            recipientEmail = finalEmail,
            messageBody = bodyPart.orEmpty().trim()
        )
    }

    private fun extractChannelMention(text: String): MessagingChannel? {
        val lower = text.lowercase(Locale.US)
        return when {
            lower.contains("whatsapp") -> MessagingChannel.WHATSAPP
            lower.contains("telegram") -> MessagingChannel.TELEGRAM
            lower.contains("viber") -> MessagingChannel.VIBER
            lower.contains("gmail") || lower.contains("email") || lower.contains("e-mail") -> MessagingChannel.GMAIL
            lower.contains("sms") || lower.startsWith("text ") || lower.contains(" text") -> MessagingChannel.SMS
            else -> null
        }
    }

    private fun splitBodyClause(raw: String): Pair<String, String?> {
        val separators = listOf(
            Regex("""\bsaying\s+""", RegexOption.IGNORE_CASE),
            Regex("""\bwith\s+the\s+message\s+""", RegexOption.IGNORE_CASE),
            Regex("""\bmessage\s*:\s*""", RegexOption.IGNORE_CASE),
            Regex("""\bthat\s+reads\s+""", RegexOption.IGNORE_CASE),
            Regex("""\bthat\s+says\s+""", RegexOption.IGNORE_CASE),
            Regex("""\bthat\s+i(?:'m|\s+am|\s+will|\s+have)?\b""", RegexOption.IGNORE_CASE)
        )
        for (regex in separators) {
            val match = regex.find(raw)
            if (match != null) {
                val head = raw.substring(0, match.range.first).trim()
                val tailOffset = if (regex.pattern.contains("i(?:'m")) match.range.first + 5 else match.range.last + 1
                val tail = raw.substring(tailOffset.coerceAtMost(raw.length)).trim()
                return head to tail
            }
        }
        return raw to null
    }
}
