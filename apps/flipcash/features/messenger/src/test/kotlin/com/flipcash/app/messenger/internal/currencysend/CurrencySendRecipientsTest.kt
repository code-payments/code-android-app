package com.flipcash.app.messenger.internal.currencysend

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.ui.ConversationReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CurrencySendRecipientsTest {
    private var next = 0

    private fun chat(
        name: String?,
        handle: String? = null,
        type: ChatType = ChatType.TIP_DM,
        activity: Long = 0,
        withUser: Boolean = true,
    ) = ConversationReference(
        chatId = ChatId(ByteArray(32) { (next + 1).toByte() }.also { next++ }),
        userId = if (withUser && type != ChatType.GROUP) ByteArray(32) { 1 }.toList() else null,
        displayName = name,
        handle = handle,
        isGroup = type == ChatType.GROUP,
        chatType = type,
        lastActivity = Instant.fromEpochSeconds(activity),
    )

    @Test
    fun `groups and contact dms never appear`() {
        val result = listOf(
            chat("Noah", activity = 3),
            chat(null, type = ChatType.GROUP, activity = 9),
            chat("Contact", type = ChatType.CONTACT_DM, activity = 8),
        ).currencySendRecipients()
        assertEquals(listOf("Noah"), result.map { it.displayName })
    }

    @Test
    fun `a chat with no counterparty is dropped`() {
        val result = listOf(chat("Ghost", withUser = false), chat("Real")).currencySendRecipients()
        assertEquals(listOf("Real"), result.map { it.displayName })
    }

    @Test
    fun `newest activity first and ties keep input order`() {
        val result = listOf(
            chat("Old", activity = 1),
            chat("TieA", activity = 5),
            chat("New", activity = 9),
            chat("TieB", activity = 5),
        ).currencySendRecipients()
        assertEquals(listOf("New", "TieA", "TieB", "Old"), result.map { it.displayName })
    }

    @Test
    fun `search matches name or username and ignores a leading at sign`() {
        val list = listOf(
            chat("Noah Parker", handle = "noahp"),
            chat("Chloe Anderson", handle = "chloe"),
        )
        assertEquals(listOf("Noah Parker"), list.matching("noah").map { it.displayName })
        assertEquals(listOf("Chloe Anderson"), list.matching("@CHL").map { it.displayName })
        assertEquals(2, list.matching("  ").size)
        assertEquals(0, list.matching("zzz").size)
    }
}
