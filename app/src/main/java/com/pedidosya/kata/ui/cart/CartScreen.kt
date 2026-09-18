package com.pedidosya.kata.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.CartTotals
import com.pedidosya.kata.domain.model.CouponValidationResult

/**
 * Cart list screen: renders [CartUiState.Loading]/[CartUiState.Error]/[CartUiState.Success],
 * including the coupon field, "Aplicar", and "Confirmar Compra" per `sdd/shopping-cart/spec`.
 *
 * [onNavigateToSummary] is invoked when [CartViewModel.events] emits
 * [CartEvent.NavigateToSummary]; the actual Summary route/screen is wired by the caller
 * (`KataNavHost`, Phase 7). Pull-to-refresh is added in a later, optional phase.
 */
@Composable
fun CartScreen(
    viewModel: CartViewModel = viewModel(factory = CartViewModel.Factory),
    onNavigateToSummary: (code: String, discountPercentage: Double, applicableCategory: String) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CartEvent.NavigateToSummary ->
                    onNavigateToSummary(event.code, event.discountPercentage, event.applicableCategory)
            }
        }
    }

    when (val current = state) {
        CartUiState.Loading -> LoadingContent()
        is CartUiState.Error -> ErrorContent(onRetry = viewModel::retry)
        is CartUiState.Success -> CartContent(
            state = current,
            onCouponInputChanged = viewModel::onCouponInputChanged,
            onApplyCoupon = viewModel::onApplyCoupon,
            onConfirmPurchase = viewModel::onConfirmPurchase,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "No pudimos cargar tu carrito.")
        Button(onClick = onRetry) { Text(text = "Reintentar") }
    }
}

@Composable
private fun CartContent(
    state: CartUiState.Success,
    onCouponInputChanged: (String) -> Unit,
    onApplyCoupon: () -> Unit,
    onConfirmPurchase: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.items.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(text = "Tu carrito está vacío.", modifier = Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(items = state.items, key = { it.id }) { item -> CartItemRow(item) }
            }
        }
        CouponSection(
            state = state,
            onCouponInputChanged = onCouponInputChanged,
            onApplyCoupon = onApplyCoupon,
            onConfirmPurchase = onConfirmPurchase,
        )
    }
}

@Composable
private fun CartItemRow(item: CartItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
        Text(text = "${item.quantity} x ${item.price}", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Coupon field, "Aplicar" (disabled mid-validation), the coupon status/preview message, and
 * "Confirmar Compra" (disabled per [CartUiState.Success.canConfirm]).
 */
@Composable
private fun CouponSection(
    state: CartUiState.Success,
    onCouponInputChanged: (String) -> Unit,
    onApplyCoupon: () -> Unit,
    onConfirmPurchase: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "Total: ${state.totals.total}", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.couponInput,
            onValueChange = onCouponInputChanged,
            label = { Text(text = "Código de cupón") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onApplyCoupon,
            enabled = !state.isValidating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Aplicar")
        }
        CouponStatusMessage(coupon = state.coupon, totals = state.totals)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onConfirmPurchase,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Confirmar Compra")
        }
    }
}

/**
 * Renders the last "Aplicar" outcome: a discount preview for [CouponValidationResult.Valid], a
 * specific error message for Invalid/Inactive/ServiceError, or nothing for [CouponValidationResult.NotApplied].
 */
@Composable
private fun CouponStatusMessage(coupon: CouponValidationResult, totals: CartTotals) {
    val message = when (coupon) {
        CouponValidationResult.NotApplied -> null
        is CouponValidationResult.Valid ->
            "Cupón aplicado: ${coupon.coupon.discountPercentage}% off. Nuevo total: ${totals.total}"
        CouponValidationResult.Invalid -> "El código ingresado no es válido."
        CouponValidationResult.Inactive -> "Este cupón ya no está activo."
        CouponValidationResult.ServiceError -> "No pudimos validar el cupón. Intentá de nuevo."
    }
    if (message != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = message, style = MaterialTheme.typography.bodySmall)
    }
}
