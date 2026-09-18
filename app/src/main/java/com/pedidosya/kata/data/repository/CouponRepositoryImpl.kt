package com.pedidosya.kata.data.repository

import com.pedidosya.kata.data.mapper.toDomain
import com.pedidosya.kata.data.remote.CouponApi
import com.pedidosya.kata.data.remote.dto.CouponDto
import com.pedidosya.kata.domain.model.CouponValidationResult
import com.pedidosya.kata.domain.repository.CouponRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Always-remote [CouponRepository]: every [validate] call hits [api] for the full coupon list,
 * with no local cache and no fallback. [code] is matched trimmed and case-insensitively against
 * the list, mirroring the design's client-side lookup decision (the mock has no per-code
 * endpoint).
 */
class CouponRepositoryImpl(
    private val api: CouponApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : CouponRepository {

    override suspend fun validate(code: String): CouponValidationResult = withContext(dispatcher) {
        runCatching { api.getCoupons() }
            .fold(
                onSuccess = { response -> resolve(response.coupons, code) },
                onFailure = { CouponValidationResult.ServiceError }
            )
    }

    private fun resolve(coupons: List<CouponDto>, code: String): CouponValidationResult {
        val match = coupons.firstOrNull { it.code.trim().equals(code.trim(), ignoreCase = true) }
        return when {
            match == null -> CouponValidationResult.Invalid
            !match.isActive -> CouponValidationResult.Inactive
            else -> CouponValidationResult.Valid(match.toDomain())
        }
    }
}
