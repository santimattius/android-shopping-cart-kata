package com.pedidosya.kata.ui.summary

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pedidosya.kata.domain.model.CartItem
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SummaryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `Loading shows progress and no purchase summary title`() {
        render(SummaryUiState.Loading)

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeRule.onNodeWithText("Resumen de compra").assertDoesNotExist()
    }

    @Test
    fun `Success shows title item rows and total`() {
        render(success(items = persistentListOf(sampleItem)))

        composeRule.onNodeWithText("Resumen de compra").assertIsDisplayed()
        composeRule.onNodeWithText(sampleItem.title).assertIsDisplayed()
        composeRule.onNodeWithText("${sampleItem.quantity} x ${sampleItem.price}").assertIsDisplayed()
        composeRule.onNodeWithText("Total a pagar: 84.99").assertIsDisplayed()
    }

    @Test
    fun `Positive nominal percentage shows discount row`() {
        render(success(nominalPercentage = 15.0))

        composeRule.onNodeWithText("Descuento aplicado: 15.0%").assertIsDisplayed()
    }

    @Test
    fun `Zero nominal percentage shows no coupon row`() {
        render(success(nominalPercentage = 0.0))

        composeRule.onNodeWithText("Sin cupón aplicado").assertIsDisplayed()
    }

    private fun render(state: SummaryUiState) {
        composeRule.setContent { SummaryScreen(state) }
    }

    private fun success(
        items: kotlinx.collections.immutable.ImmutableList<CartItem> = persistentListOf(sampleItem),
        nominalPercentage: Double = 0.0,
    ) = SummaryUiState.Success(
        items = items,
        total = 84.99,
        nominalPercentage = nominalPercentage,
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
    }
}
