package com.flipcash.app.messenger.internal.screens

import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.reactions.ReactionPill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactorsReducerTest {

    private val chat = ChatId(ByteArray(32) { 1 })

    private fun reduce(
        state: ReactorsViewModel.State,
        event: ReactorsViewModel.Event,
    ): ReactorsViewModel.State = ReactorsViewModel.updateStateForEvent(event)(state)

    private fun pill(emoji: String, count: Long) =
        ReactionPill(emoji = emoji, count = count, selfReacted = false, pending = false)

    private fun updated(vararg pills: ReactionPill) = ReactorsViewModel.Event.Updated(
        pills = pills.toList(),
        rows = emptyList(),
        loading = false,
        hasMore = false,
    )

    @Test
    fun `opening names the message and starts loading`() {
        val state = reduce(ReactorsViewModel.State(), ReactorsViewModel.Event.Open(chat, 1))

        assertEquals(chat, state.chatId)
        assertEquals(1L, state.messageId)
        assertTrue(state.loading)
    }

    @Test
    fun `an update replaces the pills, rows and paging`() {
        val opened = reduce(ReactorsViewModel.State(), ReactorsViewModel.Event.Open(chat, 1))

        val state = reduce(opened, updated(pill("👍", 3)))

        assertEquals(listOf(pill("👍", 3)), state.pills)
        assertEquals(false, state.loading)
    }

    @Test
    fun `reopening the same message keeps what it has loaded`() {
        val loaded = reduce(
            reduce(ReactorsViewModel.State(), ReactorsViewModel.Event.Open(chat, 1)),
            updated(pill("👍", 3)),
        )

        assertEquals(loaded, reduce(loaded, ReactorsViewModel.Event.Open(chat, 1)))
    }

    @Test
    fun `opening another message starts over`() {
        val loaded = reduce(
            reduce(ReactorsViewModel.State(), ReactorsViewModel.Event.Open(chat, 1)),
            updated(pill("👍", 3)),
        )

        val state = reduce(loaded, ReactorsViewModel.Event.Open(chat, 2))

        assertEquals(ReactorsViewModel.State(chatId = chat, messageId = 2), state)
    }
}
