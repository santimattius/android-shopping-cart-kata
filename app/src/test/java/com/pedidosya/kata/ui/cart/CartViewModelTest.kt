package com.pedidosya.kata.ui.cart

import app.cash.turbine.test
import com.pedidosya.kata.core.MainDispatcherRule
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.Coupon
import com.pedidosya.kata.domain.model.CouponValidationResult
import com.pedidosya.kata.domain.repository.CartRepository
import com.pedidosya.kata.domain.repository.CouponRepository
import com.pedidosya.kata.domain.usecase.CalculateTotals
import com.pedidosya.kata.domain.usecase.ValidateCoupon
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: CartRepository = mockk()
    private val couponRepository: CouponRepository = mockk()
    private val validateCoupon = ValidateCoupon(couponRepository)
    private val calculateTotals = CalculateTotals()

    @Test
    fun `cache present renders Success immediately then a silent background refresh updates it`() =
        runTest(UnconfinedTestDispatcher()) {
            val cachedItems = listOf(item(id = "p1"))
            val refreshedItems = listOf(item(id = "p1"), item(id = "p2"))
            val cartFlow = MutableStateFlow(cachedItems)
            every { repository.observeCart() } returns cartFlow
            coEvery { repository.refresh() } returns Result.success(Unit)

            val viewModel = newViewModel()

            viewModel.state.test {
                val cached = awaitItem()
                assertEquals(CartUiState.Success(cachedItems, calculateTotals(cachedItems, null)), cached)
                assertTrue((cached as CartUiState.Success).canConfirm)

                cartFlow.value = refreshedItems

                assertEquals(
                    CartUiState.Success(refreshedItems, calculateTotals(refreshedItems, null)),
                    awaitItem(),
                )
            }
            coVerify(exactly = 1) { repository.refresh() }
        }

    @Test
    fun `first load without cache and a failed refresh renders Error`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartFlow = MutableStateFlow<List<CartItem>>(emptyList())
            every { repository.observeCart() } returns cartFlow
            coEvery { repository.refresh() } returns Result.failure(IOException("offline"))

            val viewModel = newViewModel()

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

            val viewModel = newViewModel()

            viewModel.state.test {
                assertEquals(CartUiState.Error(CartErrorReason.NoCacheAvailable), awaitItem())

                viewModel.retry()
                cartFlow.value = listOf(item(id = "p1"))

                val expectedItems = listOf(item(id = "p1"))
                assertEquals(
                    CartUiState.Success(expectedItems, calculateTotals(expectedItems, null)),
                    awaitItem(),
                )
            }
            coVerify(exactly = 2) { repository.refresh() }
        }

    @Test
    fun `applying a valid coupon updates the preview with the nominal percentage and discounted total`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartItems = listOf(item(id = "p1", category = "technology"))
            val coupon = Coupon(
                code = "TECH15",
                discountPercentage = 15.0,
                applicableCategory = "technology",
                isActive = true,
            )
            withReadyCart(cartItems)
            coEvery { couponRepository.validate("TECH15") } returns CouponValidationResult.Valid(coupon)

            val viewModel = newViewModel()

            viewModel.state.test {
                awaitItem() // initial Success

                viewModel.onCouponInputChanged("TECH15")
                awaitItem() // couponInput updated, coupon reset to NotApplied
                viewModel.onApplyCoupon()

                val applied = awaitItem() as CartUiState.Success
                assertEquals(CouponValidationResult.Valid(coupon), applied.coupon)
                assertFalse(applied.isValidating)
                assertEquals(calculateTotals(cartItems, coupon), applied.totals)
                assertEquals(15.0, applied.totals.nominalPercentage, 0.0)
                assertTrue(applied.canConfirm)
            }
        }

    @Test
    fun `applying an invalid coupon shows the Invalid state and blocks confirm`() = runTest(UnconfinedTestDispatcher()) {
        val cartItems = listOf(item(id = "p1"))
        withReadyCart(cartItems)
        coEvery { couponRepository.validate("NOPE") } returns CouponValidationResult.Invalid

        val viewModel = newViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onCouponInputChanged("NOPE")
            awaitItem()
            viewModel.onApplyCoupon()

            val applied = awaitItem() as CartUiState.Success
            assertEquals(CouponValidationResult.Invalid, applied.coupon)
            assertFalse(applied.canConfirm)
        }
    }

    @Test
    fun `applying an inactive coupon shows the Inactive state and blocks confirm`() = runTest(UnconfinedTestDispatcher()) {
        val cartItems = listOf(item(id = "p1"))
        withReadyCart(cartItems)
        coEvery { couponRepository.validate("OLD10") } returns CouponValidationResult.Inactive

        val viewModel = newViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.onCouponInputChanged("OLD10")
            awaitItem()
            viewModel.onApplyCoupon()

            val applied = awaitItem() as CartUiState.Success
            assertEquals(CouponValidationResult.Inactive, applied.coupon)
            assertFalse(applied.canConfirm)
        }
        coVerify(exactly = 1) { couponRepository.validate("OLD10") }
    }

    @Test
    fun `confirming with an empty coupon field does not call the coupon service and emits 0 percent`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartItems = listOf(item(id = "p1"))
            withReadyCart(cartItems)

            val viewModel = newViewModel()

            viewModel.events.test {
                viewModel.onConfirmPurchase()

                assertEquals(CartEvent.NavigateToSummary("", 0.0, "all"), awaitItem())
            }
            coVerify(exactly = 0) { couponRepository.validate(any()) }
        }

    @Test
    fun `confirming with an already-applied valid coupon reuses it without a new remote call`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartItems = listOf(item(id = "p1", category = "technology"))
            val coupon = Coupon(
                code = "TECH15",
                discountPercentage = 15.0,
                applicableCategory = "technology",
                isActive = true,
            )
            withReadyCart(cartItems)
            coEvery { couponRepository.validate("TECH15") } returns CouponValidationResult.Valid(coupon)

            val viewModel = newViewModel()
            viewModel.onCouponInputChanged("TECH15")
            viewModel.onApplyCoupon()

            viewModel.events.test {
                viewModel.onConfirmPurchase()

                assertEquals(CartEvent.NavigateToSummary("TECH15", 15.0, "technology"), awaitItem())
            }
            coVerify(exactly = 1) { couponRepository.validate("TECH15") }
        }

    @Test
    fun `confirming with a typed but never-applied code validates remotely then emits navigate`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartItems = listOf(item(id = "p1", category = "technology"))
            val coupon = Coupon(
                code = "TECH15",
                discountPercentage = 15.0,
                applicableCategory = "technology",
                isActive = true,
            )
            withReadyCart(cartItems)
            coEvery { couponRepository.validate("TECH15") } returns CouponValidationResult.Valid(coupon)

            val viewModel = newViewModel()
            viewModel.onCouponInputChanged("TECH15")

            viewModel.events.test {
                viewModel.onConfirmPurchase()

                assertEquals(CartEvent.NavigateToSummary("TECH15", 15.0, "technology"), awaitItem())
            }
            coVerify(exactly = 1) { couponRepository.validate("TECH15") }
        }

    @Test
    fun `confirming with an invalid or errored coupon blocks navigation`() = runTest(UnconfinedTestDispatcher()) {
        val cartItems = listOf(item(id = "p1"))
        withReadyCart(cartItems)
        coEvery { couponRepository.validate("NOPE") } returns CouponValidationResult.Invalid

        val viewModel = newViewModel()
        viewModel.onCouponInputChanged("NOPE")
        viewModel.onApplyCoupon()

        viewModel.events.test {
            viewModel.onConfirmPurchase()

            expectNoEvents()
        }
        val finalState = viewModel.state.value as CartUiState.Success
        assertFalse(finalState.canConfirm)
    }

    @Test
    fun `manual refresh sets isRefreshing while in flight and clears it once the background refresh completes`() =
        runTest(UnconfinedTestDispatcher()) {
            val cartItems = listOf(item(id = "p1"))
            val cartFlow = MutableStateFlow(cartItems)
            every { repository.observeCart() } returns cartFlow
            var refreshCalls = 0
            val refreshGate = CompletableDeferred<Unit>()
            coEvery { repository.refresh() } coAnswers {
                refreshCalls++
                if (refreshCalls > 1) refreshGate.await()
                Result.success(Unit)
            }

            val viewModel = newViewModel()

            viewModel.state.test {
                val initial = awaitItem() as CartUiState.Success
                assertFalse(initial.isRefreshing)

                viewModel.onRefresh()

                val refreshing = awaitItem() as CartUiState.Success
                assertTrue(refreshing.isRefreshing)

                refreshGate.complete(Unit)

                val done = awaitItem() as CartUiState.Success
                assertFalse(done.isRefreshing)
            }
            coVerify(exactly = 2) { repository.refresh() }
        }

    private fun withReadyCart(items: List<CartItem>) {
        val cartFlow = MutableStateFlow(items)
        every { repository.observeCart() } returns cartFlow
        coEvery { repository.refresh() } returns Result.success(Unit)
    }

    private fun newViewModel() = CartViewModel(repository, validateCoupon)

    private fun item(id: String, category: String = "technology") = CartItem(
        id = id,
        title = "Item $id",
        category = category,
        quantity = 1,
        price = 10.0,
        imageUrl = null,
    )
}
