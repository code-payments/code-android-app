package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.financial.Fiat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileActionsTest {

    private val fee = Fiat(1.0)

    // -- resolvePinnedAction --

    @Test
    fun `your own profile has no pinned action`() {
        assertNull(resolvePinnedAction(isSelf = true, isBlocked = false, dmExists = false, fee = fee))
        assertNull(resolvePinnedAction(isSelf = true, isBlocked = true, dmExists = true, fee = fee))
    }

    @Test
    fun `blocked wins over an existing DM`() {
        assertEquals(
            ProfilePinnedAction.Unblock,
            resolvePinnedAction(isSelf = false, isBlocked = true, dmExists = true, fee = fee),
        )
    }

    @Test
    fun `blocked with no DM is still unblock`() {
        assertEquals(
            ProfilePinnedAction.Unblock,
            resolvePinnedAction(isSelf = false, isBlocked = true, dmExists = false, fee = null),
        )
    }

    @Test
    fun `an existing DM opens the chat`() {
        assertEquals(
            ProfilePinnedAction.OpenChat,
            resolvePinnedAction(isSelf = false, isBlocked = false, dmExists = true, fee = fee),
        )
    }

    @Test
    fun `no DM starts chatting with the fee`() {
        assertEquals(
            ProfilePinnedAction.StartChatting(fee),
            resolvePinnedAction(isSelf = false, isBlocked = false, dmExists = false, fee = fee),
        )
    }

    @Test
    fun `no DM and the fee still loading pins nothing`() {
        assertNull(resolvePinnedAction(isSelf = false, isBlocked = false, dmExists = false, fee = null))
    }

    @Test
    fun `an existing DM or a block does not wait for the fee`() {
        assertEquals(
            ProfilePinnedAction.OpenChat,
            resolvePinnedAction(isSelf = false, isBlocked = false, dmExists = true, fee = null),
        )
        assertEquals(
            ProfilePinnedAction.Unblock,
            resolvePinnedAction(isSelf = false, isBlocked = true, dmExists = false, fee = null),
        )
    }

    // -- profileMenuItems --

    @Test
    fun `blocked menu is report then unblock, with or without a DM`() {
        val expected = listOf(ChatProfileAction.Report, ChatProfileAction.Unblock)
        assertEquals(expected, profileMenuItems(isBlocked = true, hasDm = true))
        assertEquals(expected, profileMenuItems(isBlocked = true, hasDm = false))
    }

    @Test
    fun `menu with a DM leads with mute`() {
        assertEquals(
            listOf(ChatProfileAction.Mute, ChatProfileAction.Report, ChatProfileAction.Block),
            profileMenuItems(isBlocked = false, hasDm = true),
        )
    }

    @Test
    fun `menu without a DM has no mute`() {
        assertEquals(
            listOf(ChatProfileAction.Report, ChatProfileAction.Block),
            profileMenuItems(isBlocked = false, hasDm = false),
        )
    }

    @Test
    fun `report and block are destructive, mute and unblock are not`() {
        assertTrue(ChatProfileAction.Report.isDestructive)
        assertTrue(ChatProfileAction.Block.isDestructive)
        assertFalse(ChatProfileAction.Mute.isDestructive)
        assertFalse(ChatProfileAction.Unblock.isDestructive)
    }

    // -- opensChatUnderneath --

    private val dm = ChatId(List(16) { 1.toByte() })
    private val group = ChatId(List(16) { 2.toByte() })

    @Test
    fun `open chat is left off when the DM is the chat underneath`() {
        assertTrue(ProfilePinnedAction.OpenChat.opensChatUnderneath(dmChatId = dm, chatUnderneath = dm))
    }

    @Test
    fun `open chat stays when the chat underneath is another chat`() {
        assertFalse(ProfilePinnedAction.OpenChat.opensChatUnderneath(dmChatId = dm, chatUnderneath = group))
    }

    @Test
    fun `open chat stays with no chat underneath`() {
        assertFalse(ProfilePinnedAction.OpenChat.opensChatUnderneath(dmChatId = dm, chatUnderneath = null))
        assertFalse(ProfilePinnedAction.OpenChat.opensChatUnderneath(dmChatId = null, chatUnderneath = null))
    }

    @Test
    fun `only open chat is ever left off`() {
        assertFalse(ProfilePinnedAction.Unblock.opensChatUnderneath(dmChatId = dm, chatUnderneath = dm))
        assertFalse(ProfilePinnedAction.OpeningChat.opensChatUnderneath(dmChatId = dm, chatUnderneath = dm))
    }

    // -- labelRes --

    @Test
    fun `the pinned button names what it does`() {
        assertEquals(R.string.action_unblock, ProfilePinnedAction.Unblock.labelRes())
        assertEquals(R.string.action_openChat, ProfilePinnedAction.OpenChat.labelRes())
    }

    @Test
    fun `start chatting names the price`() {
        assertEquals(
            R.string.action_sendToStartChatting,
            ProfilePinnedAction.StartChatting(fee).labelRes(),
        )
    }
}
