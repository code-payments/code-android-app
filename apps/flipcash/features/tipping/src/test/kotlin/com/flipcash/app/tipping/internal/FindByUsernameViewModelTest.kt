package com.flipcash.app.tipping.internal

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.blocklist.DmDestinationResolver
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
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
 * A typed handle resolves to the DM with that person when one exists, and to their profile until
 * then. The destination itself is [DmDestinationResolver]'s answer; what is pinned here is that the
 * lookup hands it the origin that makes a block reset to the chat list, and passes it on untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FindByUsernameViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val userId = List<Byte>(32) { it.toByte() }
    private val dmChatId = ChatId(ByteArray(32) { (it + 2).toByte() })

    private val profileController = mockk<ProfileController>()
    private val userManager = mockk<UserManager>(relaxed = true)
    private val dmDestinations = mockk<DmDestinationResolver>()

    private val profile = AppRoute.Messaging.Profile(
        ProfileAddress.ById(userId),
        ProfileOrigin.UsernameLookup,
    )
    private val chat = AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(dmChatId))

    private fun TestScope.createViewModel() = FindByUsernameViewModel(
        profileController = profileController,
        userManager = userManager,
        dmDestinations = dmDestinations,
        resources = FakeResourceHelper(),
        dispatchers = TestDispatchers(testScheduler),
    )

    private fun TestScope.lookUp(destination: AppRoute): AppRoute? {
        val found = mockk<UserProfile>(relaxed = true) { every { userId } returns this@FindByUsernameViewModelTest.userId }
        coEvery { profileController.getProfileForUsername("sally") } returns Result.success(found)
        coEvery { dmDestinations.dmDestination(userId, ProfileOrigin.UsernameLookup) } returns destination

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
    fun `a handle with no DM resolves to the profile`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals(profile, lookUp(profile))
    }

    @Test
    fun `a handle with a DM resolves to the chat`() = runTest(mainCoroutineRule.dispatcher) {
        assertEquals(chat, lookUp(chat))
    }
}
