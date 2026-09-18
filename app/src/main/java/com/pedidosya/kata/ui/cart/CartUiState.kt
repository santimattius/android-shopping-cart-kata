package com.pedidosya.kata.ui.cart

import com.pedidosya.kata.domain.model.CartItem

/**
 * Closed render state for the cart screen, per `sdd/shopping-cart/design`.
 *
 * `Success.canConfirm` is a placeholder until coupon state is wired (Phase 6): with no coupon
 * typed, the cart is always confirmable. It will grow real branching once [CartViewModel]
 * combines coupon validation into this state.
 */
sealed interface CartUiState {

    data object Loading : CartUiState

    data class Error(val reason: CartErrorReason) : CartUiState

    data class Success(val items: List<CartItem>) : CartUiState {
        val canConfirm: Boolean get() = true
    }
}

/** Why the cart screen is in [CartUiState.Error]. */
sealed interface CartErrorReason {
    /** First load: no cached items exist and the network refresh failed. */
    data object NoCacheAvailable : CartErrorReason
}
