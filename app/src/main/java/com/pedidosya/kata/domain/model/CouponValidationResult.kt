package com.pedidosya.kata.domain.model

/**
 * The outcome of validating a coupon code against the remote coupon service.
 *
 * Modeled as a closed (sealed) type so every consumer (ViewModel `when` branches, Composable
 * rendering) is compiler-enforced to handle every case exhaustively.
 */
sealed interface CouponValidationResult {

    /** No coupon code has been applied yet. */
    data object NotApplied : CouponValidationResult

    /** The code matched an active coupon; [coupon] carries its discount details. */
    data class Valid(val coupon: Coupon) : CouponValidationResult

    /** The code does not match any coupon known to the remote service. */
    data object Invalid : CouponValidationResult

    /** The code matched a coupon, but it is currently inactive (`is_active == false`). */
    data object Inactive : CouponValidationResult

    /** The remote validation request failed (network/IO/HTTP/parse error). */
    data object ServiceError : CouponValidationResult
}
