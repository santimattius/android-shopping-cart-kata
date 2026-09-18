package com.pedidosya.kata.domain.usecase

import com.pedidosya.kata.domain.model.Coupon
import com.pedidosya.kata.domain.model.CouponValidationResult
import com.pedidosya.kata.domain.repository.CouponRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidateCouponTest {

    private val repository: CouponRepository = mockk()

    @Test
    fun `invoke delegates to the repository and returns a Valid result verbatim`() = runTest {
        val coupon = Coupon(code = "BIENVENIDA10", discountPercentage = 10.0, applicableCategory = "all", isActive = true)
        coEvery { repository.validate("BIENVENIDA10") } returns CouponValidationResult.Valid(coupon)

        val result = ValidateCoupon(repository)("BIENVENIDA10")

        assertEquals(CouponValidationResult.Valid(coupon), result)
    }

    @Test
    fun `invoke delegates to the repository and returns a ServiceError result verbatim`() = runTest {
        coEvery { repository.validate("BIENVENIDA10") } returns CouponValidationResult.ServiceError

        val result = ValidateCoupon(repository)("BIENVENIDA10")

        assertEquals(CouponValidationResult.ServiceError, result)
    }
}
