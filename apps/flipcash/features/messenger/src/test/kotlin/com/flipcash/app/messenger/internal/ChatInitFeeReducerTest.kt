package com.flipcash.app.messenger.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The chat-init fee is what the call-to-action pill spends its whole width saying, and it arrives
 * over the network after the pill has already rendered. The reducer's job is only to carry the
 * formatted value — including back to null when the conversation stops being one that needs a fee.
 */
class ChatInitFeeReducerTest {

    private fun reduce(
        state: ChatViewModel.State,
        event: ChatViewModel.Event,
    ): ChatViewModel.State = ChatViewModel.updateStateForEvent(event)(state)

    @Test
    fun `chat init fee starts null`() {
        assertNull(ChatViewModel.State().chatInitFee)
    }

    @Test
    fun `chat init fee update stores the formatted amount`() {
        val state = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.OnChatInitFeeUpdated("$1.00"),
        )

        assertEquals("$1.00", state.chatInitFee)
    }

    @Test
    fun `chat init fee clears when the fee no longer applies`() {
        val withFee = reduce(
            ChatViewModel.State(),
            ChatViewModel.Event.OnChatInitFeeUpdated("$1.00"),
        )

        val cleared = reduce(withFee, ChatViewModel.Event.OnChatInitFeeUpdated(null))

        assertNull(cleared.chatInitFee)
    }
}
