package com.pedidosya.kata.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pedidosya.kata.App
import com.pedidosya.kata.domain.repository.CartRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Offline-first cart screen state holder, per `sdd/shopping-cart/design`.
 *
 * [CartRepository.observeCart] (Room-backed) is the single source of truth for [state]; a
 * background [CartRepository.refresh] never blocks rendering. Cached items render immediately,
 * a background refresh replaces them silently, and only a first load with no cache and a failed
 * refresh renders [CartUiState.Error].
 */
class CartViewModel(private val repository: CartRepository) : ViewModel() {

    private val _state = MutableStateFlow<CartUiState>(CartUiState.Loading)
    val state: StateFlow<CartUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCart().collect { items ->
                if (items.isNotEmpty() || _state.value is CartUiState.Success) {
                    _state.value = CartUiState.Success(items)
                }
            }
        }
        refresh()
    }

    /** Re-attempts the fetch; called from the Error state's Retry action. */
    fun retry() = refresh()

    private fun refresh() {
        viewModelScope.launch {
            val result = repository.refresh()
            if (result.isFailure && _state.value !is CartUiState.Success) {
                _state.value = CartUiState.Error(CartErrorReason.NoCacheAvailable)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as App
                CartViewModel(app.container.cartRepository)
            }
        }
    }
}
