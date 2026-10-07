package com.flipcash.app.tipping.internal

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.FakeResourceHelper
import com.getcode.view.SuccessHoldDuration
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * A typed handle always resolves to that person's profile, with the origin that makes a block reset
 * to the chat list. Whether a DM exists is the profile's business: its button opens it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FindByUsernameViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userId = List<Byte>(32) { it.toByte() }

    private val profileController = mockk<ProfileController>()
    private val userManager = mockk<UserManager>(relaxed = true)

    private fun TestScope.createViewModel() = FindByUsernameViewModel(
        profileController = profileController,
        userManager = userManager,
        resources = FakeResourceHelper(),
        dispatchers = TestDispatchers(testScheduler),
    )

    private fun TestScope.lookUp(): AppRoute? {
        val found = mockk<UserProfile>(relaxed = true) { every { userId } returns this@FindByUsernameViewModelTest.userId }
        coEvery { profileController.getProfileForUsername("sally") } returns Result.success(found)

        val viewModel = createViewModel()
        var resolved: AppRoute? = null
        backgroundScope.launch {
            viewModel.eventFlow
                .filterIsInstance<FindByUsernameViewModel.Event.UserResolved>()
                .collect { resolved = it.destination }
        }
        viewModel.stateFlow.value.usernameFieldState.setTextAndPlaceCursorAtEnd("sally")
        viewModel.dispatchEvent(FindByUsernameViewModel.Event.LookupUsername)
        advanceTimeBy(SuccessHoldDuration + SuccessHoldDuration)
        advanceUntilIdle()
        return resolved
    }

    @Test
    fun `a handle resolves to the profile`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals(
            AppRoute.Messaging.Profile(ProfileAddress.ById(userId), ProfileOrigin.UsernameLookup),
            lookUp(),
        )
    }
}
