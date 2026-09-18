package com.pedidosya.kata.domain.usecase

import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.CartTotals
import com.pedidosya.kata.domain.model.Coupon
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Computes cart totals, applying [coupon]'s discount only to the subtotal of items whose
 * category matches [Coupon.applicableCategory] (or every item when it is `"all"`).
 *
 * This is the single point in the app where money values are rounded (BigDecimal, HALF_UP,
 * 2 decimals). Callers (ViewModels, Composables) must not re-round these values.
 */
class CalculateTotals {

    operator fun invoke(items: List<CartItem>, coupon: Coupon?): CartTotals {
        val subtotal = round2(items.sumOf { it.price * it.quantity })
        val matchingSubtotal = matchingSubtotal(items, coupon)
        val discount = round2(matchingSubtotal * (coupon?.discountPercentage ?: 0.0) / 100)
        return CartTotals(
            subtotal = subtotal,
            discount = discount,
            total = subtotal - discount,
            nominalPercentage = coupon?.discountPercentage ?: 0.0
        )
    }

    private fun matchingSubtotal(items: List<CartItem>, coupon: Coupon?): Double {
        if (coupon == null) return 0.0
        return items
            .filter { coupon.applicableCategory == CATEGORY_ALL || it.category == coupon.applicableCategory }
            .sumOf { it.price * it.quantity }
    }

    private fun round2(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    private companion object {
        const val CATEGORY_ALL = "all"
    }
}
