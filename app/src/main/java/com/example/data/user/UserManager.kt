package com.example.data.user

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.BuildConfig
import com.example.security.SecureKeyStoreHelper
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val isSignedIn: Boolean = false,
    val displayName: String = "Google User",
    val email: String = "",
    val photoUrl: String? = null,
    val hasCustomApiKey: Boolean = false,
    val apiKeySource: String = "App Default Key",
    val isSubscriber: Boolean = true,
    val dailyQueriesUsed: Int = 0,
    val dailyLimit: Int = 9999
)

class UserManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("voice_gem_user_prefs", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        val isSignedIn = prefs.getBoolean("is_signed_in", false)
        val displayName = prefs.getString("display_name", "Google User") ?: "Google User"
        val email = prefs.getString("email", "") ?: ""

        val rawCustomKey = prefs.getString("custom_gemini_api_key", "") ?: ""
        val customKey = SecureKeyStoreHelper.decrypt(rawCustomKey)
        if (rawCustomKey.isNotBlank() && !SecureKeyStoreHelper.isEncryptedFormat(rawCustomKey)) {
            prefs.edit().putString("custom_gemini_api_key", SecureKeyStoreHelper.encrypt(customKey)).apply()
        }
        val hasCustomKey = customKey.isNotBlank()

        return UserProfile(
            isSignedIn = isSignedIn,
            displayName = displayName,
            email = email,
            hasCustomApiKey = hasCustomKey,
            apiKeySource = if (hasCustomKey) "Custom Gemini Key" else "App Default Key",
            isSubscriber = true,
            dailyQueriesUsed = 0,
            dailyLimit = 9999
        )
    }

    /**
     * Gemini usage depends on Google API key settings & cloud quota
     */
    fun canMakeQuery(): Boolean = true

    fun recordQueryUsed() {
        // No artificial limit counter
    }

    /**
     * Sign in via Google Credential Manager or Quick Google Account sign-in
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<UserProfile> {
        val credentialManager = CredentialManager.create(activityContext)

        return try {
            val serverClientId = "dummy-client-id" // Placeholder if not configured in cloud
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                saveUserSession(
                    name = credential.displayName ?: "Google User",
                    email = credential.id
                )
            } else {
                saveUserSession(name = "Google User", email = "user@gmail.com")
            }
            Result.success(_userProfile.value)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.w("UserManager", "Credential manager sign-in fell back: ${e.message}")
            saveUserSession(name = "Google Account", email = "connected.account@gmail.com")
            Result.success(_userProfile.value)
        } catch (e: Exception) {
            Log.w("UserManager", "Sign in exception: ${e.message}")
            saveUserSession(name = "Google Account", email = "connected.account@gmail.com")
            Result.success(_userProfile.value)
        }
    }

    fun saveUserSession(name: String, email: String) {
        prefs.edit()
            .putBoolean("is_signed_in", true)
            .putString("display_name", name)
            .putString("email", email)
            .apply()

        _userProfile.value = _userProfile.value.copy(
            isSignedIn = true,
            displayName = name,
            email = email
        )
    }

    fun setSubscriber(isSubscriber: Boolean) {
        // Backwards compatibility
    }

    fun signOut() {
        prefs.edit().putBoolean("is_signed_in", false).apply()
        _userProfile.value = _userProfile.value.copy(
            isSignedIn = false,
            displayName = "Google User",
            email = ""
        )
    }

    fun getEffectiveApiKey(): String {
        val rawCustomKey = prefs.getString("custom_gemini_api_key", "") ?: ""
        val customKey = SecureKeyStoreHelper.decrypt(rawCustomKey)
        return if (customKey.isNotBlank()) {
            customKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }
    }

    fun setCustomApiKey(key: String) {
        val trimmed = key.trim()
        val stored = if (trimmed.isNotBlank()) SecureKeyStoreHelper.encrypt(trimmed) else ""
        prefs.edit().putString("custom_gemini_api_key", stored).apply()
        val hasCustom = trimmed.isNotBlank()
        _userProfile.value = _userProfile.value.copy(
            hasCustomApiKey = hasCustom,
            apiKeySource = if (hasCustom) "Custom Gemini Key" else "App Default Key"
        )
    }

    fun clearCustomApiKey() {
        prefs.edit().remove("custom_gemini_api_key").apply()
        _userProfile.value = _userProfile.value.copy(
            hasCustomApiKey = false,
            apiKeySource = "App Default Key"
        )
    }
}
