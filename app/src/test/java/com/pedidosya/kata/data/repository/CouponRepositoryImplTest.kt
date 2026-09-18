package com.pedidosya.kata.data.repository

import com.pedidosya.kata.data.remote.CouponApi
import com.pedidosya.kata.data.remote.dto.CouponDto
import com.pedidosya.kata.data.remote.dto.CouponsResponseDto
import com.pedidosya.kata.domain.model.CouponValidationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class CouponRepositoryImplTest {

    private val api: CouponApi = mockk()

    @Test
    fun `validate returns Valid with the matching active coupon`() = runTest {
        coEvery { api.getCoupons() } returns coupons(
            CouponDto(code = "BIENVENIDA10", discountPercentage = 10.0, applicableCategory = "all", isActive = true)
        )

        val result = repository(dispatcher()).validate("BIENVENIDA10")

        val valid = result as CouponValidationResult.Valid
        assertEquals("BIENVENIDA10", valid.coupon.code)
        assertEquals(10.0, valid.coupon.discountPercentage, 0.0)
        assertEquals("all", valid.coupon.applicableCategory)
        assertTrue(valid.coupon.isActive)
    }

    @Test
    fun `validate matches the code ignoring case and surrounding whitespace`() = runTest {
        coEvery { api.getCoupons() } returns coupons(
            CouponDto(code = "TECHREBATE25", discountPercentage = 25.0, applicableCategory = "technology", isActive = true)
        )

        val result = repository(dispatcher()).validate("  techrebate25  ")

        assertTrue(result is CouponValidationResult.Valid)
    }

    @Test
    fun `validate returns Invalid when the code matches no remote coupon`() = runTest {
        coEvery { api.getCoupons() } returns coupons(
            CouponDto(code = "BIENVENIDA10", discountPercentage = 10.0, applicableCategory = "all", isActive = true)
        )

        val result = repository(dispatcher()).validate("DOESNOTEXIST")

        assertEquals(CouponValidationResult.Invalid, result)
    }

    @Test
    fun `validate returns Inactive when the matching coupon is not active`() = runTest {
        coEvery { api.getCoupons() } returns coupons(
            CouponDto(code = "CUPONVENCIDO", discountPercentage = 50.0, applicableCategory = "all", isActive = false)
        )

        val result = repository(dispatcher()).validate("CUPONVENCIDO")

        assertEquals(CouponValidationResult.Inactive, result)
    }

    @Test
    fun `validate returns ServiceError when the request throws IOException`() = runTest {
        coEvery { api.getCoupons() } throws IOException("network down")

        val result = repository(dispatcher()).validate("BIENVENIDA10")

        assertEquals(CouponValidationResult.ServiceError, result)
    }

    @Test
    fun `validate returns ServiceError when the server responds with an HTTP error`() = runTest {
        coEvery { api.getCoupons() } throws HttpException(
            Response.error<CouponsResponseDto>(500, "".toResponseBody("application/json".toMediaType()))
        )

        val result = repository(dispatcher()).validate("BIENVENIDA10")

        assertEquals(CouponValidationResult.ServiceError, result)
    }

    @Test
    fun `validate hits the network on every call, never reusing a cached response`() = runTest {
        coEvery { api.getCoupons() } returns coupons(
            CouponDto(code = "BIENVENIDA10", discountPercentage = 10.0, applicableCategory = "all", isActive = true)
        )
        val repo = repository(dispatcher())

        repo.validate("BIENVENIDA10")
        repo.validate("BIENVENIDA10")

        coVerify(exactly = 2) { api.getCoupons() }
    }

    private fun TestScope.dispatcher(): CoroutineDispatcher = StandardTestDispatcher(testScheduler)

    private fun repository(dispatcher: CoroutineDispatcher) =
        CouponRepositoryImpl(api = api, dispatcher = dispatcher)

    private fun coupons(vararg items: CouponDto) = CouponsResponseDto(coupons = items.toList())
}
