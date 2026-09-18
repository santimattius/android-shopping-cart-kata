package com.pedidosya.kata.domain.model

/**
 * A coupon as returned by the remote coupon service.
 *
 * `applicableCategory` scopes the discount to items whose [CartItem.category] matches it
 * exactly, or to every item in the cart when its value is `"all"`.
 */
data class Coupon(
    val code: String,
    val discountPercentage: Double,
    val applicableCategory: String,
    val isActive: Boolean
)
