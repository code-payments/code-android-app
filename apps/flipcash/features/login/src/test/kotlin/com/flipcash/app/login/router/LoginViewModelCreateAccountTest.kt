package com.flipcash.app.login.router

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the Create Account button loading/lifecycle (the "tapped, nothing happened,
 * tapped again" fix). The disabled-while-loading behaviour is verified at the UI
 * layer in [com.flipcash.app.login.internal.screens.LoginScreenContentTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelCreateAccountTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val authManager: AuthManager = mock()
    private val accounts: AccountController = mock()
    private val resources = FakeResourceHelper()
    private val analytics: FlipcashAnalyticsService = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val featureFlags: FeatureFlagController = mockk(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers { java.util.Base64.getEncoder().encodeToString(firstArg()) }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic(android.util.Base64::class)
    }

    private fun createViewModel() = LoginViewModel(
        authManager = authManager,
        accounts = accounts,
        resources = resources,
        analytics = analytics,
        userManager = userManager,
        featureFlags = featureFlags,
        dispatchers = dispatchers,
    )

    @Test
    fun `CreateAccount immediately shows loading so the button disables`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(authManager.createAccount()).thenReturn(Result.success(Unit))
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.CreateAccount)
        // No advanceUntilIdle: the reducer runs synchronously inside dispatchEvent,
        // so loading is set before the async createAccount() call is dispatched.

        assertTrue(viewModel.stateFlow.value.creatingAccount.loading)
    }

    @Test
    fun `CreateAccount success shows success then settles to idle after a delay`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(authManager.createAccount()).thenReturn(Result.success(Unit))
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceTimeBy(500) // within the 1s success window
        runCurrent()

        assertTrue(viewModel.stateFlow.value.creatingAccount.success)

        advanceUntilIdle() // elapse the settle delay

        val settled = viewModel.stateFlow.value.creatingAccount
        assertFalse(settled.success)
        assertFalse(settled.loading)
    }

    @Test
    fun `CreateAccount failure resets loading so the button is tappable again`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(authManager.createAccount()).thenReturn(Result.failure(RuntimeException("boom")))
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        assertFalse(viewModel.stateFlow.value.creatingAccount.loading)
    }
}
