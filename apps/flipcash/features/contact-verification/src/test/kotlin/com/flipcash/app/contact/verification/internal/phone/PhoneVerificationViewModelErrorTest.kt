package com.flipcash.app.contact.verification.internal.phone

import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.onramp.BuyOptionsCache
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.features.contact.verification.R
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.PhoneVerificationError
import com.flipcash.services.user.UserManager
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
class PhoneVerificationViewModelErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val phoneUtils = mockk<PhoneUtils>(relaxed = true)
    // Mockito for Result-returning methods (MockK double-boxes Result inline class)
    private val verificationController: ContactVerificationController = mock()
    private val profileController = mockk<ProfileController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val featureFlags = mockk<FeatureFlagController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val buyOptionsCache = mockk<BuyOptionsCache>(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() {
        BottomBarManager.clear()

        every { resources.getString(R.string.error_title_failedToSendCodeToPhone) } returns "error_title_failedToSendCodeToPhone"
        every { resources.getString(R.string.error_description_failedToSendCodeToPhone) } returns "error_description_failedToSendCodeToPhone"
        every { resources.getString(R.string.error_title_maxAttemptsReached) } returns "error_title_maxAttemptsReached"
        every { resources.getString(R.string.error_description_maxAttemptsReached) } returns "error_description_maxAttemptsReached"
        every { resources.getString(R.string.error_title_deviceNotSupported) } returns "error_title_deviceNotSupported"
        every { resources.getString(R.string.error_description_deviceNotSupported) } returns "error_description_deviceNotSupported"
        every { resources.getString(R.string.error_description_invalidVerificationCode) } returns "error_description_invalidVerificationCode"
        every { resources.getString(R.string.error_description_codeTimedOut) } returns "error_description_codeTimedOut"
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun createViewModel(): PhoneVerificationViewModel {
        return PhoneVerificationViewModel(
            phoneUtils = phoneUtils,
            verificationController = verificationController,
            profileController = profileController,
            userManager = userManager,
            featureFlags = featureFlags,
            resources = resources,
            dispatchers = dispatchers,
            buyOptionsCache = buyOptionsCache,
        )
    }

    @Test
    fun `send code failure shows failedToSendCodeToPhone error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.sendVerificationCode(any())).thenReturn(Result.failure(RuntimeException("server error")))

        val vm = createViewModel()
        vm.dispatchEvent(PhoneVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_failedToSendCodeToPhone" })
    }

    @Test
    fun `send code with unsupported phone type shows deviceNotSupported error`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.sendVerificationCode(any())).thenReturn(Result.failure(PhoneVerificationError.UnsupportedPhoneType()))

        val vm = createViewModel()
        vm.dispatchEvent(PhoneVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_deviceNotSupported" })
    }

    @Test
    fun `invalid verification code shows error with invalidVerificationCode description`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.checkVerificationCode(any(), any())).thenReturn(Result.failure(PhoneVerificationError.InvalidVerificationCode()))

        val vm = createViewModel()
        vm.dispatchEvent(PhoneVerificationViewModel.Event.OnVerifyCodeClicked)
        advanceUntilIdle()

        assertTrue(BottomBarManager.messages.value.any { it.subtitle == "error_description_invalidVerificationCode" })
    }
}
