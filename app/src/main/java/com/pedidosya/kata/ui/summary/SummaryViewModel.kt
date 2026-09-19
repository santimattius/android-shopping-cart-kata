package com.pedidosya.kata.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedidosya.kata.App
import com.pedidosya.kata.domain.model.Coupon
import com.pedidosya.kata.domain.repository.CartRepository
import com.pedidosya.kata.domain.usecase.CalculateTotals
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Purchase summary state holder, per `sdd/shopping-cart/design`.
 *
 * Navigation carries only primitives ([code], [discountPercentage], [applicableCategory]) —
 * never a parcelable [com.pedidosya.kata.domain.model.CartTotals] — so this ViewModel re-derives
 * totals by reading the same Room-backed [CartRepository.observeCart] source of truth
 * [com.pedidosya.kata.ui.cart.CartViewModel] uses, reusing [CalculateTotals]. No second network
 * call is made. An empty [code] (Confirm with no coupon applied) means no discount at all.
 */
class SummaryViewModel(
    repository: CartRepository,
    code: String,
    discountPercentage: Double,
    applicableCategory: String,
) : ViewModel() {

    private val calculateTotals = CalculateTotals()

    private val coupon: Coupon? = if (code.isBlank()) {
        null
    } else {
        Coupon(
            code = code,
            discountPercentage = discountPercentage,
            applicableCategory = applicableCategory,
            isActive = true,
        )
    }

    val state: StateFlow<SummaryUiState> = repository.observeCart()
        .map { items ->
            val totals = calculateTotals(items, coupon)
            SummaryUiState.Success(
                items = items.toImmutableList(),
                total = totals.total,
                nominalPercentage = totals.nominalPercentage,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState.Loading)

    companion object {
        /**
         * Builds a [ViewModelProvider.Factory] capturing the navigation primitives, matching
         * [com.pedidosya.kata.ui.cart.CartViewModel.Factory]'s manual-DI pattern (no Hilt/Koin).
         */
        fun factory(
            code: String,
            discountPercentage: Double,
            applicableCategory: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                SummaryViewModel(
                    repository = app.container.cartRepository,
                    code = code,
                    discountPercentage = discountPercentage,
                    applicableCategory = applicableCategory,
                )
            }
        }
    }
}
