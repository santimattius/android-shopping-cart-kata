package com.pedidosya.kata.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of a single coupon as returned by the mock coupon endpoint.
 *
 * Confirmed via a direct `curl` against the real mock response (not just the documented shape):
 * `discount_percentage` is a whole-number JSON value (e.g. `10`), decoded here as [Double] to
 * match [com.pedidosya.kata.domain.model.Coupon.discountPercentage].
 */
@Serializable
data class CouponDto(
    val code: String,
    @SerialName("discount_percentage") val discountPercentage: Double,
    @SerialName("applicable_category") val applicableCategory: String,
    @SerialName("is_active") val isActive: Boolean
)
