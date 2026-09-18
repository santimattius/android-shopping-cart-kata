package com.pedidosya.kata.domain.model

/**
 * The result of [com.pedidosya.kata.domain.usecase.CalculateTotals] for a cart snapshot.
 *
 * All monetary fields are already rounded (HALF_UP, 2 decimals) by the use case; nothing
 * downstream (ViewModels, Composables) should re-round them.
 *
 * @property nominalPercentage the coupon's nominal discount percentage (e.g. `15.0` for 15%),
 * NOT an effective/blended percentage over the whole cart. `0.0` when no coupon is applied.
 */
data class CartTotals(
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val nominalPercentage: Double
)
