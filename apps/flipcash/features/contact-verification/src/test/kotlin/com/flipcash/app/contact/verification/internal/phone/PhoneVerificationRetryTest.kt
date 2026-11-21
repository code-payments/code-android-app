package com.flipcash.app.contact.verification.internal.phone

import androidx.lifecycle.ViewModelStore
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
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
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

/**
 * In onboarding, reaching the attempt limit returns the user to phone entry instead of closing
 * the flow, and the flow's view model lives on. The limit must start over there, or every later
 * Next tap would show the limit again with no way forward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneVerificationRetryTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val verificationController: ContactVerificationController = mock()

    private lateinit var dispatchers: TestDispatchers

    // A sent code starts the resend countdown on a java.util.Timer thread. Clearing the view model
    // stops it before the rule resets Dispatchers.Main; a tick after that throws into a later test.
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        BottomBarManager.clear()
    }

    private fun createViewModel() = PhoneVerificationViewModel(
        phoneUtils = mockk<PhoneUtils>(relaxed = true),
        verificationController = verificationController,
        profileController = mockk<ProfileController>(relaxed = true),
        userManager = mockk<UserManager>(relaxed = true),
        featureFlags = mockk<FeatureFlagController>(relaxed = true),
        resources = FakeResourceHelper(),
        dispatchers = dispatchers,
        analytics = mockk(relaxed = true),
    ).also { viewModels.put("phone", it) }

    @Test
    fun `a code can be requested again after the attempt limit`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        whenever(verificationController.sendVerificationCode(any())).thenReturn(Result.success(Unit))
        val vm = createViewModel()

        repeat(3) {
            vm.dispatchEvent(PhoneVerificationViewModel.Event.OnSendCodeClicked)
            advanceUntilIdle()
        }
        vm.dispatchEvent(PhoneVerificationViewModel.Event.OnMaxAttemptsReached)
        advanceUntilIdle()
        clearInvocations(verificationController)

        vm.dispatchEvent(PhoneVerificationViewModel.Event.OnSendCodeClicked)
        advanceUntilIdle()

        assertEquals(1, vm.stateFlow.value.attempts)
        verify(verificationController).sendVerificationCode(any())
    }
}
