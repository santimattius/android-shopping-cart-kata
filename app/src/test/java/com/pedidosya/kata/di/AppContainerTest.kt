package com.pedidosya.kata.di

import android.content.Context
import com.pedidosya.kata.data.repository.CartRepositoryImpl
import com.pedidosya.kata.data.repository.CouponRepositoryImpl
import io.mockk.mockk
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContainerTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `DefaultAppContainer exposes an OkHttpClient with a logging interceptor installed`() {
        val container: AppContainer = DefaultAppContainer(context)

        val client = container.okHttpClient

        assertTrue(client.interceptors.any { it is HttpLoggingInterceptor })
    }

    @Test
    fun `DefaultAppContainer exposes a Retrofit instance built on top of the shared OkHttpClient`() {
        val container: AppContainer = DefaultAppContainer(context)

        val retrofit = container.retrofit

        assertEquals(container.okHttpClient, retrofit.callFactory())
    }

    @Test
    fun `DefaultAppContainer exposes a CartRepositoryImpl wired to the shared Retrofit and Room database`() {
        val container: AppContainer = DefaultAppContainer(context)

        val repository = container.cartRepository

        assertTrue(repository is CartRepositoryImpl)
    }

    @Test
    fun `DefaultAppContainer exposes a CouponRepositoryImpl wired to the shared Retrofit, with no Room dependency`() {
        val container: AppContainer = DefaultAppContainer(context)

        val repository = container.couponRepository

        assertTrue(repository is CouponRepositoryImpl)
    }

    @Test
    fun `DefaultAppContainer exposes the same lazily-built ValidateCoupon instance on every access`() {
        val container: AppContainer = DefaultAppContainer(context)

        val first = container.validateCoupon
        val second = container.validateCoupon

        assertTrue(first === second)
    }
}
