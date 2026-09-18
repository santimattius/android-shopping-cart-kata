package com.pedidosya.kata.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pedidosya.kata.domain.model.CartItem

/**
 * Cart list screen: renders [CartUiState.Loading]/[CartUiState.Error]/[CartUiState.Success].
 * Coupon input, Apply/Confirm actions, and pull-to-refresh are added in later phases.
 */
@Composable
fun CartScreen(viewModel: CartViewModel = viewModel(factory = CartViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        CartUiState.Loading -> LoadingContent()
        is CartUiState.Error -> ErrorContent(onRetry = viewModel::retry)
        is CartUiState.Success -> CartContent(items = current.items)
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
private fun CartContent(items: List<CartItem>) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(text = "Tu carrito está vacío.", modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id }) { item -> CartItemRow(item) }
    }
}

@Composable
private fun CartItemRow(item: CartItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
        Text(text = "${item.quantity} x ${item.price}", style = MaterialTheme.typography.bodyMedium)
    }
}
