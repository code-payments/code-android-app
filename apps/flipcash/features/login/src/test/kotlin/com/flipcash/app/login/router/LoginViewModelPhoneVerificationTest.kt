package com.flipcash.app.login.router

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for the OnboardingPhoneVerification / PhoneNumberSend flag removal.
 * Both flags were `launched = true`, so phone verification is now driven purely by whether
 * the account has a verified phone number — the flag no longer gates it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelPhoneVerificationTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val authManager: AuthManager = mockk(relaxed = true)
    private val accounts: AccountController = mockk(relaxed = true)
    private val resources = FakeResourceHelper()
    private val analytics: FlipcashAnalyticsService = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        unmockkStatic(android.util.Base64::class)
    }

    private fun stubProfileWithPhone(verifiedPhoneNumber: String?) {
        val profile = mockk<UserProfile>(relaxed = true) {
            every { this@mockk.verifiedPhoneNumber } returns verifiedPhoneNumber
        }
        val state = mockk<UserManager.State>(relaxed = true) {
            every { userProfile } returns profile
        }
        every { userManager.state } returns MutableStateFlow(state)
    }

    private fun createViewModel() = LoginViewModel(
        authManager = authManager,
        accounts = accounts,
        resources = resources,
        analytics = analytics,
        userManager = userManager,
        dispatchers = dispatchers,
    )

    @Test
    fun `phone verification needed when no verified phone linked`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            stubProfileWithPhone(verifiedPhoneNumber = null)

            val vm = createViewModel()
            advanceUntilIdle()

            assertTrue(vm.stateFlow.value.needsPhoneVerification)
        }

    @Test
    fun `phone verification not needed when a verified phone is linked`() =
        runTest(mainCoroutineRule.dispatcher) {
            dispatchers = TestDispatchers(testScheduler)
            stubProfileWithPhone(verifiedPhoneNumber = "+15005550000")

            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.stateFlow.value.needsPhoneVerification)
        }
}
