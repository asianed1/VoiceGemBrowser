package com.example.messaging

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.ContactsContract
import com.example.security.SecureKeyStoreHelper
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.min

class QuickContactsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _contacts = MutableStateFlow(loadContacts())
    val contacts: StateFlow<List<QuickContact>> = _contacts.asStateFlow()

    private fun loadContacts(): List<QuickContact> {
        val raw = prefs.getString(KEY_CONTACTS_JSON, "") ?: ""
        if (raw.isBlank()) return emptyList()
        val decrypted = SecureKeyStoreHelper.decrypt(raw)
        if (decrypted.isBlank()) return emptyList()
        return deserializeContacts(decrypted)
    }

    fun addOrUpdateContact(contact: QuickContact): Boolean {
        val cleanName = contact.displayName.trim()
        val cleanPhone = contact.phoneNumber.trim()
        val cleanEmail = contact.emailAddress.trim()
        if (cleanName.isBlank() || (cleanPhone.isBlank() && cleanEmail.isBlank())) {
            return false
        }

        val normalizedContact = contact.copy(
            displayName = cleanName,
            spokenAlias = contact.spokenAlias.trim(),
            phoneNumber = cleanPhone,
            emailAddress = cleanEmail
        )

        val current = _contacts.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == normalizedContact.id }
        if (existingIdx >= 0) {
            current[existingIdx] = normalizedContact
        } else {
            if (current.size >= MAX_CONTACTS) {
                current.removeAt(current.lastIndex)
            }
            current.add(0, normalizedContact)
        }
        persist(current)
        return true
    }

    fun deleteContact(contactId: String) {
        val updated = _contacts.value.filterNot { it.id == contactId }
        persist(updated)
    }

    fun clearAll() {
        persist(emptyList())
    }

    private fun persist(list: List<QuickContact>) {
        val json = serializeContacts(list)
        val encrypted = SecureKeyStoreHelper.encrypt(json)
        prefs.edit().putString(KEY_CONTACTS_JSON, encrypted).apply()
        _contacts.value = list
    }

    companion object {
        private const val PREFS_NAME = "voice_gem_quick_contacts_prefs"
        private const val KEY_CONTACTS_JSON = "encrypted_quick_contacts"
        private const val MAX_CONTACTS = 30

        private val moshi: Moshi by lazy { Moshi.Builder().build() }
        private val envelopeAdapter by lazy { moshi.adapter(QuickContactsEnvelope::class.java) }

        internal fun serializeContacts(list: List<QuickContact>): String {
            return envelopeAdapter.toJson(QuickContactsEnvelope(contacts = list))
        }

        internal fun deserializeContacts(json: String): List<QuickContact> {
            return try {
                envelopeAdapter.fromJson(json)?.contacts
                    ?.filter { it.displayName.isNotBlank() }
                    .orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun findBestMatch(query: String, contacts: List<QuickContact>): QuickContact? {
            val needle = query.trim().lowercase(Locale.US).replace(Regex("""[^a-z0-9\s]"""), "").trim()
            if (needle.isBlank() || contacts.isEmpty()) return null

            // 1. Exact display name or spoken alias match
            contacts.firstOrNull { c ->
                normalizeName(c.displayName) == needle ||
                    (c.spokenAlias.isNotBlank() && normalizeName(c.spokenAlias) == needle)
            }?.let { return it }

            // 2. Token / substring containment match
            contacts.firstOrNull { c ->
                val dn = normalizeName(c.displayName)
                val alias = normalizeName(c.spokenAlias)
                (dn.isNotBlank() && (dn.contains(needle) || needle.contains(dn))) ||
                    (alias.isNotBlank() && (alias.contains(needle) || needle.contains(alias)))
            }?.let { return it }

            // 3. First-name or multi-token match
            val needleTokens = needle.split(" ").filter { it.length >= 2 }
            if (needleTokens.isNotEmpty()) {
                contacts.firstOrNull { c ->
                    val nameTokens = (normalizeName(c.displayName) + " " + normalizeName(c.spokenAlias))
                        .split(" ")
                        .filter { it.length >= 2 }
                    needleTokens.any { nt -> nameTokens.any { it == nt } }
                }?.let { return it }
            }

            // 4. Lightweight fuzzy match (Levenshtein <= 2 for speech-to-text spelling variants like Jon/John)
            var bestContact: QuickContact? = null
            var bestDistance = 3
            for (contact in contacts) {
                val candidates = listOfNotNull(
                    normalizeName(contact.displayName).takeIf { it.isNotBlank() },
                    normalizeName(contact.spokenAlias).takeIf { it.isNotBlank() }
                ) + normalizeName(contact.displayName).split(" ").filter { it.length >= 3 }

                for (candidate in candidates) {
                    val dist = levenshtein(needle, candidate)
                    if (dist < bestDistance) {
                        bestDistance = dist
                        bestContact = contact
                    }
                }
            }
            return bestContact
        }

        private fun normalizeName(raw: String): String {
            return raw.trim().lowercase(Locale.US).replace(Regex("""[^a-z0-9\s]"""), "").replace(Regex("""\s+"""), " ").trim()
        }

        private fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = i - 1
                dp[0] = i
                for (j in 1..b.length) {
                    val temp = dp[j]
                    dp[j] = if (a[i - 1] == b[j - 1]) {
                        prev
                    } else {
                        1 + min(prev, min(dp[j], dp[j - 1]))
                    }
                    prev = temp
                }
            }
            return dp[b.length]
        }

        /**
         * Extracts phone or email contact details from a single-pick Intent result URI
         * without requiring the READ_CONTACTS manifest permission.
         */
        fun extractFromPickedUri(context: Context, uri: Uri): QuickContact? {
            return try {
                val cr = context.contentResolver
                val projection = arrayOf(
                    ContactsContract.Data.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.Data.MIMETYPE
                )
                cr.query(uri, projection, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
                    val dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)

                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawData = if (dataIdx >= 0) cursor.getString(dataIdx).orEmpty().trim() else ""
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx).orEmpty() else ""

                    if (rawData.isBlank()) return null

                    val isEmail = mime == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ||
                        rawData.contains("@")
                    QuickContact(
                        displayName = name.ifBlank { rawData },
                        phoneNumber = if (!isEmail) rawData else "",
                        emailAddress = if (isEmail) rawData else "",
                        preferredChannel = if (isEmail) MessagingChannel.GMAIL else MessagingChannel.SMS
                    )
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
