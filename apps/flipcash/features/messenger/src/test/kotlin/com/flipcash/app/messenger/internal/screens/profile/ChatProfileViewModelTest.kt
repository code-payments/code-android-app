package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.blocklist.BlocklistCoordinator
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Where a person's profile gets its join date from. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatProfileViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val theirId: List<Byte> = List(16) { 2 }
    private val cachedJoin = Instant.fromEpochSeconds(1_700_000_000)
    private val serverJoin = Instant.fromEpochSeconds(1_710_000_000)

    private val profiles = mockk<ProfileController>()
    private val userManager = mockk<UserManager> { every { accountId } returns List(16) { 1 } }

    private fun viewModel() = ChatProfileViewModel(
        contactCoordinator = mockk<ContactCoordinator>(relaxed = true),
        userManager = userManager,
        featureFlags = mockk<FeatureFlagController>(relaxed = true),
        blocklist = mockk<BlocklistCoordinator>(relaxed = true),
        profiles = profiles,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
        resources = mockk<ResourceHelper>(relaxed = true),
    )

    private fun participant(joinedAt: Instant?) = ChatParticipant.TipUser(
        userId = theirId,
        profile = UserProfile(
            displayName = "Sally",
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
            joinedAt = joinedAt,
            userId = theirId,
            username = "sally_streamer",
        ),
    )

    @Test
    fun `a chat's cached participant fetches the join date`() = runTest {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.success(participant(serverJoin).profile)
        val model = viewModel()

        model.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant(cachedJoin)))

        assertEquals(serverJoin, model.stateFlow.value.joinDate)
        coVerify(exactly = 1) { profiles.getProfileForUser(theirId) }
    }

    @Test
    fun `a freshly fetched profile keeps its join date without fetching again`() = runTest {
        val model = viewModel()

        model.dispatchEvent(
            ChatProfileViewModel.Event.OnParticipantSet(participant(serverJoin), fromServer = true)
        )

        assertEquals(serverJoin, model.stateFlow.value.joinDate)
        coVerify(exactly = 0) { profiles.getProfileForUser(any()) }
    }
}
