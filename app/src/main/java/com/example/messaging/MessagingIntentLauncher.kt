package com.example.messaging

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.TelephonyManager
import java.util.Locale

data class LaunchResult(
    val launched: Boolean,
    val feedbackMessage: String
)

/**
 * Zero-permission, Play-Policy-compliant Intent handoff for pre-filled new messages
 * and fallback compose across SMS, Gmail, WhatsApp, Telegram, and Viber.
 */
object MessagingIntentLauncher {

    private val countryCallingCodes = mapOf(
        "US" to "1", "CA" to "1",
        "PH" to "63",
        "GB" to "44", "UK" to "44",
        "AU" to "61",
        "SG" to "65",
        "IN" to "91",
        "JP" to "81",
        "DE" to "49",
        "FR" to "33",
        "ES" to "34",
        "IT" to "39",
        "AE" to "971"
    )

    fun launchPreFilledCompose(context: Context, draft: MessagingDraft): LaunchResult {
        val body = draft.messageBody.trim()
        return when (draft.channel) {
            MessagingChannel.SMS -> launchSms(context, draft.recipientPhone, body)
            MessagingChannel.GMAIL -> launchGmailOrEmail(context, draft.recipientEmail, draft.subject, body)
            MessagingChannel.WHATSAPP -> launchWhatsApp(context, draft.recipientPhone, body)
            MessagingChannel.TELEGRAM -> launchTargetedShare(
                context = context,
                channel = MessagingChannel.TELEGRAM,
                body = body,
                recipientHint = draft.recipientName
            )
            MessagingChannel.VIBER -> launchTargetedShare(
                context = context,
                channel = MessagingChannel.VIBER,
                body = body,
                recipientHint = draft.recipientName
            )
            MessagingChannel.OTHER -> launchGenericTextShare(context, body, draft.replyPackageName)
        }
    }

    private fun launchSms(context: Context, phone: String, body: String): LaunchResult {
        val cleanPhone = phone.trim()
        val uri = if (cleanPhone.isNotEmpty()) {
            Uri.parse("smsto:${Uri.encode(cleanPhone)}")
        } else {
            Uri.parse("smsto:")
        }
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(
            context = context,
            intent = intent,
            successMessage = "Opened your SMS app pre-filled. Tap Send to deliver.",
            missingMessage = "No SMS app is available on this device."
        )
    }

    private fun launchGmailOrEmail(
        context: Context,
        email: String,
        subject: String,
        body: String
    ): LaunchResult {
        val cleanEmail = email.trim()
        val uriBuilder = StringBuilder("mailto:")
        if (cleanEmail.isNotEmpty()) {
            uriBuilder.append(Uri.encode(cleanEmail))
        }
        val uri = Uri.parse(uriBuilder.toString())
        val gmailPkg = findInstalledPackage(context, MessagingChannel.GMAIL)
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (cleanEmail.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanEmail))
            }
            if (subject.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            putExtra(Intent.EXTRA_TEXT, body)
            if (gmailPkg != null) {
                setPackage(gmailPkg)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val result = startSafely(
            context = context,
            intent = intent,
            successMessage = "Opened email compose pre-filled. Tap Send when ready.",
            missingMessage = "No email app found on this device."
        )
        if (result.launched || gmailPkg == null) return result

        // Retry without explicit Gmail package filter if user's default handler differs
        intent.setPackage(null)
        return startSafely(
            context = context,
            intent = intent,
            successMessage = "Opened email compose pre-filled. Tap Send when ready.",
            missingMessage = "No email app found on this device."
        )
    }

    private fun launchWhatsApp(context: Context, phone: String, body: String): LaunchResult {
        val whatsappPkg = findInstalledPackage(context, MessagingChannel.WHATSAPP)
            ?: return LaunchResult(false, "WhatsApp does not appear to be installed on this device.")

        val e164Digits = formatForWhatsAppUri(phone, detectCountryIso(context))
        if (e164Digits != null) {
            val waUrl = "https://api.whatsapp.com/send?phone=$e164Digits&text=${Uri.encode(body)}"
            val directIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                setPackage(whatsappPkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val directAttempt = startSafely(
                context = context,
                intent = directIntent,
                successMessage = "Opened WhatsApp chat pre-filled. Tap Send to deliver.",
                missingMessage = "Could not open WhatsApp directly."
            )
            if (directAttempt.launched) return directAttempt
        }

        return launchTargetedShare(context, MessagingChannel.WHATSAPP, body, "")
    }

    private fun launchTargetedShare(
        context: Context,
        channel: MessagingChannel,
        body: String,
        recipientHint: String
    ): LaunchResult {
        val pkg = findInstalledPackage(context, channel)
            ?: return LaunchResult(false, "${channel.displayName} does not appear to be installed on this device.")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val targetPhrase = if (recipientHint.isNotBlank()) " for $recipientHint" else ""
        return startSafely(
            context = context,
            intent = intent,
            successMessage = "Opened ${channel.displayName} with your message ready$targetPhrase. Select chat and tap Send.",
            missingMessage = "Could not open ${channel.displayName}."
        )
    }

    private fun launchGenericTextShare(context: Context, body: String, preferredPackage: String?): LaunchResult {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            if (!preferredPackage.isNullOrBlank()) {
                setPackage(preferredPackage)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startSafely(
            context = context,
            intent = intent,
            successMessage = "Opened messaging app with your draft ready.",
            missingMessage = "Could not open the messaging app."
        )
    }

    private fun startSafely(
        context: Context,
        intent: Intent,
        successMessage: String,
        missingMessage: String
    ): LaunchResult {
        return try {
            context.startActivity(intent)
            LaunchResult(true, successMessage)
        } catch (_: Exception) {
            LaunchResult(false, missingMessage)
        }
    }

    fun findInstalledPackage(context: Context, channel: MessagingChannel): String? {
        val candidates = buildList {
            channel.primaryPackage?.let { add(it) }
            addAll(channel.fallbackPackages)
        }
        val pm = context.packageManager
        for (pkg in candidates) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // Continue checking fallbacks
            } catch (_: Exception) {
                // Ignore
            }
        }
        return null
    }

    private fun detectCountryIso(context: Context): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val sim = tm?.simCountryIso?.uppercase(Locale.US).orEmpty()
            val net = tm?.networkCountryIso?.uppercase(Locale.US).orEmpty()
            sim.ifBlank { net }.ifBlank { Locale.getDefault().country.uppercase(Locale.US) }
        } catch (_: Exception) {
            Locale.getDefault().country.uppercase(Locale.US)
        }
    }

    /**
     * Converts local or international phone numbers into country-coded digits for wa.me / api.whatsapp.com.
     * Example: "0917 555 1234" in PH -> "639175551234"; "+1 (555) 123-4567" -> "15551234567".
     */
    internal fun formatForWhatsAppUri(rawPhone: String, countryIso: String = Locale.getDefault().country): String? {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) return null

        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length < 7) return null

        if (hasPlus) return digitsOnly
        if (digitsOnly.startsWith("00") && digitsOnly.length > 9) {
            return digitsOnly.drop(2)
        }

        val callingCode = countryCallingCodes[countryIso.uppercase(Locale.US)]
        if (callingCode != null) {
            if (digitsOnly.startsWith("0") && digitsOnly.length in 9..12) {
                return callingCode + digitsOnly.drop(1)
            }
            if (callingCode == "1" && digitsOnly.length == 10) {
                return "1$digitsOnly"
            }
        }
        return digitsOnly
    }
}
