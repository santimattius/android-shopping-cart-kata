package com.pedidosya.kata.ui.cart

import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.CartTotals
import com.pedidosya.kata.domain.model.CouponValidationResult

/**
 * Closed render state for the cart screen, per `sdd/shopping-cart/design`.
 */
sealed interface CartUiState {

    data object Loading : CartUiState

    data class Error(val reason: CartErrorReason) : CartUiState

    /**
     * @property totals live preview of [CalculateTotals][com.pedidosya.kata.domain.usecase.CalculateTotals]
     * for [items] and, when [coupon] is [CouponValidationResult.Valid], its discount.
     * @property couponInput the text currently typed in the coupon field.
     * @property coupon the outcome of the last "Aplicar" validation, or [CouponValidationResult.NotApplied].
     * @property isValidating true while an "Aplicar"/"Confirmar" remote validation is in flight.
     * @property isRefreshing true while a manual pull-to-refresh gesture's background refresh is
     * in flight (distinct from [isValidating], which tracks coupon validation).
     */
    data class Success(
        val items: List<CartItem>,
        val totals: CartTotals,
        val couponInput: String = "",
        val coupon: CouponValidationResult = CouponValidationResult.NotApplied,
        val isValidating: Boolean = false,
        val isRefreshing: Boolean = false,
    ) : CartUiState {
        /**
         * Confirm gating per `sdd/shopping-cart/spec`: an empty field with no lingering Valid
         * result bypasses validation; a Valid result always confirms; any other non-blank,
         * non-Valid state (Invalid/Inactive/ServiceError/NotApplied-with-text) blocks confirm.
         */
        val canConfirm: Boolean get() =
            (couponInput.isBlank() && coupon !is CouponValidationResult.Valid) ||
                coupon is CouponValidationResult.Valid
    }
}

/** Why the cart screen is in [CartUiState.Error]. */
sealed interface CartErrorReason {
    /** First load: no cached items exist and the network refresh failed. */
    data object NoCacheAvailable : CartErrorReason
}
