package com.flipcash.app.messenger.internal

import com.flipcash.analytics.ChatType as AnalyticsChatType
import com.flipcash.analytics.MuteDuration
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.ChatEvents
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.services.models.MuteChatError
import com.flipcash.services.models.UnmuteChatError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MuteState
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** `Chat Muted` and `Chat Unmuted` are sent when the RPC answers, carrying the route's chat type. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MuteChatAnalyticsTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val analytics = RecordingAnalytics()

    private val chatId = ChatId(ByteArray(16) { 3 }.toList())

    @Before
    fun setUp() = BottomBarManager.clear()

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun viewModel() = MuteChatViewModel(chatCoordinator, resources, analytics)

    @Test
    fun `a mute reports the option picked`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.mute(any(), any()) } returns Result.success(Unit)

        viewModel().mute(chatId, ChatType.TIP_DM, MuteOption.EightHours)
        advanceUntilIdle()

        coVerify { chatCoordinator.mute(chatId, match { it is MuteState.Until }) }
        assertEquals(
            listOf(
                ChatEvents.muted(
                    AnalyticsChatType.TIP, MuteDuration.EIGHT_HOURS, AnalyticsState.SUCCESS, error = null,
                )
            ),
            analytics.events,
        )
    }

    @Test
    fun `a refused mute reports the result name`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.mute(any(), any()) } returns Result.failure(MuteChatError.NotFound())

        viewModel().mute(chatId, ChatType.GROUP, MuteOption.Forever)
        advanceUntilIdle()

        assertEquals(
            listOf(
                ChatEvents.muted(
                    AnalyticsChatType.GROUP, MuteDuration.ALWAYS, AnalyticsState.FAILURE, error = "NotFound",
                )
            ),
            analytics.events,
        )
    }

    @Test
    fun `an unmute that fails in transport reports Network`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.unmute(any()) } returns Result.failure(RuntimeException("io"))

        viewModel().unmute(chatId, ChatType.CONTACT_DM)
        advanceUntilIdle()

        assertEquals(
            listOf(ChatEvents.unmuted(AnalyticsChatType.CONTACT, AnalyticsState.FAILURE, error = "Network")),
            analytics.events,
        )
    }

    @Test
    fun `an unmute reports Success`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { chatCoordinator.unmute(any()) } returns Result.success(Unit)

        viewModel().unmute(chatId, ChatType.GROUP)
        advanceUntilIdle()

        assertEquals(
            listOf(ChatEvents.unmuted(AnalyticsChatType.GROUP, AnalyticsState.SUCCESS, error = null)),
            analytics.events,
        )
    }
}
