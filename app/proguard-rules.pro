# ==============================================================================
# VoiceGemBrowser ProGuard / R8 Optimization & Minification Rules
# ==============================================================================

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Moshi & Moshi Kotlin Reflect
-dontwarn com.squareup.moshi.**
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.data.model.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class kotlin.reflect.** { *; }

# Retrofit 2 & OkHttp 3
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# Google Credential Manager & Google ID Sign-in
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Data layer models, preferences & Version 7.0 voice/local modules
-keep class com.example.data.user.** { *; }
-keep class com.example.data.repository.** { *; }
-keep class com.example.media.RadioStation { *; }
-keep class com.example.media.PodcastEpisode { *; }
-keep class com.example.media.PodcastSearchResult { *; }
-keep class com.example.media.ItunesSearchResponse { *; }
-keep class com.example.media.ItunesPodcastItem { *; }
-keep class com.example.notification.CapturedMessage { *; }
-keep class com.example.notification.FilterVerdict { *; }
-keep class com.example.notification.LocalSpamFilter { *; }
-keep class com.example.notification.LocalMessageSynthesizer { *; }
-keep class com.example.security.** { *; }
-keep class com.example.voice.VoiceGenderPreference { *; }
-keep class com.example.voice.VoiceSwitchResult { *; }
