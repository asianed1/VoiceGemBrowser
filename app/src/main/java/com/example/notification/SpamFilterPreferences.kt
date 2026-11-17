package com.example.notification

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpamFilterPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _spamFilterEnabled = MutableStateFlow(loadSpamFilterEnabled())
    val spamFilterEnabled: StateFlow<Boolean> = _spamFilterEnabled.asStateFlow()

    private val _mutedSenders = MutableStateFlow(loadMutedSenders())
    val mutedSenders: StateFlow<Set<String>> = _mutedSenders.asStateFlow()

    private val _mutedKeywords = MutableStateFlow(loadMutedKeywords())
    val mutedKeywords: StateFlow<Set<String>> = _mutedKeywords.asStateFlow()

    private fun loadSpamFilterEnabled(): Boolean {
        return prefs.getBoolean(KEY_SPAM_FILTER_ENABLED, true)
    }

    private fun loadMutedSenders(): Set<String> {
        return prefs.getStringSet(KEY_MUTED_SENDERS, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSortedSet(String.CASE_INSENSITIVE_ORDER)
            ?: emptySet()
    }

    private fun loadMutedKeywords(): Set<String> {
        return prefs.getStringSet(KEY_MUTED_KEYWORDS, emptySet())
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSortedSet(String.CASE_INSENSITIVE_ORDER)
            ?: emptySet()
    }

    fun isSpamFilterEnabledSync(): Boolean = prefs.getBoolean(KEY_SPAM_FILTER_ENABLED, true)

    fun getMutedSendersSync(): Set<String> = loadMutedSenders()

    fun getMutedKeywordsSync(): Set<String> = loadMutedKeywords()

    fun setSpamFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPAM_FILTER_ENABLED, enabled).apply()
        _spamFilterEnabled.value = enabled
    }

    fun muteSender(sender: String) {
        val cleaned = sender.trim()
        if (cleaned.isEmpty()) return
        val updated = loadMutedSenders().toMutableSet()
        updated.add(cleaned)
        prefs.edit().putStringSet(KEY_MUTED_SENDERS, updated).apply()
        _mutedSenders.value = updated.toSortedSet(String.CASE_INSENSITIVE_ORDER)
    }

    fun unmuteSender(sender: String) {
        val targetLower = sender.trim().lowercase(Locale.US)
        val updated = loadMutedSenders()
            .filterNot { it.lowercase(Locale.US) == targetLower }
            .toMutableSet()
        prefs.edit().putStringSet(KEY_MUTED_SENDERS, updated).apply()
        _mutedSenders.value = updated.toSortedSet(String.CASE_INSENSITIVE_ORDER)
    }

    fun addMutedKeyword(keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.length < 2) return
        val updated = loadMutedKeywords().toMutableSet()
        updated.add(cleaned)
        prefs.edit().putStringSet(KEY_MUTED_KEYWORDS, updated).apply()
        _mutedKeywords.value = updated.toSortedSet(String.CASE_INSENSITIVE_ORDER)
    }

    fun removeMutedKeyword(keyword: String) {
        val targetLower = keyword.trim().lowercase(Locale.US)
        val updated = loadMutedKeywords()
            .filterNot { it.lowercase(Locale.US) == targetLower }
            .toMutableSet()
        prefs.edit().putStringSet(KEY_MUTED_KEYWORDS, updated).apply()
        _mutedKeywords.value = updated.toSortedSet(String.CASE_INSENSITIVE_ORDER)
    }

    companion object {
        private const val PREFS_NAME = "voicegem_spam_filter_prefs"
        private const val KEY_SPAM_FILTER_ENABLED = "spam_filter_enabled"
        private const val KEY_MUTED_SENDERS = "muted_senders_set"
        private const val KEY_MUTED_KEYWORDS = "muted_keywords_set"
    }
}
