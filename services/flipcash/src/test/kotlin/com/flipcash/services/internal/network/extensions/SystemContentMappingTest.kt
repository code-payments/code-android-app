package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.MessageContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The server's `SystemContent.fallback_text` reaching the app as a notice, and blank ones not. */
class SystemContentMappingTest {

    private fun system(text: String) = MessagingModel.Content.newBuilder()
        .setSystem(MessagingModel.SystemContent.newBuilder().setFallbackText(text))
        .build()

    private fun message(vararg contents: MessagingModel.Content) = MessagingModel.Message.newBuilder()
        .setMessageId(MessagingModel.MessageId.newBuilder().setValue(7))
        .addAllContent(contents.toList())
        .build()

    @Test
    fun `fallback text maps to a system content`() {
        assertEquals(
            MessageContent.System("Alex joined the group"),
            system("Alex joined the group").toMessageContent(),
        )
    }

    @Test
    fun `a notice keeps its content`() {
        assertEquals(
            listOf(MessageContent.System("Alex joined the group")),
            message(system("Alex joined the group")).toChatMessage().content,
        )
    }

    @Test
    fun `blank notices drop their content and keep the message`() {
        listOf("", "   ", "\n\t", "\u0085", "\u00A0").forEach { blank ->
            val mapped = message(system(blank)).toChatMessage()
            assertEquals(emptyList(), mapped.content, "blank ${blank.map { it.code }}")
            assertEquals(7L, mapped.messageId)
        }
    }

    @Test
    fun `the unit separator is text, not blank`() {
        assertEquals(
            listOf(MessageContent.System("\u001F")),
            message(system("\u001F")).toChatMessage().content,
        )
    }

    @Test
    fun `only the blank content of a message is dropped`() {
        assertEquals(
            listOf(MessageContent.System("Sam left")),
            message(system(" "), system("Sam left")).toChatMessage().content,
        )
    }
}
