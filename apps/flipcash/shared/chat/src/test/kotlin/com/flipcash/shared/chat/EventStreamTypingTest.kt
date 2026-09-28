package com.flipcash.shared.chat

import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.TypingNotification
import com.flipcash.services.models.chat.TypingState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Typing notifications from the stream reach [ChatState.typingIndicators], minus the user's own. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventStreamTypingTest {

    private val self = listOf<Byte>(1)
    private val alice = listOf<Byte>(2)
    private val chat = ChatId("aabbccdd")

    private val updates = Channel<ChatUpdate>(Channel.UNLIMITED)
    private val stateHolder = ChatStateHolder()

    private val delegate = EventStreamDelegate(
        eventStreamingController = mockk<EventStreamingController>(relaxed = true).also {
            every { it.chatUpdates } returns updates.receiveAsFlow()
            every { it.streamFailures } returns emptyFlow()
            every { it.isConnected } returns false
        },
        messagingController = mockk(relaxed = true),
        metadataDataSource = mockk(relaxed = true),
        messageDataSource = mockk(relaxed = true),
        memberDataSource = mockk(relaxed = true),
        tokenCoordinator = mockk(relaxed = true),
        userManager = mockk<UserManager>(relaxed = true).also { every { it.accountId } returns self },
        stateHolder = stateHolder,
        analytics = mockk(relaxed = true),
        exchange = mockk(relaxed = true),
    )

    private fun typing(vararg notifications: Pair<List<Byte>, TypingState>) = ChatUpdate(
        chatId = chat,
        typingNotifications = notifications.map { (userId, state) -> TypingNotification(userId, state) },
    )

    @Test
    fun `a counterpart's typing shows and the user's own does not`() = runTest {
        delegate.initialize(backgroundScope)
        delegate.open()

        updates.send(typing(self to TypingState.STARTED_TYPING, alice to TypingState.STARTED_TYPING))
        runCurrent()

        assertEquals(listOf(alice), stateHolder.current.typingIndicators[chat]?.map { it.userId })
    }

    @Test
    fun `clearAll drops typists`() = runTest {
        delegate.initialize(backgroundScope)
        delegate.open()
        updates.send(typing(alice to TypingState.STARTED_TYPING))
        runCurrent()

        delegate.clearAll()

        assertTrue(stateHolder.current.typingIndicators.isEmpty())
    }
}
