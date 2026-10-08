package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.WebPreviewGate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebPreviewGateTest {

    private val chatId = ChatId(ByteArray(16) { it.toByte() })
    private val other = ChatId(ByteArray(16) { (it + 1).toByte() })

    private val stateHolder = ChatStateHolder()
    private val metadata = mockk<ChatMetadataDataSource>()
    private val gate = WebPreviewGate(stateHolder, metadata)

    private fun open(chat: ChatId?) = stateHolder.update { it.copy(activeChat = chat) }

    private fun stored(type: ChatType, isMember: Boolean = true) {
        coEvery { metadata.getChatType(chatId) } returns type
        coEvery { metadata.isMember(chatId) } returns isMember
    }

    @Test
    fun `a chat that is not the open one is refused`() = runTest {
        stored(ChatType.CONTACT_DM)
        open(other)
        assertFalse(gate.allows(chatId))
    }

    @Test
    fun `nothing open refuses every chat`() = runTest {
        stored(ChatType.CONTACT_DM)
        open(null)
        assertFalse(gate.allows(chatId))
    }

    @Test
    fun `the open group is refused to a non-member`() = runTest {
        stored(ChatType.GROUP, isMember = false)
        open(chatId)
        assertFalse(gate.allows(chatId))
    }

    @Test
    fun `the open group is allowed to a member`() = runTest {
        stored(ChatType.GROUP, isMember = true)
        open(chatId)
        assertTrue(gate.allows(chatId))
    }

    @Test
    fun `an open DM is allowed without reading membership`() = runTest {
        stored(ChatType.CONTACT_DM, isMember = false)
        open(chatId)
        assertTrue(gate.allows(chatId))

        stored(ChatType.TIP_DM, isMember = false)
        assertTrue(gate.allows(chatId))
        coVerify(exactly = 0) { metadata.isMember(any()) }
    }

    @Test
    fun `an open chat of unknown type is refused`() = runTest {
        stored(ChatType.UNKNOWN)
        open(chatId)
        assertFalse(gate.allows(chatId))
    }
}
