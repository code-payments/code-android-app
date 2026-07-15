package com.flipcash.app.login.internal.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.getcode.theme.DesignSystem
import com.getcode.view.LoadingSuccessState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

/**
 * UI-level regression guard for the Create Account button: it must be tappable when
 * idle and DISABLED while creating (which is what debounces the double-taps the
 * tester hit on the slow first run).
 */
@RunWith(RobolectricTestRunner::class)
class LoginScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setScreen(
        isCreatingAccount: LoadingSuccessState,
        onCreate: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            DesignSystem {
                LoginRouterScreenContent(
                    isLoggingIn = LoadingSuccessState(),
                    isCreatingAccount = isCreatingAccount,
                    isLabsOpen = false,
                    createAccount = onCreate,
                    login = {},
                    onLogoTapped = {},
                    openBetaFlags = {},
                )
            }
        }
    }

    @Test
    fun `create account button is enabled and clickable when idle`() {
        var clicked = false
        setScreen(isCreatingAccount = LoadingSuccessState(), onCreate = { clicked = true })

        composeTestRule.onNodeWithTag("create_account_button")
            .assertIsEnabled()
            .performClick()

        assertTrue(clicked)
    }

    @Test
    fun `create account button is disabled while creating`() {
        setScreen(isCreatingAccount = LoadingSuccessState(loading = true))

        composeTestRule.onNodeWithTag("create_account_button").assertIsNotEnabled()
    }
}
