package com.flipcash.app.login.router

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.features.login.R
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelErrorTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val authManager: AuthManager = mock()
    private val accounts: AccountController = mock()

    // MockK for everything else
    private val resources: ResourceHelper = mockk(relaxed = true)
    private val analytics: FlipcashAnalyticsService = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val featureFlags: FeatureFlagController = mockk(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
        // android.util.Base64 is stubbed in unit tests; mock it so encodeBase64() doesn't NPE
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers { java.util.Base64.getEncoder().encodeToString(firstArg()) }
        every { resources.getString(R.string.error_title_createAccountFailed) } returns "error_title_createAccountFailed"
        every { resources.getString(R.string.error_description_createAccountFailed) } returns "error_description_createAccountFailed"
        every { resources.getString(R.string.error_title_loginFailed) } returns "error_title_loginFailed"
        every { resources.getString(R.string.error_description_loginFailed) } returns "error_description_loginFailed"
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic(android.util.Base64::class)
    }

    private fun createViewModel(): LoginViewModel {
        return LoginViewModel(
            authManager = authManager,
            accounts = accounts,
            resources = resources,
            analytics = analytics,
            userManager = userManager,
            featureFlags = featureFlags,
            dispatchers = dispatchers,
        )
    }

    @Test
    fun `create account failure shows createAccountFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(authManager.createAccount()).thenReturn(Result.failure(RuntimeException()))
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.CreateAccount)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_createAccountFailed" })
    }

    @Test
    fun `deeplink login with bad base58 shows loginFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.LogIn(seed = "!!!", fromDeeplink = true))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_loginFailed" })
    }

    @Test
    fun `deeplink login with wrong entropy size shows loginFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        val shortEntropy = "3yZe7d" // valid base58 but decodes to < 16 bytes
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.LogIn(seed = shortEntropy, fromDeeplink = true))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_loginFailed" })
    }

    @Test
    fun `login failure shows loginFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(authManager.login(
            entropyB64 = org.mockito.kotlin.any(),
            isSoftLogin = org.mockito.kotlin.any(),
            isFromSelection = org.mockito.kotlin.any(),
            rollbackOnError = org.mockito.kotlin.any(),
        )).thenReturn(Result.failure(RuntimeException("auth error")))
        val viewModel = createViewModel()

        viewModel.dispatchEvent(LoginViewModel.Event.FacilitateLogin(entropyB64 = "dGVzdGVudHJvcHkxMjM0NQ=="))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_loginFailed" })
    }
}
