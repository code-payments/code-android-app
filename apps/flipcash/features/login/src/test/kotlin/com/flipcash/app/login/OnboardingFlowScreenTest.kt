package com.flipcash.app.login

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.rememberNavResultStateRegistry
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.PermissionChecker
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The end of onboarding releases the user onto Chats, replacing the whole stack (`ClearAll`),
 * whether or not they just created the account.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingFlowScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val allGranted = object : PermissionChecker {
        override fun isGranted(permission: String) = true
        override fun shouldShowRationale(permission: String) = false
    }

    @Test
    fun `permissions phase with everything granted lands on chats and clears the stack`() {
        val onboarding = AppRoute.OnboardingFlow(
            phase = AppRoute.OnboardingFlow.Phase.Permissions,
            skipContacts = false,
        )
        val navigator = CodeNavigator(
            backStack = NavBackStack<NavKey>(AppRoute.Tabs.Wallet, onboarding),
            resultStore = EmptyCodeNavigator.resultStore,
            onRootReached = {},
        )

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalCodeNavigator provides navigator,
                LocalPermissionChecker provides allGranted,
            ) {
                OnboardingFlowScreen(onboarding, rememberNavResultStateRegistry(), purchaseStep = {})
            }
        }
        composeTestRule.waitForIdle()

        // ClearAll leaves only the destination on the stack.
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), navigator.backStack.toList())
    }
}
