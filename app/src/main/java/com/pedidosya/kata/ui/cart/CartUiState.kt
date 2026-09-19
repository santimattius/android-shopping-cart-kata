package com.pedidosya.kata.ui.cart

import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.CartTotals
import com.pedidosya.kata.domain.model.CouponValidationResult
import kotlinx.collections.immutable.ImmutableList

/**
 * Closed render state for the cart screen, per `sdd/shopping-cart/design`.
 */
sealed interface CartUiState {
    data object Loading : CartUiState

    data class Error(
        val reason: CartErrorReason,
    ) : CartUiState

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
        val items: ImmutableList<CartItem>,
        val totals: CartTotals,
        val couponInput: String = "",
        val coupon: CouponValidationResult = CouponValidationResult.NotApplied,
        val isValidating: Boolean = false,
        val isRefreshing: Boolean = false,
    ) : CartUiState {
        /**
         * Confirm gating per `sdd/shopping-cart/spec`: an empty field bypasses validation,
         * and typed [CouponValidationResult.NotApplied] input remains confirmable so Confirm
         * can validate it. Terminal validation failures and in-flight validation block Confirm.
         */
        val canConfirm: Boolean get() =
            !isValidating &&
                when (coupon) {
                    CouponValidationResult.Invalid,
                    CouponValidationResult.Inactive,
                    CouponValidationResult.ServiceError,
                    -> false

                    CouponValidationResult.NotApplied,
                    is CouponValidationResult.Valid,
                    -> true
                }
    }
}

/** Why the cart screen is in [CartUiState.Error]. */
sealed interface CartErrorReason {
    /** First load: no cached items exist and the network refresh failed. */
    data object NoCacheAvailable : CartErrorReason
}
