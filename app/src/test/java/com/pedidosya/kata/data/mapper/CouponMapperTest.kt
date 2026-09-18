package com.pedidosya.kata.data.mapper

import com.pedidosya.kata.data.remote.dto.CouponDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponMapperTest {

    @Test
    fun `CouponDto maps every field into the domain Coupon verbatim, active coupon`() {
        val dto = CouponDto(
            code = "BIENVENIDA10",
            discountPercentage = 10.0,
            applicableCategory = "all",
            isActive = true
        )

        val coupon = dto.toDomain()

        assertEquals("BIENVENIDA10", coupon.code)
        assertEquals(10.0, coupon.discountPercentage, 0.0)
        assertEquals("all", coupon.applicableCategory)
        assertTrue(coupon.isActive)
    }

    @Test
    fun `CouponDto maps a category-scoped inactive coupon verbatim`() {
        val dto = CouponDto(
            code = "CUPONVENCIDO",
            discountPercentage = 50.0,
            applicableCategory = "all",
            isActive = false
        )

        val coupon = dto.toDomain()

        assertEquals("CUPONVENCIDO", coupon.code)
        assertEquals(50.0, coupon.discountPercentage, 0.0)
        assertFalse(coupon.isActive)
    }
}
