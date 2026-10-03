package com.flipcash.app.messenger.internal

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatSummary
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The mute sheet opens with its unmute row already decided, so the seed must find the chat whether
 * it sits in the main list or in the archived one.
 */
class MuteChatViewerStateSeedTest {

    private val chatCoordinator = mockk<ChatCoordinator>(relaxed = true)
    private val chatId = ChatId(ByteArray(16) { 3 }.toList())
    private val muted = ViewerState(mute = MuteState.Forever, version = 1L)

    private fun summary() = ChatSummary(
        metadata = ChatMetadata(
            chatId = chatId,
            type = ChatType.GROUP,
            members = emptyList(),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(1),
            viewerState = muted,
        ),
        unreadCount = 0,
    )

    private fun viewModel() = MuteChatViewModel(chatCoordinator, mockk(relaxed = true), RecordingAnalytics())

    @Test
    fun `a chat in the main list seeds from it`() {
        every { chatCoordinator.currentFeed(*anyVararg()) } returns listOf(summary())
        every { chatCoordinator.currentArchivedFeed(*anyVararg()) } returns emptyList()
        assertEquals(muted, viewModel().currentViewerState(chatId))
    }

    @Test
    fun `an archived chat seeds from the archived list`() {
        every { chatCoordinator.currentFeed(*anyVararg()) } returns emptyList()
        every { chatCoordinator.currentArchivedFeed(*anyVararg()) } returns listOf(summary())
        assertEquals(muted, viewModel().currentViewerState(chatId))
    }

    @Test
    fun `a chat in neither list has no seed`() {
        every { chatCoordinator.currentFeed(*anyVararg()) } returns null
        every { chatCoordinator.currentArchivedFeed(*anyVararg()) } returns null
        assertNull(viewModel().currentViewerState(chatId))
    }
}
