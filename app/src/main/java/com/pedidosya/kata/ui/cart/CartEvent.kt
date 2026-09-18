package com.pedidosya.kata.ui.cart

/**
 * One-time signals [CartViewModel] emits for [CartScreen] to act on. Navigation itself is wired
 * in Phase 7; this batch only produces the event with the data Summary will need.
 */
sealed interface CartEvent {

    /**
     * Confirm Purchase succeeded. [code]/[discountPercentage]/[applicableCategory] are empty/zero
     * when the purchase was confirmed with no coupon applied (0% discount, per spec).
     */
    data class NavigateToSummary(
        val code: String,
        val discountPercentage: Double,
        val applicableCategory: String,
    ) : CartEvent
}
