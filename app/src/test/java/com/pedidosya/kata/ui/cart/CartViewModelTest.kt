package com.pedidosya.kata.ui.cart

import app.cash.turbine.test
import com.pedidosya.kata.core.MainDispatcherRule
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.repository.CartRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: CartRepository = mockk()

    @Test
    fun `cache present renders Success immediately then a silent background refresh updates it`() =
        runTest(UnconfinedTestDispatcher()) {
            val cachedItems = listOf(item(id = "p1"))
            val refreshedItems = listOf(item(id = "p1"), item(id = "p2"))
            val cartFlow = MutableStateFlow(cachedItems)
            every { repository.observeCart() } returns cartFlow
            coEvery { repository.refresh() } returns Result.success(Unit)

            val viewModel = CartViewModel(repository)

            viewModel.state.test {
                val cached = awaitItem()
                assertEquals(CartUiState.Success(cachedItems), cached)
                assertTrue((cached as CartUiState.Success).canConfirm)

                cartFlow.value = refreshedItems

                assertEquals(CartUiState.Success(refreshedItems), awaitItem())
            }
            coVerify(exactly = 1) { repository.refresh() }
        }

    @Test
    fun `first load without cache and a failed refresh renders Error`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartFlow = MutableStateFlow<List<CartItem>>(emptyList())
            every { repository.observeCart() } returns cartFlow
            coEvery { repository.refresh() } returns Result.failure(IOException("offline"))

            val viewModel = CartViewModel(repository)

            viewModel.state.test {
                assertEquals(CartUiState.Error(CartErrorReason.NoCacheAvailable), awaitItem())
            }
        }

    @Test
    fun `retry after an error calls refresh again and succeeds once the cache is populated`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartFlow = MutableStateFlow<List<CartItem>>(emptyList())
            every { repository.observeCart() } returns cartFlow
            coEvery { repository.refresh() } returnsMany listOf(
                Result.failure(IOException("offline")),
                Result.success(Unit),
            )

            val viewModel = CartViewModel(repository)

            viewModel.state.test {
                assertEquals(CartUiState.Error(CartErrorReason.NoCacheAvailable), awaitItem())

                viewModel.retry()
                cartFlow.value = listOf(item(id = "p1"))

                assertEquals(CartUiState.Success(listOf(item(id = "p1"))), awaitItem())
            }
            coVerify(exactly = 2) { repository.refresh() }
        }

    private fun item(id: String) = CartItem(
        id = id,
        title = "Item $id",
        category = "technology",
        quantity = 1,
        price = 10.0,
        imageUrl = null,
    )
}
