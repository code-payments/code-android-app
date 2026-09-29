package com.flipcash.app.messenger.internal.screens.components

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ReceiptStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * While the viewer's newest send settles, the receipt stays on the row above it, and moves down in
 * one step when the hold releases. Rows are newest first, as the reversed transcript holds them.
 */
class ReceiptHandoffTest {

    private fun mine(id: Long, status: ReceiptStatus, clientId: String? = null) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("m$id"),
        isFromSelf = true,
        timestamp = Instant.fromEpochSeconds(id * 60),
        receiptStatus = status,
        pendingClientIdHex = clientId,
    )

    private fun theirs(id: Long) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("t$id"),
        isFromSelf = false,
        timestamp = Instant.fromEpochSeconds(id * 60),
    )

    private fun readUpTo(id: Long) = MessagePointer(
        type = PointerType.READ,
        userId = listOf(2.toByte()),
        value = id,
        timestamp = Instant.fromEpochSeconds(0),
    )

    private fun shown(
        rows: List<ChatListItem.ContentBubble>,
        settlingKey: Any? = null,
        read: MessagePointer? = null,
    ): List<Boolean> = rows.indices.map { i ->
        shouldShowReceiptLabel(i, rows[i], { rows.getOrNull(it) }, read, settlingKey)
    }

    @Test
    fun `a confirmed send that is still settling leaves the line on the row above`() {
        val send = mine(2, ReceiptStatus.SENT, clientId = "c2")
        val rows = listOf(send, mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(false, true), shown(rows, settlingKey = send.messageKey))
    }

    @Test
    fun `once the hold releases the line is on the new row only`() {
        val rows = listOf(mine(2, ReceiptStatus.SENT, clientId = "c2"), mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(true, false), shown(rows, settlingKey = null))
    }

    @Test
    fun `a send still in flight behind the hold leaves the line above as it was`() {
        val send = mine(0, ReceiptStatus.SENDING, clientId = "c2")
        val rows = listOf(send, mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(false, true), shown(rows, settlingKey = send.messageKey))
    }

    @Test
    fun `a failed send shows its own line during the hold and keeps the one above`() {
        val send = mine(0, ReceiptStatus.FAILED, clientId = "c2")
        val rows = listOf(send, mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(true, true), shown(rows, settlingKey = send.messageKey))
    }

    @Test
    fun `a first send that is settling shows no line anywhere`() {
        val send = mine(1, ReceiptStatus.SENT, clientId = "c1")
        assertEquals(listOf(false), shown(listOf(send), settlingKey = send.messageKey))
    }

    @Test
    fun `Read stays on the last read message while a newer one shows Delivered`() {
        val rows = listOf(mine(3, ReceiptStatus.SENT), mine(2, ReceiptStatus.SENT), mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(true, true, false), shown(rows, read = readUpTo(2)))
    }

    @Test
    fun `when the newest message is read it carries the only line`() {
        val rows = listOf(mine(2, ReceiptStatus.SENT), mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(true, false), shown(rows, read = readUpTo(2)))
    }

    @Test
    fun `Read skips the counterpart's messages to land on the last read one of mine`() {
        val rows = listOf(mine(3, ReceiptStatus.SENT), theirs(2), mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(true, false, true), shown(rows, read = readUpTo(2)))
    }

    @Test
    fun `older delivered sends show nothing, however far back`() {
        val rows = listOf(mine(5, ReceiptStatus.SENT), theirs(4), mine(3, ReceiptStatus.SENT), theirs(2), mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(true, false, false, false, false), shown(rows))
    }

    @Test
    fun `a settling send keeps the Read line where it is`() {
        val send = mine(2, ReceiptStatus.SENT, clientId = "c2")
        val rows = listOf(send, mine(1, ReceiptStatus.SENT))
        assertEquals(listOf(false, true), shown(rows, settlingKey = send.messageKey, read = readUpTo(1)))
    }
}
