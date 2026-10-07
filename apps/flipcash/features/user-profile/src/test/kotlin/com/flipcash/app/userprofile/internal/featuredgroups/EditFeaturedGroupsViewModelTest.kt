package com.flipcash.app.userprofile.internal.featuredgroups

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.SetFeaturedGroupsError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.FeaturedGroupsStore
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EditFeaturedGroupsViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatController = mockk<ChatController>()
    private val store = FeaturedGroupsStore(chatController)
    private val userManager = mockk<UserManager>(relaxed = true)

    private val a = ChatId("aa")
    private val b = ChatId("bb")
    private val c = ChatId("cc")

    @Before
    @After
    fun clearAlerts() = BottomBarManager.clear()

    private fun group(
        hex: String,
        type: ChatType = ChatType.GROUP,
        isPrivate: Boolean = false,
        title: String? = hex,
    ) = ChatMetadata(
        chatId = ChatId(hex),
        type = type,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(0),
        title = title,
        isPrivate = isPrivate,
    )

    private fun feed(vararg chats: ChatMetadata) = Result.success(
        ChatFeedPage(chats = chats.toList(), pagingToken = null, hasMore = false)
    )

    /** [featured] is what the server returns when the screen re-reads; [joined] is the group feed. */
    private fun TestScope.viewModel(
        cached: List<ChatMetadata> = emptyList(),
        featured: Result<List<ChatMetadata>> = Result.success(cached),
        joined: Result<ChatFeedPage> = feed(),
    ): EditFeaturedGroupsViewModel {
        store.replace(cached)
        every { userManager.state } returns MutableStateFlow(
            UserManager.State(userProfile = UserProfile.Empty.copy(username = "me"))
        )
        coEvery { chatController.getFeaturedGroups(any()) } returns featured
        coEvery { chatController.getGroupChatFeed(any()) } returns joined
        return EditFeaturedGroupsViewModel(
            userManager = userManager,
            chatController = chatController,
            store = store,
            resources = FakeResourceHelper(),
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    private val EditFeaturedGroupsViewModel.state get() = stateFlow.value
    private fun EditFeaturedGroupsViewModel.toggle(chatId: ChatId) =
        dispatchEvent(EditFeaturedGroupsViewModel.Event.Toggle(chatId))

    private fun EditFeaturedGroupsViewModel.search(text: String) {
        state.queryState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `candidates are the featured groups then the joined public ones`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(
            cached = listOf(group("bb"), group("aa")),
            joined = feed(
                group("aa"),
                group("cc"),
                group("dd", isPrivate = true),
                group("ee", type = ChatType.CONTACT_DM),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf(b, a, c), vm.state.candidates.map { it.chatId })
        assertEquals(listOf(b, a), vm.state.selection)
        assertEquals(EditFeaturedGroupsViewModel.LoadState.Loaded, vm.state.loadState)
    }

    @Test
    fun `a featured group the user has left stays on the list`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(cached = listOf(group("aa")), joined = feed(group("cc")))
        advanceUntilIdle()

        assertEquals(listOf(a, c), vm.state.candidates.map { it.chatId })
        assertEquals(listOf(a), vm.state.selection)
    }

    @Test
    fun `new picks go on the end and a removal keeps the order of the rest`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(joined = feed(group("aa"), group("bb"), group("cc")))
        advanceUntilIdle()

        vm.toggle(b)
        vm.toggle(a)
        vm.toggle(c)
        assertEquals(listOf(b, a, c), vm.state.selection)

        vm.toggle(a)
        assertEquals(listOf(b, c), vm.state.selection)
    }

    @Test
    fun `selection stops at the limit and a picked group can still be dropped`() = runTest(mainCoroutineRule.dispatcher) {
        val all = (0..10).map { group("%02x".format(it)) }
        val vm = viewModel(joined = feed(*all.toTypedArray()))
        advanceUntilIdle()

        all.take(FeaturedGroupsLimit).forEach { vm.toggle(it.chatId) }
        assertTrue(vm.state.atLimit)

        val eleventh = all.last().chatId
        assertFalse(vm.state.isEnabled(eleventh))
        vm.toggle(eleventh)
        assertEquals(FeaturedGroupsLimit, vm.state.selection.size)
        assertFalse(eleventh in vm.state.selection)

        val first = all.first().chatId
        assertTrue(vm.state.isEnabled(first))
        vm.toggle(first)
        assertEquals(FeaturedGroupsLimit - 1, vm.state.selection.size)
    }

    @Test
    fun `save needs a real change`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(joined = feed(group("aa")))
        advanceUntilIdle()
        assertFalse(vm.state.canSave)

        vm.toggle(a)
        assertTrue(vm.state.canSave)

        vm.toggle(a)
        assertFalse(vm.state.canSave)
    }

    @Test
    fun `a pick made before the load finishes survives it`() {
        val reduce = EditFeaturedGroupsViewModel.updateStateForEvent
        val opened = EditFeaturedGroupsViewModel.State.seededWith(emptyList(), "Group Chat")

        val picked = reduce(EditFeaturedGroupsViewModel.Event.Toggle(a))(opened)
        assertFalse(picked.canSave)

        val read = reduce(EditFeaturedGroupsViewModel.Event.OnFeaturedRead(listOf(b)))(picked)
        val loaded = reduce(EditFeaturedGroupsViewModel.Event.OnCandidatesLoaded(emptyList()))(read)

        assertEquals(listOf(a), loaded.selection)
        assertEquals(listOf(b), loaded.saved)
        assertTrue(loaded.canSave)
    }

    @Test
    fun `a failed read of the featured list blocks saving`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(
            cached = listOf(group("aa")),
            featured = Result.failure(IllegalStateException("offline")),
        )
        advanceUntilIdle()

        assertEquals(EditFeaturedGroupsViewModel.LoadState.Failed, vm.state.loadState)
        vm.toggle(b)
        assertFalse(vm.state.canSave)
    }

    @Test
    fun `an untouched selection follows the server over a stale cache`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(
            cached = listOf(group("aa")),
            featured = Result.success(listOf(group("bb"), group("cc"))),
        )
        advanceUntilIdle()

        assertEquals(listOf(b, c), vm.state.selection)
        assertFalse(vm.state.canSave)
    }

    @Test
    fun `saving sends the picked order and keeps what the server returns`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(joined = feed(group("aa"), group("bb")))
        advanceUntilIdle()
        val returned = listOf(group("bb"), group("aa"))
        coEvery { chatController.setFeaturedGroups(any()) } returns Result.success(returned)

        vm.toggle(b)
        vm.toggle(a)
        vm.dispatchEvent(EditFeaturedGroupsViewModel.Event.Save)
        advanceUntilIdle()

        coVerify { chatController.setFeaturedGroups(listOf(b, a)) }
        assertEquals(returned, store.groups.value)
    }

    @Test
    fun `clearing every group saves an empty list`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(cached = listOf(group("aa")))
        advanceUntilIdle()
        coEvery { chatController.setFeaturedGroups(any()) } returns Result.success(emptyList())

        vm.toggle(a)
        assertTrue(vm.state.canSave)
        vm.dispatchEvent(EditFeaturedGroupsViewModel.Event.Save)
        advanceUntilIdle()

        coVerify { chatController.setFeaturedGroups(emptyList()) }
    }

    @Test
    fun `a denied save explains the private group and keeps the selection`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(joined = feed(group("aa")))
        advanceUntilIdle()
        coEvery { chatController.setFeaturedGroups(any()) } returns
            Result.failure(SetFeaturedGroupsError.Denied())

        vm.toggle(a)
        vm.dispatchEvent(EditFeaturedGroupsViewModel.Event.Save)
        advanceUntilIdle()

        assertEquals(
            "error_title_featuredGroupsPrivate",
            BottomBarManager.messages.value.firstOrNull()?.title,
        )
        assertEquals(listOf(a), vm.state.selection)
        assertTrue(vm.state.processingState.isIdle)
    }

    @Test
    fun `any other failed save shows the generic error`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(joined = feed(group("aa")))
        advanceUntilIdle()
        coEvery { chatController.setFeaturedGroups(any()) } returns
            Result.failure(SetFeaturedGroupsError.NotFound())

        vm.toggle(a)
        vm.dispatchEvent(EditFeaturedGroupsViewModel.Event.Save)
        advanceUntilIdle()

        assertEquals(
            "error_title_featuredGroupsSaveFailed",
            BottomBarManager.messages.value.firstOrNull()?.title,
        )
        assertTrue(vm.state.processingState.isIdle)
    }

    @Test
    fun `search matches titles ignoring case and outer spaces`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = viewModel(
            joined = feed(
                group("aa", title = "Solana Devs"),
                group("bb", title = "Cooking"),
            ),
        )
        advanceUntilIdle()

        vm.search("  SOLANA ")
        advanceUntilIdle()
        assertEquals(listOf(a), vm.state.visibleCandidates.map { it.chatId })

        vm.search("   ")
        advanceUntilIdle()
        assertEquals(2, vm.state.visibleCandidates.size)
    }
}
