package com.pedidosya.kata.ui.cart

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        composeRule.setContent {
            CartScreen(
                state = CartUiState.Loading,
                onRetry = {},
                onRefresh = {},
                onCouponInputChanged = {},
                onApplyCoupon = {},
                onConfirmPurchase = {},
            )
        }

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeRule.onNodeWithText("Confirmar Compra").assertDoesNotExist()
    }
}
