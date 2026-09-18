package com.pedidosya.kata.domain.usecase

import com.pedidosya.kata.domain.model.CouponValidationResult
import com.pedidosya.kata.domain.repository.CouponRepository

/**
 * Thin use-case wrapper the ViewModel layer calls instead of [CouponRepository] directly, per the
 * design's data-flow (`ValidateCoupon → CouponRepositoryImpl → CouponApi`). Always remote: it adds
 * no caching or fallback of its own.
 */
class ValidateCoupon(private val repository: CouponRepository) {

    suspend operator fun invoke(code: String): CouponValidationResult = repository.validate(code)
}
