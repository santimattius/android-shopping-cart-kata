package com.pedidosya.kata.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Permanent Robolectric/Compose harness canary.
 *
 * This test proves the JVM test infrastructure (Robolectric + `ui-test-manifest` +
 * `isIncludeAndroidResources`) is wired correctly, independent of any application UI.
 * It must stay green forever: it is the separator between "harness is broken" and
 * "a specific screen's behavior is broken".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ComposeHarnessSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `renders compose harness ready text`() {
        composeRule.setContent {
            Text("compose-harness-ready")
        }

        composeRule.onNodeWithText("compose-harness-ready").assertIsDisplayed()
    }
}
