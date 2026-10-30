package com.flipcash.app.messenger.internal

import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.BanUserError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.GroupRosters
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `Group: Member Removed` follows the ban call, and only after the host confirms. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberModerationAnalyticsTest {

    private val chatController = mockk<ChatController>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true) {
        every { getString(any<Int>()) } returns "text"
        every { getString(any<Int>(), *anyVararg()) } returns "text"
    }
    private val groupRosters = mockk<GroupRosters>(relaxed = true)
    private val analytics = RecordingAnalytics()
    private val chatId = ChatId(ByteArray(32) { 3 }.toList())
    private val userId = listOf<Byte>(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BottomBarManager.clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        BottomBarManager.clear()
    }

    private fun viewModel() = MemberModerationViewModel(chatController, resources, groupRosters, analytics)

    private fun MemberModerationViewModel.confirm() {
        confirmRemove(chatId, userId, "Ada", "Group")
        BottomBarManager.messages.value.single().actions.single().onClick()
    }

    @Test
    fun `a removed member sends Member Removed as a success`() {
        coEvery { chatController.banUser(chatId, userId) } returns Result.success(Unit)

        viewModel().confirm()

        assertEquals(
            listOf(GroupEvents.memberRemoved(state = AnalyticsState.SUCCESS, error = null)),
            analytics.events,
        )
    }

    @Test
    fun `a failed ban sends Member Removed with the error`() {
        coEvery { chatController.banUser(chatId, userId) } returns Result.failure(BanUserError.NotFound())

        viewModel().confirm()

        assertEquals(
            listOf(GroupEvents.memberRemoved(state = AnalyticsState.FAILURE, error = "NotFound")),
            analytics.events,
        )
    }

    @Test
    fun `asking to remove sends nothing until the host confirms`() {
        viewModel().confirmRemove(chatId, userId, "Ada", "Group")

        assertTrue(analytics.events.isEmpty())
    }
}
