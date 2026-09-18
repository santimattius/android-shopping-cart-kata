package com.pedidosya.kata.domain.usecase

import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.Coupon
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateTotalsTest {

    private val calculateTotals = CalculateTotals()

    @Test
    fun `no coupon applied charges full price and shows zero nominal percentage`() {
        val items = listOf(
            CartItem(id = "1", title = "Phone", category = "technology", quantity = 2, price = 100.0, imageUrl = null)
        )

        val totals = calculateTotals(items, coupon = null)

        assertEquals(200.0, totals.subtotal, 0.0)
        assertEquals(0.0, totals.discount, 0.0)
        assertEquals(200.0, totals.total, 0.0)
        assertEquals(0.0, totals.nominalPercentage, 0.0)
    }

    @Test
    fun `coupon scoped to all discounts the entire subtotal`() {
        val items = listOf(
            CartItem(id = "1", title = "Phone", category = "technology", quantity = 1, price = 100.0, imageUrl = null),
            CartItem(id = "2", title = "Apples", category = "grocery", quantity = 2, price = 50.0, imageUrl = null)
        )
        val coupon = Coupon(code = "ALL10", discountPercentage = 10.0, applicableCategory = "all", isActive = true)

        val totals = calculateTotals(items, coupon)

        assertEquals(200.0, totals.subtotal, 0.0)
        assertEquals(20.0, totals.discount, 0.0)
        assertEquals(180.0, totals.total, 0.0)
        assertEquals(10.0, totals.nominalPercentage, 0.0)
    }

    @Test
    fun `category-scoped coupon discounts only the matching category subtotal in a mixed cart`() {
        val items = listOf(
            CartItem(id = "1", title = "Phone", category = "technology", quantity = 1, price = 100.0, imageUrl = null),
            CartItem(id = "2", title = "Apples", category = "grocery", quantity = 2, price = 50.0, imageUrl = null)
        )
        val coupon = Coupon(code = "TECH20", discountPercentage = 20.0, applicableCategory = "technology", isActive = true)

        val totals = calculateTotals(items, coupon)

        // technology subtotal (100.0) discounted 20% = 20.0; grocery (100.0) pays full price.
        assertEquals(200.0, totals.subtotal, 0.0)
        assertEquals(20.0, totals.discount, 0.0)
        assertEquals(180.0, totals.total, 0.0)
        assertEquals(20.0, totals.nominalPercentage, 0.0)
    }

    @Test
    fun `coupon category that matches no item in the cart applies zero discount but keeps the nominal percentage`() {
        val items = listOf(
            CartItem(id = "1", title = "Apples", category = "grocery", quantity = 1, price = 50.0, imageUrl = null)
        )
        val coupon = Coupon(code = "TECH20", discountPercentage = 20.0, applicableCategory = "technology", isActive = true)

        val totals = calculateTotals(items, coupon)

        assertEquals(50.0, totals.subtotal, 0.0)
        assertEquals(0.0, totals.discount, 0.0)
        assertEquals(50.0, totals.total, 0.0)
        assertEquals(20.0, totals.nominalPercentage, 0.0)
    }

    @Test
    fun `empty cart yields zero totals`() {
        val totals = calculateTotals(items = emptyList(), coupon = null)

        assertEquals(0.0, totals.subtotal, 0.0)
        assertEquals(0.0, totals.discount, 0.0)
        assertEquals(0.0, totals.total, 0.0)
        assertEquals(0.0, totals.nominalPercentage, 0.0)
    }

    @Test
    fun `discount rounds HALF_UP to two decimals when the raw computation lands on a half-cent`() {
        // 24.5 * 3 = 73.5; 73.5 * 15% = 11.025 -- a genuine third decimal digit, not floating
        // point noise. HALF_UP must round 11.025 up to 11.03 (never truncate to 11.02).
        val items = listOf(
            CartItem(id = "1", title = "Headphones", category = "technology", quantity = 3, price = 24.5, imageUrl = null)
        )
        val coupon = Coupon(code = "TECH15", discountPercentage = 15.0, applicableCategory = "technology", isActive = true)

        val totals = calculateTotals(items, coupon)

        assertEquals(73.5, totals.subtotal, 0.0)
        assertEquals(11.03, totals.discount, 0.0)
        assertEquals(62.47, totals.total, 0.0)
        assertEquals(15.0, totals.nominalPercentage, 0.0)
    }
}
