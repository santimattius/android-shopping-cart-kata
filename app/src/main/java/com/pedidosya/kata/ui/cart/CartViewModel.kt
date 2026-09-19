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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Offline-first cart screen state holder, per `sdd/shopping-cart/design`.
 *
 * [CartRepository.observeCart] (Room-backed) is the single source of truth for [state]; a
 * background [CartRepository.refresh] never blocks rendering. Cached items render immediately,
 * a background refresh replaces them silently, and only a first load with no cache and a failed
 * refresh renders [CartUiState.Error].
 *
 * [state] is produced by exactly one [combine] reduction of [CartRepository.observeCart] and the
 * private [inputs] holder (per `sdd/viewmodel-state-and-concurrency/design`): no handler assigns
 * cart-screen state directly, and no handler reads a synchronous `.value` of [state] to decide
 * its next step — every handler reads and writes [inputs] only.
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

    private val inputs = MutableStateFlow(CartInputs())

    val state: StateFlow<CartUiState> =
        combine(repository.observeCart().map { it.toImmutableList() }, inputs, ::reduce)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState.Loading)

    private val _events = Channel<CartEvent>(Channel.BUFFERED)

    /**
     * Fan-out to exactly one collector (design Decision 5): a buffered, single-consumer
     * handoff that survives a transient gap where [CartScreen] is not actively collecting
     * (e.g. a configuration-change resubscription window), unlike a replay-0 `SharedFlow`.
     * `receiveAsFlow()` (not `consumeAsFlow()`) permits the sequential re-collection that
     * resubscription is. A second concurrent collector would split, not duplicate, this stream.
     */
    val events: Flow<CartEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    /**
     * Reduces the Room-backed [items] and the locally-owned [inputs] into one [CartUiState],
     * per the design's total table: a non-empty cache always wins ([CartUiState.Success]); an
     * empty cart resolves by [CartInputs.loadPhase] alone ([LoadPhase.Loading] stays Loading,
     * [LoadPhase.Failed] renders [CartUiState.Error], [LoadPhase.Loaded] renders an empty
     * [CartUiState.Success]). This makes the terminal state independent of the relative
     * interleaving of the cart-observation collector and the refresh call.
     */
    private fun reduce(items: ImmutableList<CartItem>, inputs: CartInputs): CartUiState {
        val isEmptyAndUnresolved = items.isEmpty() && inputs.loadPhase == LoadPhase.Loading
        val isEmptyAndFailed = items.isEmpty() && inputs.loadPhase == LoadPhase.Failed
        return when {
            isEmptyAndUnresolved -> CartUiState.Loading
            isEmptyAndFailed -> CartUiState.Error(CartErrorReason.NoCacheAvailable)
            else -> {
                val activeCoupon = (inputs.coupon as? CouponValidationResult.Valid)?.coupon
                CartUiState.Success(
                    items = items,
                    totals = calculateTotals(items, activeCoupon),
                    couponInput = inputs.couponInput,
                    coupon = inputs.coupon,
                    isValidating = inputs.isValidating,
                    isRefreshing = inputs.isRefreshing,
                )
            }
        }
    }

    /** Re-attempts the fetch; called from the Error state's Retry action. */
    fun retry() = refresh()

    /**
     * Pull-to-refresh gesture: reuses the same background [CartRepository.refresh] as the
     * initial load and [retry], toggling [CartUiState.Success.isRefreshing] while it is in
     * flight. Distinct from [retry], which only applies when no cache is available yet
     * ([CartUiState.Error]); this only runs from an already-rendered [CartUiState.Success].
     */
    fun onRefresh() = refresh(manual = true)

    /** Updates the typed coupon code; any previous validation result becomes stale. */
    fun onCouponInputChanged(text: String) {
        inputs.update {
            it.copy(couponInput = text, coupon = CouponValidationResult.NotApplied, isValidating = false)
        }
    }

    /** "Aplicar": validates the typed code remotely and previews the discount if Valid. */
    fun onApplyCoupon() {
        val code = inputs.value.couponInput
        if (code.isBlank()) return
        viewModelScope.launch { validateAndUpdate(code) }
    }

    /**
     * "Confirmar Compra": per spec, an empty field bypasses the coupon service entirely
     * (0% discount). A code already validated Valid reuses that result. A typed-but-unapplied
     * code validates now. Invalid/Inactive/ServiceError blocks navigation.
     */
    fun onConfirmPurchase() {
        val code = inputs.value.couponInput

        if (code.isBlank()) {
            emitNavigate(code = "", discountPercentage = 0.0, applicableCategory = CATEGORY_ALL)
            return
        }

        when (val existing = inputs.value.coupon) {
            is CouponValidationResult.Valid -> {
                emitNavigate(existing.coupon)
            }

            CouponValidationResult.Invalid,
            CouponValidationResult.Inactive,
            CouponValidationResult.ServiceError,
            -> {
                Unit
            }

            // blocked: error message stays visible, no navigation

            CouponValidationResult.NotApplied -> {
                viewModelScope.launch {
                    val result = validateAndUpdate(code)
                    if (result is CouponValidationResult.Valid) emitNavigate(result.coupon)
                }
            }
        }
    }

    private suspend fun validateAndUpdate(code: String): CouponValidationResult {
        inputs.update { it.copy(isValidating = true) }
        val result = validateCoupon(code)
        inputs.update { it.copy(coupon = result, isValidating = false) }
        return result
    }

    private fun emitNavigate(coupon: Coupon) = emitNavigate(coupon.code, coupon.discountPercentage, coupon.applicableCategory)

    private fun emitNavigate(
        code: String,
        discountPercentage: Double,
        applicableCategory: String,
    ) {
        _events.trySend(CartEvent.NavigateToSummary(code, discountPercentage, applicableCategory))
    }

    /** `manual` marks the pull-to-refresh gesture only (design Decision 4). */
    private fun refresh(manual: Boolean = false) {
        viewModelScope.launch {
            if (manual) inputs.update { it.copy(isRefreshing = true) }
            val result = repository.refresh()
            inputs.update {
                it.copy(
                    loadPhase = when {
                        result.isSuccess -> LoadPhase.Loaded
                        it.loadPhase == LoadPhase.Loading -> LoadPhase.Failed
                        else -> it.loadPhase
                    },
                    isRefreshing = if (manual) false else it.isRefreshing,
                )
            }
        }
    }

    private enum class LoadPhase { Loading, Loaded, Failed }

    private data class CartInputs(
        val loadPhase: LoadPhase = LoadPhase.Loading,
        val couponInput: String = "",
        val coupon: CouponValidationResult = CouponValidationResult.NotApplied,
        val isValidating: Boolean = false,
        val isRefreshing: Boolean = false,
    )

    companion object {
        private const val CATEGORY_ALL = "all"

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                    CartViewModel(app.container.cartRepository, app.container.validateCoupon)
                }
            }
    }
}
