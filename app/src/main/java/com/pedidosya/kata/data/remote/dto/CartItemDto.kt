package com.pedidosya.kata.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a single cart line item as returned by the mock cart endpoint.
 *
 * `title` and `image_url` are absent on some items in the real mock response (confirmed via a
 * direct `curl`, not just the documented shape), so both are nullable with safe defaults; the
 * mapper in [com.pedidosya.kata.data.mapper] fills a domain-safe fallback for a missing title.
 */
@Serializable
data class CartItemDto(
    val id: String,
    val title: String? = null,
    val category: String,
    val quantity: Int,
    val price: Double,
    @SerialName("image_url") val imageUrl: String? = null
)
