package com.pedidosya.kata.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedidosya.kata.App
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.Coupon
import com.pedidosya.kata.domain.model.CouponValidationResult
import com.pedidosya.kata.domain.repository.CartRepository
import com.pedidosya.kata.domain.usecase.CalculateTotals
import com.pedidosya.kata.domain.usecase.ValidateCoupon
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Offline-first cart screen state holder, per `sdd/shopping-cart/design`.
 *
 * [CartRepository.observeCart] (Room-backed) is the single source of truth for [state]; a
 * background [CartRepository.refresh] never blocks rendering. Cached items render immediately,
 * a background refresh replaces them silently, and only a first load with no cache and a failed
 * refresh renders [CartUiState.Error].
 *
 * Coupon validation ([validateCoupon]) is always remote and never cached; [CalculateTotals] is a
 * pure function reused directly (no DI needed) to recompute the live preview on every coupon
 * input/validation change.
 */
class CartViewModel(
    private val repository: CartRepository,
    private val validateCoupon: ValidateCoupon,
) : ViewModel() {

    private val calculateTotals = CalculateTotals()

    private val _state = MutableStateFlow<CartUiState>(CartUiState.Loading)
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CartEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CartEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeCart().collect { items ->
                val current = _state.value
                if (items.isNotEmpty() || current is CartUiState.Success) {
                    _state.value = successFor(
                        items = items,
                        couponInput = (current as? CartUiState.Success)?.couponInput ?: "",
                        coupon = (current as? CartUiState.Success)?.coupon ?: CouponValidationResult.NotApplied,
                        isValidating = (current as? CartUiState.Success)?.isValidating ?: false,
                    )
                }
            }
        }
        refresh()
    }

    /** Re-attempts the fetch; called from the Error state's Retry action. */
    fun retry() = refresh()

    /** Updates the typed coupon code; any previous validation result becomes stale. */
    fun onCouponInputChanged(text: String) {
        val current = _state.value
        if (current !is CartUiState.Success) return
        _state.value = successFor(
            items = current.items,
            couponInput = text,
            coupon = CouponValidationResult.NotApplied,
            isValidating = false,
        )
    }

    /** "Aplicar": validates the typed code remotely and previews the discount if Valid. */
    fun onApplyCoupon() {
        val current = _state.value
        if (current !is CartUiState.Success || current.couponInput.isBlank()) return
        viewModelScope.launch { validateAndUpdate(current.couponInput) }
    }

    /**
     * "Confirmar Compra": per spec, an empty field bypasses the coupon service entirely
     * (0% discount). A code already validated Valid reuses that result. A typed-but-unapplied
     * code validates now. Invalid/Inactive/ServiceError blocks navigation.
     */
    fun onConfirmPurchase() {
        val current = _state.value
        if (current !is CartUiState.Success) return
        val code = current.couponInput

        if (code.isBlank()) {
            emitNavigate(code = "", discountPercentage = 0.0, applicableCategory = CATEGORY_ALL)
            return
        }

        when (val existing = current.coupon) {
            is CouponValidationResult.Valid -> emitNavigate(existing.coupon)
            CouponValidationResult.Invalid,
            CouponValidationResult.Inactive,
            CouponValidationResult.ServiceError,
            -> Unit // blocked: error message stays visible, no navigation

            CouponValidationResult.NotApplied -> viewModelScope.launch {
                val result = validateAndUpdate(code)
                if (result is CouponValidationResult.Valid) emitNavigate(result.coupon)
            }
        }
    }

    private suspend fun validateAndUpdate(code: String): CouponValidationResult {
        val validating = _state.value
        if (validating is CartUiState.Success) {
            _state.value = validating.copy(isValidating = true)
        }
        val result = validateCoupon(code)
        val latest = _state.value
        if (latest is CartUiState.Success) {
            _state.value = successFor(
                items = latest.items,
                couponInput = latest.couponInput,
                coupon = result,
                isValidating = false,
            )
        }
        return result
    }

    private fun successFor(
        items: List<CartItem>,
        couponInput: String,
        coupon: CouponValidationResult,
        isValidating: Boolean,
    ): CartUiState.Success {
        val activeCoupon = (coupon as? CouponValidationResult.Valid)?.coupon
        return CartUiState.Success(
            items = items,
            totals = calculateTotals(items, activeCoupon),
            couponInput = couponInput,
            coupon = coupon,
            isValidating = isValidating,
        )
    }

    private fun emitNavigate(coupon: Coupon) =
        emitNavigate(coupon.code, coupon.discountPercentage, coupon.applicableCategory)

    private fun emitNavigate(code: String, discountPercentage: Double, applicableCategory: String) {
        viewModelScope.launch {
            _events.emit(CartEvent.NavigateToSummary(code, discountPercentage, applicableCategory))
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val result = repository.refresh()
            if (result.isFailure && _state.value !is CartUiState.Success) {
                _state.value = CartUiState.Error(CartErrorReason.NoCacheAvailable)
            }
        }
    }

    companion object {
        private const val CATEGORY_ALL = "all"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                CartViewModel(app.container.cartRepository, app.container.validateCoupon)
            }
        }
    }
}
