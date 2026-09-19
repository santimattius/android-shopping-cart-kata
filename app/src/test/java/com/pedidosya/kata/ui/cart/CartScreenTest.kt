package com.pedidosya.kata.ui.cart

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.pedidosya.kata.domain.model.CartItem
import com.pedidosya.kata.domain.model.CartTotals
import com.pedidosya.kata.domain.model.Coupon
import com.pedidosya.kata.domain.model.CouponValidationResult
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CartScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Loading shows progress and no confirm purchase action`() {
        render(CartUiState.Loading)

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeRule.onNodeWithText("Confirmar Compra").assertDoesNotExist()
    }

    @Test
    fun `Error shows unavailable cart message`() {
        render(CartUiState.Error(CartErrorReason.NoCacheAvailable))

        composeRule.onNodeWithText("No pudimos cargar tu carrito.").assertIsDisplayed()
    }

    @Test
    fun `Error retry invokes retry callback once`() {
        var retries = 0
        render(
            state = CartUiState.Error(CartErrorReason.NoCacheAvailable),
            onRetry = { retries++ },
        )

        composeRule.onNodeWithText("Reintentar").performClick()

        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun `Success shows each item title and quantity price`() {
        render(success(items = persistentListOf(sampleItem)))

        composeRule.onNodeWithText(sampleItem.title).assertIsDisplayed()
        composeRule.onNodeWithText("${sampleItem.quantity} x ${sampleItem.price}").assertIsDisplayed()
    }

    @Test
    fun `Success with no items shows empty cart and no item row`() {
        render(success(items = persistentListOf()))

        composeRule.onNodeWithText("Tu carrito está vacío.").assertIsDisplayed()
        composeRule.onNodeWithText(sampleItem.title).assertDoesNotExist()
    }

    @Test
    fun `Validating coupon disables apply`() {
        render(success(isValidating = true))

        composeRule.onNodeWithText("Aplicar").assertIsNotEnabled()
    }

    @Test
    fun `Idle coupon enables apply and invokes callback`() {
        var applications = 0
        render(
            state = success(),
            onApplyCoupon = { applications++ },
        )

        composeRule.onNodeWithText("Aplicar").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, applications) }
    }

    @Test
    fun `Invalid coupon disables confirm purchase`() {
        render(success(coupon = CouponValidationResult.Invalid))

        composeRule.onNodeWithText("Confirmar Compra").assertIsNotEnabled()
    }

    @Test
    fun `Validating coupon disables confirm purchase`() {
        render(success(isValidating = true))

        composeRule.onNodeWithText("Confirmar Compra").assertIsNotEnabled()
    }

    @Test
    fun `Not applied coupon enables confirm and invokes callback`() {
        var confirmations = 0
        render(
            state = success(coupon = CouponValidationResult.NotApplied),
            onConfirmPurchase = { confirmations++ },
        )

        composeRule.onNodeWithText("Confirmar Compra").assertIsEnabled().performClick()

        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun `Coupon text input invokes controlled input callback`() {
        var input: String? = null
        render(
            state = success(),
            onCouponInputChanged = { input = it },
        )

        composeRule.onNode(hasSetTextAction()).performTextInput("AHORRO15")

        composeRule.runOnIdle { assertEquals("AHORRO15", input) }
    }

    @Test
    fun `Coupon status messages cover every result branch`() {
        val messages =
            listOf(
                "Cupón aplicado: 15.0% off. Nuevo total: 84.99",
                "El código ingresado no es válido.",
                "Este cupón ya no está activo.",
                "No pudimos validar el cupón. Intentá de nuevo.",
            )
        val coupon =
            androidx.compose.runtime.mutableStateOf<CouponValidationResult>(CouponValidationResult.NotApplied)
        composeRule.setContent {
            CartScreen(
                state = success(coupon = coupon.value),
                onRetry = {},
                onRefresh = {},
                onCouponInputChanged = {},
                onApplyCoupon = {},
                onConfirmPurchase = {},
            )
        }

        messages.forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }

        composeRule.runOnIdle { coupon.value = CouponValidationResult.Valid(sampleCoupon) }
        composeRule.onNodeWithText(messages[0]).assertIsDisplayed()

        composeRule.runOnIdle { coupon.value = CouponValidationResult.Invalid }
        composeRule.onNodeWithText(messages[1]).assertIsDisplayed()

        composeRule.runOnIdle { coupon.value = CouponValidationResult.Inactive }
        composeRule.onNodeWithText(messages[2]).assertIsDisplayed()

        composeRule.runOnIdle { coupon.value = CouponValidationResult.ServiceError }
        composeRule.onNodeWithText(messages[3]).assertIsDisplayed()
    }

    @Test
    fun `Refreshing state keeps cart items rendered`() {
        render(success(items = persistentListOf(sampleItem), isRefreshing = true))

        composeRule.onNodeWithText(sampleItem.title).assertIsDisplayed()
    }

    @Test
    fun `Pull to refresh swipe invokes refresh callback once`() {
        var refreshes = 0
        render(
            state = success(items = persistentListOf(sampleItem)),
            onRefresh = { refreshes++ },
        )

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeDown() }

        composeRule.runOnIdle { assertEquals(1, refreshes) }
    }

    private fun render(
        state: CartUiState,
        onRetry: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onCouponInputChanged: (String) -> Unit = {},
        onApplyCoupon: () -> Unit = {},
        onConfirmPurchase: () -> Unit = {},
    ) {
        composeRule.setContent {
            CartScreen(
                state = state,
                onRetry = onRetry,
                onRefresh = onRefresh,
                onCouponInputChanged = onCouponInputChanged,
                onApplyCoupon = onApplyCoupon,
                onConfirmPurchase = onConfirmPurchase,
            )
        }
    }

    private fun success(
        items: kotlinx.collections.immutable.ImmutableList<CartItem> = persistentListOf(sampleItem),
        coupon: CouponValidationResult = CouponValidationResult.NotApplied,
        isValidating: Boolean = false,
        isRefreshing: Boolean = false,
    ) = CartUiState.Success(
        items = items,
        totals = sampleTotals,
        coupon = coupon,
        isValidating = isValidating,
        isRefreshing = isRefreshing,
    )

    private companion object {
        val sampleItem =
            CartItem(
                id = "headphones",
                title = "Auriculares",
                category = "technology",
                quantity = 2,
                price = 49.99,
                imageUrl = null,
            )
        val sampleTotals =
            CartTotals(
                subtotal = 99.98,
                discount = 14.99,
                total = 84.99,
                nominalPercentage = 15.0,
            )
        val sampleCoupon =
            Coupon(
                code = "AHORRO15",
                discountPercentage = 15.0,
                applicableCategory = "all",
                isActive = true,
            )
    }
}
