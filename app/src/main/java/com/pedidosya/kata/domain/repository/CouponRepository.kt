package com.pedidosya.kata.domain.repository

import com.pedidosya.kata.domain.model.CouponValidationResult

/**
 * Remote-only coupon validation. There is no local cache: every call hits the remote coupon
 * service, matching [code] case- and whitespace-insensitively against the full coupon list.
 */
interface CouponRepository {

    /** Validates [code] against the remote coupon service. */
    suspend fun validate(code: String): CouponValidationResult
}
