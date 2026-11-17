package com.flipcash.app.login

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.analytics.events.AccountEvents
import com.flipcash.app.analytics.LocalAnalytics
import com.flipcash.app.analytics.RecordingAnalytics
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
 * The onboarding-completed event fires once per new account. With the Group Money gate open the
 * permissions phase has no steps, so the account leaves through the same exit as a gate-closed
 * user whose permissions are all granted, and only the new account fires it there.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingCompletedEventTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val analytics = RecordingAnalytics()

    private val allGranted = object : PermissionChecker {
        override fun isGranted(permission: String) = true
        override fun shouldShowRationale(permission: String) = false
    }

    private val noneGranted = object : PermissionChecker {
        override fun isGranted(permission: String) = false
        override fun shouldShowRationale(permission: String) = false
    }

    @Test
    fun `gate open, finishing the name step fires onboarding completed once and goes home`() {
        val afterName = (groupMoneyContactRoute().target as AppRoute.UpdateUserProfile).target
            as AppRoute.OnboardingFlow

        val navigator = render(afterName, noneGranted)

        assertEquals(1, analytics.events.count { it == AccountEvents.completeOnboarding() })
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), navigator.backStack.toList())
    }

    @Test
    fun `gate closed with every permission granted, the permissions phase fires nothing`() {
        val afterName = AppRoute.OnboardingFlow(
            phase = AppRoute.OnboardingFlow.Phase.Permissions,
            skipContacts = true,
        )

        val navigator = render(afterName, allGranted)

        assertEquals(0, analytics.events.count { it == AccountEvents.completeOnboarding() })
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), navigator.backStack.toList())
    }

    private fun render(route: AppRoute.OnboardingFlow, checker: PermissionChecker): CodeNavigator {
        val navigator = CodeNavigator(
            backStack = NavBackStack<NavKey>(AppRoute.Tabs.Wallet, route),
            resultStore = EmptyCodeNavigator.resultStore,
            onRootReached = {},
        )
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalCodeNavigator provides navigator,
                LocalPermissionChecker provides checker,
                LocalAnalytics provides analytics,
            ) {
                OnboardingFlowScreen(route, rememberNavResultStateRegistry(), purchaseStep = {})
            }
        }
        composeTestRule.waitForIdle()
        return navigator
    }
}
