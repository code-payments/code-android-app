package com.flipcash.shared.chat.media

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatMediaSendPlanTest {

    @Test
    fun `whitespace around the caption is trimmed`() {
        assertEquals(
            listOf(ChatMediaSendPlan.Message.Media("a", "look", null)),
            ChatMediaSendPlan.build(listOf("a"), "  look \n", null),
        )
    }

    @Test
    fun `whitespace alone is no caption and no text message`() {
        assertEquals(
            listOf(ChatMediaSendPlan.Message.Media("a", null, null)),
            ChatMediaSendPlan.build(listOf("a"), "   ", null),
        )
        assertEquals(emptyList(), ChatMediaSendPlan.build(emptyList(), "   ", 5))
    }
}
