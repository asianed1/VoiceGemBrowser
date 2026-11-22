package com.example.messaging

import com.squareup.moshi.JsonClass
import java.util.Locale
import java.util.UUID

enum class MessagingChannel(
    val displayName: String,
    val primaryPackage: String?,
    val fallbackPackages: List<String>
) {
    SMS("SMS", null, emptyList()),
    WHATSAPP("WhatsApp", "com.whatsapp", listOf("com.whatsapp.w4b")),
    TELEGRAM("Telegram", "org.telegram.messenger", listOf("org.thunderdog.challegram", "org.telegram.messenger.web")),
    VIBER("Viber", "com.viber.voip", emptyList()),
    GMAIL("Gmail", "com.google.android.gm", emptyList()),
    OTHER("Message", null, emptyList());

    companion object {
        fun fromAppNameOrPackage(appName: String?, packageName: String?): MessagingChannel {
            val combined = "${appName.orEmpty()} ${packageName.orEmpty()}".lowercase(Locale.US)
            return when {
                combined.contains("whatsapp") -> WHATSAPP
                combined.contains("telegram") || combined.contains("challegram") -> TELEGRAM
                combined.contains("viber") -> VIBER
                combined.contains("gmail") || combined.contains(".gm") || combined.contains("email") -> GMAIL
                combined.contains("sms") || combined.contains("messaging") || combined.contains("mms") || combined.contains("messages") -> SMS
                else -> OTHER
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class QuickContact(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val spokenAlias: String = "",
    val phoneNumber: String = "",
    val emailAddress: String = "",
    val preferredChannel: MessagingChannel = MessagingChannel.SMS
)

@JsonClass(generateAdapter = true)
data class QuickContactsEnvelope(
    val contacts: List<QuickContact> = emptyList()
)

enum class MessagingMode {
    SILENT_REPLY,
    NEW_MESSAGE
}

enum class ComposeStage {
    NEEDS_RECIPIENT,
    DICTATING_BODY,
    AWAITING_CONFIRMATION
}

data class MessagingDraft(
    val id: String = UUID.randomUUID().toString(),
    val mode: MessagingMode,
    val stage: ComposeStage,
    val channel: MessagingChannel,
    val recipientName: String = "",
    val recipientPhone: String = "",
    val recipientEmail: String = "",
    val subject: String = "",
    val messageBody: String = "",
    val replyMessageId: String? = null,
    val replyPackageName: String? = null,
    val hasDirectRemoteInput: Boolean = false,
    val originalMessageSnippet: String? = null
) {
    fun hasRecipientInfo(): Boolean {
        if (mode == MessagingMode.SILENT_REPLY) {
            return !replyMessageId.isNullOrBlank() || recipientName.isNotBlank()
        }
        return when (channel) {
            MessagingChannel.GMAIL -> recipientEmail.isNotBlank()
            MessagingChannel.SMS, MessagingChannel.WHATSAPP ->
                recipientPhone.isNotBlank() || recipientName.isNotBlank()
            MessagingChannel.TELEGRAM, MessagingChannel.VIBER, MessagingChannel.OTHER ->
                recipientPhone.isNotBlank() || recipientEmail.isNotBlank() || recipientName.isNotBlank()
        }
    }

    fun hasSendTarget(): Boolean {
        if (mode == MessagingMode.SILENT_REPLY && hasDirectRemoteInput) return true
        return when (channel) {
            MessagingChannel.GMAIL -> recipientEmail.isNotBlank()
            MessagingChannel.SMS -> recipientPhone.isNotBlank()
            MessagingChannel.WHATSAPP -> recipientPhone.isNotBlank()
            MessagingChannel.TELEGRAM, MessagingChannel.VIBER, MessagingChannel.OTHER -> true
        }
    }
}
