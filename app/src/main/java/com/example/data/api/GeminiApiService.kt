package com.example.data.api

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.GeminiRequest
import com.example.data.model.GeminiResponse
import com.example.security.SecureKeyStoreHelper
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    @Volatile
    private var androidPackageName: String? = null

    @Volatile
    private var androidCertSha1: String? = null

    fun configureAppIdentity(context: Context) {
        if (androidPackageName != null) return
        val appCtx = context.applicationContext
        androidPackageName = appCtx.packageName
        androidCertSha1 = SecureKeyStoreHelper.computeAppCertSha1Hex(appCtx)
    }

    private val appIdentityInterceptor = Interceptor { chain ->
        val builder = chain.request().newBuilder()
        androidPackageName?.let { pkg ->
            builder.header("X-Android-Package", pkg)
        }
        androidCertSha1?.let { cert ->
            builder.header("X-Android-Cert", cert)
        }
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        redactHeader("x-goog-api-key")
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(appIdentityInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val moshi: Moshi = Moshi.Builder().build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}
