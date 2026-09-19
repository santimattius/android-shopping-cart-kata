package com.pedidosya.kata.ui.summary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
 * Purchase summary screen: item recap, the coupon's nominal discount percentage (or its absence
 * when no coupon was applied), and the final total, per `sdd/shopping-cart/spec`.
 */
@Composable
fun SummaryScreen(viewModel: SummaryViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SummaryScreen(state = state)
}

@Composable
internal fun SummaryScreen(state: SummaryUiState) {
    when (val current = state) {
        SummaryUiState.Loading -> LoadingContent()
        is SummaryUiState.Success -> SummaryContent(current)
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun SummaryContent(state: SummaryUiState.Success) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Resumen de compra", style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(items = state.items, key = { it.id }) { item -> SummaryItemRow(item) }
        }
        HorizontalDivider()
        DiscountRow(state.nominalPercentage)
        Text(
            text = "Total a pagar: ${state.total}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SummaryItemRow(item: CartItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = item.title, style = MaterialTheme.typography.bodyLarge)
        Text(text = "${item.quantity} x ${item.price}", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shows the coupon's nominal % when a discount was applied, or an explicit "no coupon" line. */
@Composable
private fun DiscountRow(nominalPercentage: Double) {
    val text =
        if (nominalPercentage > 0.0) {
            "Descuento aplicado: $nominalPercentage%"
        } else {
            "Sin cupón aplicado"
        }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
