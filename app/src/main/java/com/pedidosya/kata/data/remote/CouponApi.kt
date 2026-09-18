package com.pedidosya.kata.data.remote

import com.pedidosya.kata.data.remote.dto.CouponsResponseDto
import retrofit2.http.GET

/**
 * Retrofit access to the mock coupon endpoint. There is no lookup-by-code endpoint (confirmed
 * via a direct `curl`): every call returns the full coupon list, and matching happens client-side
 * in [com.pedidosya.kata.data.repository.CouponRepositoryImpl].
 */
interface CouponApi {

    @GET("c/8e56-250c-4cd8-9c5a")
    suspend fun getCoupons(): CouponsResponseDto
}
