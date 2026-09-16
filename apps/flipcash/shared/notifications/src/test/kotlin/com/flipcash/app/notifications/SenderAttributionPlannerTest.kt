package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID
import kotlin.test.Test
import kotlin.test.assertEquals

class SenderAttributionPlannerTest {

    private val alice: ID = listOf(0x0a, 0x0b, 0x0c)

    // region Group chats: the sender is whoever the payload names, or nobody

    @Test
    fun `group push is attributed to the sending user`() {
        assertEquals(
            SenderLookup.ByUserId(alice),
            planSenderLookup(
                chatType = ChatType.GROUP,
                sendingUserId = alice,
                hasDeviceContact = false,
            ),
        )
    }

    @Test
    fun `group push without a sender attributes to nobody rather than a member`() {
        assertEquals(
            SenderLookup.None,
            planSenderLookup(
                chatType = ChatType.GROUP,
                sendingUserId = null,
                hasDeviceContact = false,
            ),
        )
    }

    // endregion

    // region DMs: unchanged behaviour

    @Test
    fun `dm push prefers the device contact over any fetch`() {
        assertEquals(
            SenderLookup.None,
            planSenderLookup(
                chatType = ChatType.CONTACT_DM,
                sendingUserId = alice,
                hasDeviceContact = true,
            ),
        )
    }

    @Test
    fun `dm push without a device contact uses the sending user`() {
        assertEquals(
            SenderLookup.ByUserId(alice),
            planSenderLookup(
                chatType = ChatType.TIP_DM,
                sendingUserId = alice,
                hasDeviceContact = false,
            ),
        )
    }

    @Test
    fun `dm push without a sending user falls back to the other member`() {
        assertEquals(
            SenderLookup.OtherMember,
            planSenderLookup(
                chatType = ChatType.TIP_DM,
                sendingUserId = null,
                hasDeviceContact = false,
            ),
        )
    }

    // endregion

    // region Payloads with no chat metadata at all

    @Test
    fun `unknown chat type without a sending user falls back to the other member`() {
        assertEquals(
            SenderLookup.OtherMember,
            planSenderLookup(
                chatType = null,
                sendingUserId = null,
                hasDeviceContact = false,
            ),
        )
    }

    @Test
    fun `unknown chat type still honours a sending user`() {
        assertEquals(
            SenderLookup.ByUserId(alice),
            planSenderLookup(
                chatType = null,
                sendingUserId = alice,
                hasDeviceContact = false,
            ),
        )
    }

    // endregion
}
