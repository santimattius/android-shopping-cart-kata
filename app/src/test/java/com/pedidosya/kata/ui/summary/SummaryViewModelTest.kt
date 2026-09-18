package com.pedidosya.kata.ui.summary

import app.cash.turbine.test
import com.pedidosya.kata.core.MainDispatcherRule
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.repository.CartRepository
import com.pedidosya.kata.domain.usecase.CalculateTotals
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Per `sdd/shopping-cart/design`: Summary receives only primitives (code, nominal %, applicable
 * category) via navigation and re-derives [com.pedidosya.kata.domain.model.CartTotals] from the
 * same Room-backed [CartRepository] cache used by [com.pedidosya.kata.ui.cart.CartViewModel] —
 * no second network call, no parcelable domain object crossing the back stack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: CartRepository = mockk()
    private val calculateTotals = CalculateTotals()

    @Test
    fun `with a valid applied coupon it shows the discounted total and the nominal percentage`() =
        runTest(UnconfinedTestDispatcher()) {
            val items = listOf(item(id = "p1", category = "technology", price = 100.0))
            every { repository.observeCart() } returns MutableStateFlow(items)

            val viewModel = SummaryViewModel(
                repository = repository,
                code = "TECH15",
                discountPercentage = 15.0,
                applicableCategory = "technology",
            )

            viewModel.state.test {
                val success = awaitItem() as SummaryUiState.Success
                val expectedCoupon = com.pedidosya.kata.domain.model.Coupon(
                    code = "TECH15",
                    discountPercentage = 15.0,
                    applicableCategory = "technology",
                    isActive = true,
                )
                val expectedTotals = calculateTotals(items, expectedCoupon)
                assertEquals(expectedTotals.total, success.total, 0.0)
                assertEquals(15.0, success.nominalPercentage, 0.0)
                assertEquals(items, success.items)
            }
        }

    @Test
    fun `with no coupon code it shows the full total and zero percentage`() =
        runTest(UnconfinedTestDispatcher()) {
            val items = listOf(item(id = "p1", category = "technology", price = 100.0))
            every { repository.observeCart() } returns MutableStateFlow(items)

            val viewModel = SummaryViewModel(
                repository = repository,
                code = "",
                discountPercentage = 0.0,
                applicableCategory = "all",
            )

            viewModel.state.test {
                val success = awaitItem() as SummaryUiState.Success
                val expectedTotals = calculateTotals(items, null)
                assertEquals(expectedTotals.total, success.total, 0.0)
                assertEquals(0.0, success.nominalPercentage, 0.0)
            }
        }

    @Test
    fun `a coupon whose category does not match any item discounts nothing despite having a code`() =
        runTest(UnconfinedTestDispatcher()) {
            val items = listOf(item(id = "p1", category = "grocery", price = 50.0))
            every { repository.observeCart() } returns MutableStateFlow(items)

            val viewModel = SummaryViewModel(
                repository = repository,
                code = "TECH15",
                discountPercentage = 15.0,
                applicableCategory = "technology",
            )

            viewModel.state.test {
                val success = awaitItem() as SummaryUiState.Success
                assertEquals(50.0, success.total, 0.0)
                assertEquals(15.0, success.nominalPercentage, 0.0)
            }
        }

    private fun item(id: String, category: String, price: Double) = CartItem(
        id = id,
        title = "Item $id",
        category = category,
        quantity = 1,
        price = price,
        imageUrl = null,
    )
}
