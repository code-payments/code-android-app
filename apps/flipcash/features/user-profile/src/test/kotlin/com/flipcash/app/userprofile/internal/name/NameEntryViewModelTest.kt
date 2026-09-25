package com.flipcash.app.userprofile.internal.name

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PropertyValue
import com.flipcash.analytics.events.DisplayNameEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.services.controllers.ModerationController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

/**
 * Covers the two branches [NameEntryViewModel] picks between on [NameEntryViewModel.Event.CheckName]:
 * whether the account already had a display name (read from [UserManager.profile] before the
 * write) decides `Display Name Set` vs `Display Name Updated`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NameEntryViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userManager = mockk<UserManager>(relaxed = true)
    private val analytics = RecordingAnalytics()
    private val moderationController = mockk<ModerationController>(relaxed = true)
    // Mockito for the Result-returning suspend method (MockK double-boxes Result inline class)
    private val profileController = mock<ProfileController>()
    private val resources = FakeResourceHelper()

    private lateinit var dispatchers: TestDispatchers

    private fun createViewModel() = NameEntryViewModel(
        userManager = userManager,
        analytics = analytics,
        moderationController = moderationController,
        profileController = profileController,
        resources = resources,
        dispatchers = dispatchers,
    )

    private fun profileNamed(name: String) = UserProfile(
        displayName = name,
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
    )

    @Test
    fun `first name on an account without one tracks Display Name Set`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { userManager.state } returns MutableStateFlow(UserManager.State())
        every { userManager.profile } returns null
        whenever(profileController.setDisplayName(any())).thenReturn(Result.success(Unit))

        val vm = createViewModel()
        vm.dispatchEvent(NameEntryViewModel.Event.CheckName(DisplayNameSource.Onboarding))
        advanceUntilIdle()

        assertEquals(
            listOf(DisplayNameEvents.set(DisplayNameSource.Onboarding.analytics)),
            analytics.events,
        )
    }

    @Test
    fun `replacing an existing name tracks the literal Display Name Updated event`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { userManager.state } returns MutableStateFlow(UserManager.State())
        every { userManager.profile } returns profileNamed("Ada")
        whenever(profileController.setDisplayName(any())).thenReturn(Result.success(Unit))

        val vm = createViewModel()
        vm.dispatchEvent(NameEntryViewModel.Event.CheckName(DisplayNameSource.MyAccount))
        advanceUntilIdle()

        assertEquals(
            listOf(
                AnalyticsEvent(
                    "Display Name Updated",
                    mapOf("Source" to PropertyValue.Text("My Account")),
                )
            ),
            analytics.events,
        )
    }
}
