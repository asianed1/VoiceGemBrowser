# ==============================================================================
# VoiceGemBrowser R8 Optimization, Shrinking & Obfuscation Rules (v12.0)
# ==============================================================================
# Third-party libraries (Retrofit, OkHttp, Coroutines, Credential Manager, Moshi)
# ship their own targeted AAR consumer rules. Keeping only specific reflection entry
# points lets R8 shrink, optimize, and obfuscate unused bytecode cleanly.

# Preserve stack trace source files and line numbers for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Moshi KSP-generated JsonAdapters & annotated models (no kotlin-reflect needed)
-dontwarn com.squareup.moshi.**
-keepnames @com.squareup.moshi.JsonClass class com.example.data.model.**
-keepnames @com.squareup.moshi.JsonClass class com.example.media.**
-keepnames @com.squareup.moshi.JsonClass class com.example.messaging.**
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclassmembers class * extends com.squareup.moshi.JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}

# Retrofit HTTP service interfaces & annotated methods (allow obfuscation of names)
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn javax.annotation.**
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
