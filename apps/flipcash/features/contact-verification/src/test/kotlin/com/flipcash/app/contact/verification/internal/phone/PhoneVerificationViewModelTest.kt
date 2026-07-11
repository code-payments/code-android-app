package com.flipcash.app.contact.verification.internal.phone

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.phone.CountryLocale
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.services.controllers.ContactVerificationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneVerificationViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val testLocale = CountryLocale(name = "Nigeria", phoneCode = 234, countryCode = "NG", resId = 1)

    private val phoneUtils: PhoneUtils = mockk(relaxed = true) {
        coEvery { ensureLoaded() } returns Unit
        every { defaultCountryLocale } returns testLocale
        every { formatNumber(any(), any(), any()) } returns ""
        every { isPhoneNumberValid(any(), any()) } returns false
        every { parseInternationalNumber(any()) } returns null
    }
    private val verificationController: ContactVerificationController = mockk(relaxed = true)
    private val profileController: ProfileController = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val featureFlags: FeatureFlagController = mockk(relaxed = true)
    private val resources = FakeResourceHelper()

    private fun createViewModel(dispatchers: TestDispatchers) = PhoneVerificationViewModel(
        phoneUtils = phoneUtils,
        verificationController = verificationController,
        profileController = profileController,
        userManager = userManager,
        featureFlags = featureFlags,
        resources = resources,
        dispatchers = dispatchers,
    )

    /**
     * Verifies that construction does not eagerly read defaultCountryLocale (i.e. initialState
     * starts as Stub), and that after ensureLoaded() the state is populated with the real locale.
     *
     * With the old code (initialState = State(selectedLocale = phoneUtils.defaultCountryLocale)),
     * the first assertion (Stub) would fail because defaultCountryLocale is read during construction.
     */
    @Test
    fun `initial state is Stub before coroutines run`() = runTest(mainCoroutineRule.dispatcher) {
        val dispatchers = TestDispatchers(testScheduler)
        val viewModel = createViewModel(dispatchers)

        // With OLD code: selectedLocale = testLocale (eager read in initialState)
        // With NEW code: selectedLocale = Stub (no eager read; init coroutine hasn't advanced yet)
        assertEquals(CountryLocale.Stub, viewModel.stateFlow.value.selectedLocale)
    }

    @Test
    fun `starts on Stub then populates default locale after ensureLoaded`() = runTest(mainCoroutineRule.dispatcher) {
        val dispatchers = TestDispatchers(testScheduler)
        val viewModel = createViewModel(dispatchers)

        // Before advancing: construction must not have read defaultCountryLocale eagerly
        assertEquals(CountryLocale.Stub, viewModel.stateFlow.value.selectedLocale)

        // After advancing: ensureLoaded completes, OnCountrySelected is dispatched
        advanceUntilIdle()
        assertEquals(testLocale, viewModel.stateFlow.value.selectedLocale)
    }
}
