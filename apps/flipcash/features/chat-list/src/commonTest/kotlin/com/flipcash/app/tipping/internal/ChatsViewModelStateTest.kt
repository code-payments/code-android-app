package com.flipcash.app.tipping.internal

import com.flipcash.app.core.data.Loadable
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatListFilter
import com.flipcash.shared.chat.ChatListProjection
import com.flipcash.shared.chat.ui.ConversationReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ChatsViewModelStateTest {

    private fun reduce(state: ChatsViewModel.State, event: ChatsViewModel.Event) =
        ChatsViewModel.updateStateForEvent(event)(state)

    private val dm = ConversationReference(chatId = ChatId(byteArrayOf(1)))
    private val group = ConversationReference(chatId = ChatId(byteArrayOf(2)), isGroup = true)
    private val unreadDm = ConversationReference(chatId = ChatId(byteArrayOf(3)))

    private fun ids(vararg chats: ConversationReference) = chats.map { it.chatId.toString() }

    // Unread, Groups and DMs each hold different chats, so a chip reading the wrong list fails.
    private val projection = ChatListProjection.Empty.copy(
        main = ids(dm, group, unreadDm),
        unreadChip = ids(group, unreadDm),
        groupsChip = ids(group),
        dmsChip = ids(dm, unreadDm),
        archivedRowVisible = true,
        archivedRowCount = 2,
    )

    private val loaded = reduce(
        ChatsViewModel.State(),
        ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(listOf(dm, group, unreadDm)), projection),
    )

    private val groupMoney = reduce(loaded, ChatsViewModel.Event.GroupMoneyChanged(on = true))

    @Test
    fun `chips start hidden under All`() {
        assertFalse(ChatsViewModel.State().showsChips)
        assertFalse(loaded.showsChips)
    }

    @Test
    fun `a reveal holds across later feed updates`() {
        val revealed = reduce(loaded, ChatsViewModel.Event.ChipsRevealed)
        val updated = reduce(
            revealed,
            ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(listOf(group)), projection),
        )
        assertTrue(updated.chipsRevealed)
        assertTrue(updated.showsChips)
    }

    @Test
    fun `choosing a filter shows the chips and stays revealed back on All`() {
        val unread = reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Unread))
        assertTrue(unread.showsChips)
        val all = reduce(unread, ChatsViewModel.Event.FilterSelected(ChatListFilter.All))
        assertTrue(all.showsChips)
    }

    @Test
    fun `each filter shows its own rows in feed order`() {
        assertEquals(listOf(dm, group, unreadDm), loaded.visibleChats)
        assertEquals(
            listOf(group, unreadDm),
            reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Unread)).visibleChats,
        )
        assertEquals(
            listOf(group),
            reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Groups)).visibleChats,
        )
        assertEquals(
            listOf(dm, unreadDm),
            reduce(groupMoney, ChatsViewModel.Event.FilterSelected(ChatListFilter.DMs)).visibleChats,
        )
    }

    @Test
    fun `the third chip is Groups, or DMs under the group-money UI`() {
        assertEquals(ChatListFilter.Groups, ChatsViewModel.State().kindFilter)
        assertEquals(ChatListFilter.Groups, loaded.kindFilter)
        assertEquals(ChatListFilter.DMs, groupMoney.kindFilter)
        assertEquals(
            ChatListFilter.Groups,
            reduce(groupMoney, ChatsViewModel.Event.GroupMoneyChanged(on = false)).kindFilter,
        )
    }

    @Test
    fun `the third chip counts the unread chats of its kind`() {
        val counted = reduce(
            ChatsViewModel.State(),
            ChatsViewModel.Event.ChatsUpdated(
                Loadable.Loaded(listOf(dm, group, unreadDm)),
                projection.copy(groupsChipCount = 3, dmsChipCount = 5),
            ),
        )
        assertEquals(3, counted.kindChipCount)
        assertEquals(5, reduce(counted, ChatsViewModel.Event.GroupMoneyChanged(on = true)).kindChipCount)
    }

    @Test
    fun `a selected chip that leaves the row falls back to All`() {
        val groups = reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Groups))
        assertEquals(ChatListFilter.All, reduce(groups, ChatsViewModel.Event.GroupMoneyChanged(on = true)).filter)

        val dms = reduce(groupMoney, ChatsViewModel.Event.FilterSelected(ChatListFilter.DMs))
        assertEquals(ChatListFilter.All, reduce(dms, ChatsViewModel.Event.GroupMoneyChanged(on = false)).filter)
    }

    @Test
    fun `a chip still in the row stays selected when the flag is reported again or flips`() {
        // The screen reports the flag each time it enters composition, usually unchanged.
        val dms = reduce(groupMoney, ChatsViewModel.Event.FilterSelected(ChatListFilter.DMs))
        assertEquals(ChatListFilter.DMs, reduce(dms, ChatsViewModel.Event.GroupMoneyChanged(on = true)).filter)

        val groups = reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Groups))
        assertEquals(ChatListFilter.Groups, reduce(groups, ChatsViewModel.Event.GroupMoneyChanged(on = false)).filter)

        val unread = reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Unread))
        assertEquals(ChatListFilter.Unread, reduce(unread, ChatsViewModel.Event.GroupMoneyChanged(on = true)).filter)
    }

    @Test
    fun `the Archived row shows only under All`() {
        assertTrue(loaded.showsArchivedRow)
        assertFalse(reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Unread)).showsArchivedRow)
        assertFalse(reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Groups)).showsArchivedRow)
        assertFalse(reduce(groupMoney, ChatsViewModel.Event.FilterSelected(ChatListFilter.DMs)).showsArchivedRow)
    }

    @Test
    fun `an empty main list with archived chats is not the no-chats prompt`() {
        val archivedOnly = reduce(
            ChatsViewModel.State(),
            ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(emptyList()), projection.copy(main = emptyList())),
        )
        assertFalse(archivedOnly.hasNoChatsAtAll)
        assertTrue(archivedOnly.visibleChats.isEmpty())

        val nothing = reduce(
            ChatsViewModel.State(),
            ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(emptyList()), ChatListProjection.Empty),
        )
        assertTrue(nothing.hasNoChatsAtAll)
    }
}
