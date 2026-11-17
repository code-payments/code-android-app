package com.flipcash.app.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.auth.internal.accounts.AccountStore
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.onboarding.OnboardingStep
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.login.router.LoginViewModel
import com.flipcash.services.controllers.AccountController
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec for new-account onboarding with the Group Money gate open (frames node 2-02 to 2-06):
 * Create registers the account and does the access key screen's work without showing it, then
 * the flow runs phone, code, email and name before notification permission. With the gate closed
 * Create still leads to the access key screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupMoneyOnboardingSpecTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val authManager: AuthManager = mock()
    private val accountStore: AccountStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        BottomBarManager.clear()
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers { java.util.Base64.getEncoder().encodeToString(firstArg()) }
        every { android.util.Base64.encode(any<ByteArray>(), any()) } answers { java.util.Base64.getEncoder().encode(firstArg<ByteArray>()) }
        whenever(authManager.accounts).thenReturn(accountStore)
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic(android.util.Base64::class)
    }

    private fun TestScope.viewModel(): Pair<LoginViewModel, MutableList<LoginViewModel.Event>> {
        val vm = LoginViewModel(
            authManager = authManager,
            accounts = mock<AccountController>(),
            resources = FakeResourceHelper(),
            analytics = RecordingAnalytics(),
            dispatchers = TestDispatchers(testScheduler),
        )
        val events = mutableListOf<LoginViewModel.Event>()
        vm.eventFlow.onEach { events += it }.launchIn(backgroundScope)
        return vm to events
    }

    // region gate open: Create

    @Test
    fun `gate open, Create does the access key work without showing the access key screen`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(authManager.isGroupMoneyOnboardingOpen()).thenReturn(true)
        whenever(authManager.createAccount()).thenReturn(Result.success(Unit))
        whenever(authManager.completeAccessKeyStep()).thenReturn(Result.success(false))
        val (vm, events) = viewModel()

        vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        verify(authManager).completeAccessKeyStep()
        assertTrue(LoginViewModel.Event.GroupMoneyAccountReady(requiresPurchase = false) in events)
        assertFalse(LoginViewModel.Event.CreateAccountSettled in events, "would open the access key screen")
    }

    @Test
    fun `gate open, an account that must pay to register goes to purchase first`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(authManager.isGroupMoneyOnboardingOpen()).thenReturn(true)
        whenever(authManager.createAccount()).thenReturn(Result.success(Unit))
        whenever(authManager.completeAccessKeyStep()).thenReturn(Result.success(true))
        val (vm, events) = viewModel()

        vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        assertTrue(LoginViewModel.Event.GroupMoneyAccountReady(requiresPurchase = true) in events)
    }

    @Test
    fun `gate open, a failed account setup shows the access key screen's error and a retry never registers twice`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(authManager.isGroupMoneyOnboardingOpen()).thenReturn(true)
        whenever(authManager.createAccount()).thenReturn(Result.success(Unit))
        whenever(authManager.completeAccessKeyStep())
            .thenReturn(Result.failure(RuntimeException("provisioning failed")), Result.success(false))
        val (vm, events) = viewModel()

        vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_accountSetupFailed" })
        assertTrue(vm.stateFlow.value.creatingAccount.isIdle, "Create must be tappable again")

        vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        verify(authManager, times(1)).createAccount()
        verify(authManager, times(2)).completeAccessKeyStep()
        assertTrue(LoginViewModel.Event.GroupMoneyAccountReady(requiresPurchase = false) in events)
    }

    // endregion

    // region gate closed: today's flow

    @Test
    fun `gate closed, Create leads to the access key screen and does none of its work`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(authManager.isGroupMoneyOnboardingOpen()).thenReturn(false)
        whenever(authManager.createAccount()).thenReturn(Result.success(Unit))
        val (vm, events) = viewModel()

        vm.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        assertTrue(LoginViewModel.Event.CreateAccountSettled in events)
        verify(authManager, never()).completeAccessKeyStep()
    }

    // endregion

    // region route after Create (and purchase)

    @Test
    fun `the contact steps start at phone entry in onboarding mode and end at the name step`() {
        val route = groupMoneyContactRoute()

        assertTrue(route.onboarding)
        assertTrue(route.includePhone)
        assertTrue(route.includeEmail)
        assertEquals(
            AppRoute.UpdateUserProfile(
                origin = AppRoute.OnboardingFlow(),
                steps = listOf(UpdateProfileStep.Name(DisplayNameSource.Onboarding, forGroups = true)),
                target = AppRoute.OnboardingFlow(
                    phase = AppRoute.OnboardingFlow.Phase.Permissions,
                    skipContacts = true,
                    skipNotifications = true,
                ),
                allowBack = false,
            ),
            route.target,
        )
    }

    @Test
    fun `gate open, the name step goes home with no permission step`() {
        val afterName = (groupMoneyContactRoute().target as AppRoute.UpdateUserProfile).target
            as AppRoute.OnboardingFlow

        assertEquals(AppRoute.OnboardingFlow.Phase.Permissions, afterName.phase)
        assertEquals(emptyList(), afterName.initialStack, "an empty permissions phase releases to home")
    }

    @Test
    fun `gate closed, the permissions phase after name still asks for notifications`() {
        val afterName = AppRoute.OnboardingFlow(
            phase = AppRoute.OnboardingFlow.Phase.Permissions,
            skipContacts = true,
        )

        assertEquals(listOf(OnboardingStep.NotificationPermission), afterName.initialStack)
    }

    // endregion
}
