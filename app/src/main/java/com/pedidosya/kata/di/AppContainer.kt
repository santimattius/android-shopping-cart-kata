package com.pedidosya.kata.di

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual, framework-free dependency graph for the app.
 *
 * Concrete repositories ([com.pedidosya.kata.domain.repository.CartRepository],
 * [com.pedidosya.kata.domain.repository.CouponRepository]) and their remote APIs are wired here
 * in later phases, once the corresponding data-layer classes exist. For now this exposes only
 * the shared networking primitives every future API/repository will build on.
 */
interface AppContainer {
    val okHttpClient: OkHttpClient
    val retrofit: Retrofit
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val json: Json by lazy {
        Json { ignoreUnknownKeys = true }
    }

    override val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    override val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    companion object {
        // Placeholder until the real cart/coupon mock endpoints are wired in Phase 3/5.
        private const val BASE_URL = "https://raw.githubusercontent.com/"
    }
}
