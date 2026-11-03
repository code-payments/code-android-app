package com.flipcash.app.tipping.internal

import com.flipcash.app.core.data.Loadable
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatListFilter
import com.flipcash.shared.chat.ChatListProjection
import com.flipcash.shared.chat.ui.ConversationReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatsViewModelStateTest {

    private fun reduce(state: ChatsViewModel.State, event: ChatsViewModel.Event) =
        ChatsViewModel.updateStateForEvent(event)(state)

    private val dm = ConversationReference(chatId = ChatId(byteArrayOf(1)))
    private val group = ConversationReference(chatId = ChatId(byteArrayOf(2)), isGroup = true)

    private val projection = ChatListProjection.Empty.copy(
        main = listOf(dm.chatId.toString(), group.chatId.toString()),
        unreadChip = listOf(dm.chatId.toString()),
        groupsChip = listOf(group.chatId.toString()),
        archivedRowVisible = true,
        archivedRowCount = 2,
    )

    private val loaded = reduce(
        ChatsViewModel.State(),
        ChatsViewModel.Event.ChatsUpdated(Loadable.Loaded(listOf(dm, group)), projection),
    )

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
        assertEquals(listOf(dm, group), loaded.visibleChats)
        assertEquals(
            listOf(dm),
            reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Unread)).visibleChats,
        )
        assertEquals(
            listOf(group),
            reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Groups)).visibleChats,
        )
    }

    @Test
    fun `the Archived row shows only under All`() {
        assertTrue(loaded.showsArchivedRow)
        assertFalse(reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Unread)).showsArchivedRow)
        assertFalse(reduce(loaded, ChatsViewModel.Event.FilterSelected(ChatListFilter.Groups)).showsArchivedRow)
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
