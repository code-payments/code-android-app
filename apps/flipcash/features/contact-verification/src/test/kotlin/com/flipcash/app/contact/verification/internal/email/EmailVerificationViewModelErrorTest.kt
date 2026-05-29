package com.flipcash.app.contact.verification.internal.email

import com.flipcash.features.contact.verification.R
import com.flipcash.app.core.verification.email.EmailCodeChannel
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.EmailVerificationError
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val resources = mockk<ResourceHelper>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { resources.getString(R.string.error_title_failedToSendCodeToEmail) } returns "error_title_failedToSendCodeToEmail"
        every { resources.getString(R.string.error_description_failedToSendCodeToEmail) } returns "error_description_failedToSendCodeToEmail"
        every { resources.getString(R.string.error_title_maxAttemptsReached) } returns "error_title_maxAttemptsReached"
        every { resources.getString(R.string.error_description_maxAttemptsReached) } returns "error_description_maxAttemptsReached"
        every { resources.getString(R.string.error_title_emailVerificationFailed) } returns "error_title_emailVerificationFailed"
        every { resources.getString(R.string.error_description_emailVerificationFailed) } returns "error_description_emailVerificationFailed"
        every { resources.getString(R.string.error_title_emailVerificationLinkInvalid) } returns "error_title_emailVerificationLinkInvalid"
        every { resources.getString(R.string.error_description_emailVerificationLinkInvalid) } returns "error_description_emailVerificationLinkInvalid"
        every { resources.getString(R.string.error_title_emailVerificationLinkExpired) } returns "error_title_emailVerificationLinkExpired"
        every { resources.getString(R.string.error_description_emailVerificationLinkExpired) } returns "error_description_emailVerificationLinkExpired"
        every { resources.getString(R.string.action_resendVerificationEmail) } returns "action_resendVerificationEmail"
        every { resources.getString(R.string.action_ok) } returns "OK"
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
