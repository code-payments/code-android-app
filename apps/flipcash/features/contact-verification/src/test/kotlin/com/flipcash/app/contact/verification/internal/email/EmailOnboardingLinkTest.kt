package com.flipcash.app.contact.verification.internal.email

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.app.core.verification.email.EmailCodeChannel
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.EmailVerificationError
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
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
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Onboarding saves the email without a code: a valid address goes to the server's unverified link
 * call and the flow moves on; a refusal or a network failure shows an error and keeps the user on
 * the email screen. Runs on Robolectric so the format check uses the real Patterns matcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EmailOnboardingLinkTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val verificationController: ContactVerificationController = mock()
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
        // Even where the on-ramp would require a verified email, onboarding only links one.
        val resolvedFlags = mockk<ResolvedUserFlags>(relaxed = true) {
            every { requireCoinbaseEmailVerification } returns ResolvedFlag(
                serverValue = true,
                override = FieldOverride.None,
            )
        }
        every { userFlags.resolvedFlags } returns MutableStateFlow(resolvedFlags)
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun TestScope.onboardingViewModel(email: String): Pair<EmailVerificationViewModel, () -> Boolean> {
        dispatchers = TestDispatchers(testScheduler)
        val vm = EmailVerificationViewModel(
            verificationController = verificationController,
            profileController = mockk<ProfileController>(relaxed = true),
            resources = FakeResourceHelper(),
            dispatchers = dispatchers,
            emailCodeChannel = EmailCodeChannel(),
            userFlags = userFlags,
        )
        var saved = false
        vm.eventFlow
            .filterIsInstance<EmailVerificationViewModel.Event.OnEntrySaved>()
            .onEach { saved = true }
            .launchIn(backgroundScope)
        vm.dispatchEvent(EmailVerificationViewModel.Event.OnOnboardingModeSet(onboarding = true))
        vm.stateFlow.value.email.setTextAndPlaceCursorAtEnd(email)
        return vm to { saved }
    }

    @Test
    fun `onboarding links the address without sending a code`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(verificationController.linkUnverifiedEmail(any())).thenReturn(Result.success(Unit))
        val (vm, saved) = onboardingViewModel("new.user@example.com")

        vm.dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        verify(verificationController).linkUnverifiedEmail(ContactMethod.Email("new.user@example.com"))
        verify(verificationController, never()).sendVerificationCode(any())
        assertTrue(saved())
    }

    @Test
    fun `a refused link shows an error and stays on the screen`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(verificationController.linkUnverifiedEmail(any()))
            .thenReturn(Result.failure(EmailVerificationError.Denied()))
        val (vm, saved) = onboardingViewModel("new.user@example.com")

        vm.dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        assertFalse(saved())
        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_failedToSendCodeToEmail" })
        assertTrue(vm.stateFlow.value.sendingCode.isIdle)
    }

    @Test
    fun `a network failure shows an error and stays on the screen`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(verificationController.linkUnverifiedEmail(any()))
            .thenReturn(Result.failure(RuntimeException("unavailable")))
        val (vm, saved) = onboardingViewModel("new.user@example.com")

        vm.dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        assertFalse(saved())
        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_failedToSendCodeToEmail" })
    }

    @Test
    fun `an address that fails the format check is not sent`() = runTest(mainCoroutineRule.dispatcher) {
        val (vm, saved) = onboardingViewModel("not-an-email")

        vm.dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        verify(verificationController, never()).linkUnverifiedEmail(any())
        assertFalse(saved())
        assertEquals(0, BottomBarManager.messages.value.size)
    }
}
