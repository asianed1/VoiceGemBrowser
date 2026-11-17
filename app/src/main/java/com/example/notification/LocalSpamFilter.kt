package com.example.notification

import java.util.Locale

enum class FilterVerdict {
    ALLOW,
    BLOCK_OTP_SENSITIVE,
    SPAM_PROMO,
    USER_MUTED
}

/**
 * 100% on-device classifier that protects user privacy and attention:
 * 1. OTP / 2FA codes are ALWAYS blocked silently at capture & synthesis time so
 *    sensitive passcodes are never stored in memory or spoken aloud over Bluetooth.
 * 2. Promotional / marketing spam & bulk newsletters are routed to the inspectable Filtered queue.
 * 3. User-muted senders & keywords are filtered cleanly.
 */
object LocalSpamFilter {

    private val numericOtpRegex = Regex("""(?<!\d)(?:\d{4,8}|\d{3}[-\s]\d{3})(?!\d)""")
    private val googleOtpRegex = Regex("""\bg-\d{5,8}\b""", RegexOption.IGNORE_CASE)

    private val strongOtpPhrases = listOf(
        "verification code",
        "security code",
        "one-time password",
        "one time password",
        "one-time passcode",
        "one time passcode",
        "one-time code",
        "one time code",
        "2fa code",
        "two-factor",
        "two factor",
        "authentication code",
        "auth code",
        "login code",
        "sign-in code",
        "signin code",
        "confirmation code",
        "temporary code",
        "reset code",
        "your otp",
        "otp is",
        "otp:",
        "passcode is",
        "passcode:",
        "is your code",
        "your code is",
        "mfa code"
    )

    private val otpContextHints = listOf(
        "otp",
        "2fa",
        "mfa",
        "passcode",
        "pin",
        "verify",
        "verification",
        "authenticate",
        "do not share",
        "don't share",
        "never share",
        "valid for",
        "expires in",
        "use code",
        "enter code",
        "enter the code"
    )

    private val strongPromoPhrases = listOf(
        "unsubscribe",
        "reply stop",
        "text stop",
        "stop to opt out",
        "stop to opt-out",
        "opt out of future",
        "manage email preferences",
        "manage preferences",
        "flash sale",
        "limited time offer",
        "limited-time offer",
        "promo code",
        "discount code",
        "coupon code",
        "shop now",
        "buy now",
        "claim your",
        "you've won",
        "you have won",
        "free gift",
        "cashback",
        "cash back",
        "special offer",
        "exclusive deal",
        "exclusive offer",
        "clearance sale",
        "mega sale",
        "black friday",
        "cyber monday",
        "pre-approved",
        "loan approved",
        "act now",
        "save up to",
        "earn up to",
        "off your order",
        "off your next"
    )

    private val percentOffRegex = Regex("""\b\d{1,2}%\s*off\b""", RegexOption.IGNORE_CASE)

    private val automatedSenderHints = listOf(
        "noreply",
        "no-reply",
        "donotreply",
        "do-not-reply",
        "promotions",
        "marketing",
        "newsletter",
        "deals@",
        "offers@"
    )

    fun evaluate(
        sender: String,
        text: String,
        mutedSenders: Set<String> = emptySet(),
        mutedKeywords: Set<String> = emptySet(),
        promoFilterEnabled: Boolean = true
    ): FilterVerdict {
        // 1. Always-on OTP / 2FA protection (never bypassed, never stored, never read aloud)
        if (isSensitiveOtpOr2fa(sender, text)) {
            return FilterVerdict.BLOCK_OTP_SENSITIVE
        }

        val senderLower = sender.trim().lowercase(Locale.US)
        val textLower = text.trim().lowercase(Locale.US)

        // 2. User-muted senders or keywords
        if (mutedSenders.any { muted ->
                val target = muted.trim().lowercase(Locale.US)
                target.isNotEmpty() && (senderLower == target || senderLower.contains(target))
            }
        ) {
            return FilterVerdict.USER_MUTED
        }

        if (mutedKeywords.any { kw ->
                val target = kw.trim().lowercase(Locale.US)
                target.isNotEmpty() && (textLower.contains(target) || senderLower.contains(target))
            }
        ) {
            return FilterVerdict.USER_MUTED
        }

        // 3. On-device promo & noise spam detection
        if (promoFilterEnabled && isPromotionalOrNoiseSpam(sender, text)) {
            return FilterVerdict.SPAM_PROMO
        }

        return FilterVerdict.ALLOW
    }

    fun isSensitiveOtpOr2fa(sender: String, text: String): Boolean {
        val combined = "${sender.trim()} ${text.trim()}".lowercase(Locale.US)
        if (combined.isBlank()) return false

        if (googleOtpRegex.containsMatchIn(combined)) {
            return true
        }

        if (strongOtpPhrases.any { combined.contains(it) }) {
            return true
        }

        val hasDigitCode = numericOtpRegex.containsMatchIn(combined)
        if (hasDigitCode && otpContextHints.any { combined.contains(it) }) {
            return true
        }

        return false
    }

    fun isPromotionalOrNoiseSpam(sender: String, text: String): Boolean {
        val textLower = text.trim().lowercase(Locale.US)
        val senderLower = sender.trim().lowercase(Locale.US)
        if (textLower.isBlank()) return false

        if (strongPromoPhrases.any { textLower.contains(it) }) {
            return true
        }

        if (percentOffRegex.containsMatchIn(textLower)) {
            return true
        }

        val hasAutomatedSender = automatedSenderHints.any { senderLower.contains(it) }
        val hasDealNoise = textLower.contains("sale") ||
            textLower.contains("discount") ||
            textLower.contains("offer") ||
            textLower.contains("deal") ||
            textLower.contains("voucher") ||
            textLower.contains("view in browser") ||
            textLower.contains("newsletter")

        if (hasAutomatedSender && hasDealNoise) {
            return true
        }

        return false
    }
}
