package com.pedidosya.kata.domain.model

/**
 * A single line item in the shopping cart.
 *
 * `category` is the raw category string coming from the remote source (e.g. "technology",
 * "grocery"); it is matched verbatim against [Coupon.applicableCategory] when computing
 * category-scoped discounts.
 */
data class CartItem(
    val id: String,
    val title: String,
    val category: String,
    val quantity: Int,
    val price: Double,
    val imageUrl: String?
)
