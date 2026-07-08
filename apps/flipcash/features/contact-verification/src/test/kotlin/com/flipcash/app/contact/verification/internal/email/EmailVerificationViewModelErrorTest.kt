package com.flipcash.app.contact.verification.internal.email

import com.flipcash.app.core.verification.email.EmailCodeChannel
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.EmailVerificationError
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val verificationController: ContactVerificationController = mock()
    private val profileController = mockk<ProfileController>(relaxed = true)
    private val resources = FakeResourceHelper()
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()
        // Verification required → OnSendCodeClicked takes the server send path.
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

    private fun createViewModel(): EmailVerificationViewModel {
        return EmailVerificationViewModel(
            verificationController = verificationController,
            profileController = profileController,
            resources = resources,
            dispatchers = dispatchers,
            emailCodeChannel = EmailCodeChannel(),
            userFlags = userFlags,
        )
    }

    @Test
    fun `send code failure shows failedToSendCodeToEmail error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.sendVerificationCode(any())).thenReturn(Result.failure(RuntimeException("server error")))

        val vm = createViewModel()
        vm.dispatchEvent(EmailVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_failedToSendCodeToEmail" })
    }

    @Test
    fun `invalid verification code shows emailVerificationLinkInvalid error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.checkVerificationCode(any(), any())).thenReturn(Result.failure(EmailVerificationError.InvalidVerificationCode()))

        val vm = createViewModel()
        vm.dispatchEvent(EmailVerificationViewModel.Event.OnDataProvided(email = "test@example.com", code = "123456"))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_emailVerificationLinkInvalid" })
    }

    @Test
    fun `expired verification shows emailVerificationLinkExpired error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.checkVerificationCode(any(), any())).thenReturn(Result.failure(EmailVerificationError.NoVerification()))

        val vm = createViewModel()
        vm.dispatchEvent(EmailVerificationViewModel.Event.OnDataProvided(email = "test@example.com", code = "123456"))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_emailVerificationLinkExpired" })
    }

    @Test
    fun `generic verification failure shows emailVerificationFailed error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.checkVerificationCode(any(), any())).thenReturn(Result.failure(RuntimeException("unknown error")))

        val vm = createViewModel()
        vm.dispatchEvent(EmailVerificationViewModel.Event.OnDataProvided(email = "test@example.com", code = "123456"))
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_emailVerificationFailed" })
    }
}
