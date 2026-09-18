package com.pedidosya.kata.data.remote.dto

import kotlinx.serialization.Serializable

/** Wire shape of the mock cart endpoint's top-level JSON object. */
@Serializable
data class CartResponseDto(
    val cart: CartDto
)

@Serializable
data class CartDto(
    val id: String,
    val currency: String,
    val items: List<CartItemDto>
)
