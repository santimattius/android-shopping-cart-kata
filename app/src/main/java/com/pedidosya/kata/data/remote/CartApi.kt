package com.pedidosya.kata.data.remote

import com.pedidosya.kata.data.remote.dto.CartResponseDto
import retrofit2.http.GET

/**
 * Retrofit access to the mock cart endpoint. Base URL is configured on the shared [retrofit2.Retrofit]
 * instance built by [com.pedidosya.kata.di.AppContainer].
 */
interface CartApi {

    @GET("c/9518-ea8d-4660-afd8")
    suspend fun getCart(): CartResponseDto
}
