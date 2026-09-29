package com.flipcash.app.messenger.internal.screens.components

import com.flipcash.services.models.chat.MessageContent
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

    private fun shown(rows: List<ChatListItem.ContentBubble>, settlingKey: Any?): List<Boolean> =
        rows.indices.map { i ->
            shouldShowReceiptLabel(i, rows[i], { rows.getOrNull(it) }, otherReadPointer = null, settlingKey)
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
}
