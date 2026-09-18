package com.pedidosya.kata.ui.summary

import com.pedidosya.kata.domain.model.CartItem

/**
 * Closed render state for the purchase summary screen, per `sdd/shopping-cart/design`.
 *
 * There is no error state: [SummaryViewModel] only reads from the already-populated Room cache
 * ([com.pedidosya.kata.domain.repository.CartRepository.observeCart] never throws), so the only
 * transition is [Loading] until the first cart snapshot arrives, then [Success].
 */
sealed interface SummaryUiState {

    data object Loading : SummaryUiState

    /**
     * @property items the confirmed cart items, for the recap list.
     * @property total the final amount to pay, already reflecting the category-scoped discount
     * (or the full subtotal when no coupon was applied).
     * @property nominalPercentage the coupon's nominal discount percentage (e.g. `15.0`), never
     * an effective/blended percentage. `0.0` when no coupon was applied.
     */
    data class Success(
        val items: List<CartItem>,
        val total: Double,
        val nominalPercentage: Double,
    ) : SummaryUiState
}
