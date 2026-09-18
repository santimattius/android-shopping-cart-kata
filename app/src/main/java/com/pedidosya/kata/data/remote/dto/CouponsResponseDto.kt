package com.pedidosya.kata.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape of the mock coupon endpoint's top-level JSON object: a flat list of every coupon,
 * with no lookup-by-code endpoint (confirmed via a direct `curl`). Matching a typed code against
 * this list happens client-side in [com.pedidosya.kata.data.repository.CouponRepositoryImpl].
 */
@Serializable
data class CouponsResponseDto(
    val coupons: List<CouponDto>
)
