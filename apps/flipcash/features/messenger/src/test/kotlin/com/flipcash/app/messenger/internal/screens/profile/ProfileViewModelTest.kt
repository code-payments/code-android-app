package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.GetUserProfileError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import com.flipcash.core.R as CoreR

/**
 * A profile opened by link: the address resolves to a person, or the screen is told to leave with
 * the same bottom-bar message a tip card link used to show.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val myId: List<Byte> = List(16) { 1 }
    private val theirId: List<Byte> = List(16) { 2 }

    private val profiles = mockk<ProfileController>()
    private val userManager = mockk<UserManager> { every { accountId } returns myId }
    private val exchange = mockk<Exchange> { every { observePreferredRate() } returns emptyFlow() }
    private val resources = mockk<ResourceHelper> {
        every { getString(CoreR.string.error_title_usernameNotFound) } returns "usernameNotFound"
        every { getString(CoreR.string.error_description_usernameNotFound, *anyVararg()) } returns ""
        every { getString(CoreR.string.error_title_profileUnavailable) } returns "profileUnavailable"
        every { getString(CoreR.string.error_description_profileUnavailable) } returns ""
    }

    @Before
    fun setUp() {
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun viewModel() = ProfileViewModel(
        profiles = profiles,
        userManager = userManager,
        exchange = exchange,
        resources = resources,
        dispatchers = TestDispatcherProvider(mainCoroutineRule.dispatcher),
    )

    private fun profile(userId: List<Byte>? = theirId) = UserProfile(
        displayName = "Sally",
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        joinedAt = Instant.fromEpochSeconds(1_710_000_000),
        userId = userId,
        username = "sally_streamer",
    )

    /** Loads [address] and returns the events the screen would act on. */
    private fun kotlinx.coroutines.test.TestScope.load(
        model: ProfileViewModel,
        address: ProfileAddress,
    ): List<ProfileViewModel.Event> {
        val events = mutableListOf<ProfileViewModel.Event>()
        backgroundScope.launch(mainCoroutineRule.dispatcher) { model.eventFlow.collect(events::add) }
        model.dispatchEvent(ProfileViewModel.Event.Load(address))
        return events
    }

    private fun barTitles() = BottomBarManager.messages.value.map { it.title }

    @Test
    fun `a handle resolves to that person`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUsername("sally_streamer") } returns Result.success(profile())
        val model = viewModel()

        load(model, ProfileAddress.ByUsername("sally_streamer"))

        assertEquals(ChatParticipant.TipUser(theirId, profile()), model.stateFlow.value.participant)
        assertTrue(barTitles().isEmpty())
    }

    @Test
    fun `an id resolves through the id lookup`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUser(theirId) } returns Result.success(profile())
        val model = viewModel()

        load(model, ProfileAddress.ById(theirId))

        assertEquals(theirId, model.stateFlow.value.participant?.userId)
        coVerify(exactly = 0) { profiles.getProfileForUsername(any()) }
    }

    @Test
    fun `an unclaimed handle says nobody has it and leaves`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUsername("nobody_here") } returns
            Result.failure(GetUserProfileError.NotFound())
        val model = viewModel()

        val events = load(model, ProfileAddress.ByUsername("nobody_here"))

        assertEquals(listOf("usernameNotFound"), barTitles())
        assertTrue(ProfileViewModel.Event.Unavailable in events)
        assertNull(model.stateFlow.value.participant)
    }

    @Test
    fun `a failed fetch is reported as an error and leaves`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUsername("sally_streamer") } returns
            Result.failure(IllegalStateException("offline"))
        val model = viewModel()

        val events = load(model, ProfileAddress.ByUsername("sally_streamer"))

        assertEquals(listOf("profileUnavailable"), barTitles())
        assertTrue(ProfileViewModel.Event.Unavailable in events)
    }

    // An id nobody owns is a failed lookup, not an unclaimed handle: there's no name to put in
    // "Nobody has claimed @…", which is how the tip card path told the two apart as well.
    @Test
    fun `an id nobody owns is reported as unavailable`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUser(theirId) } returns
            Result.failure(GetUserProfileError.NotFound())
        val model = viewModel()

        val events = load(model, ProfileAddress.ById(theirId))

        assertEquals(listOf("profileUnavailable"), barTitles())
        assertTrue(ProfileViewModel.Event.Unavailable in events)
    }

    @Test
    fun `a profile without an id can't be messaged and leaves`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUsername("sally_streamer") } returns
            Result.success(profile(userId = null))
        val model = viewModel()

        val events = load(model, ProfileAddress.ByUsername("sally_streamer"))

        assertTrue(ProfileViewModel.Event.Unavailable in events)
        assertNull(model.stateFlow.value.participant)
    }

    // The router can't match a handle before this account's own profile has arrived.
    @Test
    fun `a handle that turns out to be the viewer's own is handed back`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { profiles.getProfileForUsername("me_myself") } returns
            Result.success(profile(userId = myId))
        val model = viewModel()

        val events = load(model, ProfileAddress.ByUsername("me_myself"))

        assertTrue(ProfileViewModel.Event.OwnProfile in events)
        assertNull(model.stateFlow.value.participant)
        assertTrue(barTitles().isEmpty())
    }
}
