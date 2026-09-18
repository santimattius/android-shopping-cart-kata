package com.pedidosya.kata.di

import android.content.Context
import androidx.room.Room
import com.pedidosya.kata.data.local.KataDatabase
import com.pedidosya.kata.data.remote.CartApi
import com.pedidosya.kata.data.remote.CouponApi
import com.pedidosya.kata.data.repository.CartRepositoryImpl
import com.pedidosya.kata.data.repository.CouponRepositoryImpl
import com.pedidosya.kata.domain.repository.CartRepository
import com.pedidosya.kata.domain.repository.CouponRepository
import com.pedidosya.kata.domain.usecase.ValidateCoupon
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual, framework-free dependency graph for the app.
 *
 * [couponRepository] never touches Room: coupon validation is always remote, per design.
 */
interface AppContainer {
    val okHttpClient: OkHttpClient
    val retrofit: Retrofit
    val cartRepository: CartRepository
    val couponRepository: CouponRepository
    val validateCoupon: ValidateCoupon
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

    private val cartApi: CartApi by lazy { retrofit.create(CartApi::class.java) }

    private val database: KataDatabase by lazy {
        Room.databaseBuilder(context, KataDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    override val cartRepository: CartRepository by lazy {
        CartRepositoryImpl(api = cartApi, dao = database.cartItemDao())
    }

    private val couponApi: CouponApi by lazy { retrofit.create(CouponApi::class.java) }

    override val couponRepository: CouponRepository by lazy {
        CouponRepositoryImpl(api = couponApi)
    }

    override val validateCoupon: ValidateCoupon by lazy {
        ValidateCoupon(repository = couponRepository)
    }

    companion object {
        private const val BASE_URL = "https://dummyjson.com/"
        private const val DATABASE_NAME = "kata-cart.db"
    }
}
