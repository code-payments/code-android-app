package com.flipcash.app.userprofile.internal.username

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.ProfileField
import com.flipcash.analytics.events.ProfileEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameEntryViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val analytics = RecordingAnalytics()
    // Mockito for the Result-returning suspend method (MockK double-boxes Result inline class)
    private val profileController = mock<ProfileController>()

    private fun createViewModel(): UsernameEntryViewModel {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.state } returns MutableStateFlow(UserManager.State())
        return UsernameEntryViewModel(
            userManager = userManager,
            profileController = profileController,
            userFlags = mockk(relaxed = true),
            resources = FakeResourceHelper(),
            exchange = mockk(relaxed = true),
            analytics = analytics,
        )
    }

    // The base ViewModel dispatches events on Dispatchers.Default, so the test scheduler can't
    // drain them; wait in real time instead.
    private suspend fun awaitRealTime(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    @Test
    fun `a claimed username sends Profile Field Saved for the username`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(profileController.setUsername(any())).thenReturn(Result.success(Unit))

        createViewModel().dispatchEvent(UsernameEntryViewModel.Event.CheckUsername)
        awaitRealTime { analytics.events.isNotEmpty() }

        assertEquals(listOf<AnalyticsEvent>(ProfileEvents.fieldSaved(ProfileField.USERNAME)), analytics.events)
    }

    @Test
    fun `a failed username claim sends nothing`() = runTest(mainCoroutineRule.dispatcher) {
        whenever(profileController.setUsername(any())).thenReturn(Result.failure(IllegalStateException("boom")))

        createViewModel().dispatchEvent(UsernameEntryViewModel.Event.CheckUsername)
        // No event to wait for on the failure path; give the pipeline time to run to its end.
        withContext(Dispatchers.Default) { delay(500) }

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }
}
