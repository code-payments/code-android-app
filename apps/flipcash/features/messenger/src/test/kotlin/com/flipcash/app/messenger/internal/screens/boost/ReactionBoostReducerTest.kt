package com.flipcash.app.messenger.internal.screens.boost

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.payments.boost.BoostChat
import com.flipcash.shared.payments.boost.ReactionBoostSplit
import com.flipcash.shared.chat.reactions.SelfReaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReactionBoostReducerTest {

    private val chat = ChatId(ByteArray(32) { 1 })

    private fun reduce(
        state: ReactionBoostViewModel.State,
        event: ReactionBoostViewModel.Event,
    ): ReactionBoostViewModel.State = ReactionBoostViewModel.updateStateForEvent(event)(state)

    private fun opened(vararg own: String): ReactionBoostViewModel.State = reduce(
        reduce(ReactionBoostViewModel.State(), ReactionBoostViewModel.Event.Open(chat, 7)),
        ReactionBoostViewModel.Event.OwnReactionsChanged(own.toList()),
    )

    @Test
    fun `the viewer's reactions are offered most recent first`() {
        val own = listOf(
            SelfReaction("👍", Instant.fromEpochSeconds(10)),
            SelfReaction("🔥", Instant.fromEpochSeconds(30)),
            SelfReaction("💯", Instant.fromEpochSeconds(20)),
        )

        assertEquals(listOf("🔥", "💯", "👍"), ReactionBoostViewModel.boostable(own))
    }

    @Test
    fun `the sheet starts on the most recent reaction`() {
        val state = opened("🔥", "💯", "👍")

        assertEquals("🔥", state.emoji)
        assertTrue(state.canCycle)
    }

    @Test
    fun `tapping the emoji cycles through the viewer's reactions and wraps`() {
        var state = opened("🔥", "💯", "👍")
        val seen = (1..3).map {
            state = reduce(state, ReactionBoostViewModel.Event.CycleEmoji)
            state.emoji
        }

        assertEquals(listOf("💯", "👍", "🔥"), seen)
    }

    @Test
    fun `a single reaction doesn't cycle`() {
        val state = opened("🔥")

        assertFalse(state.canCycle)
        assertEquals("🔥", reduce(state, ReactionBoostViewModel.Event.CycleEmoji).emoji)
    }

    @Test
    fun `the chosen reaction is kept while it's still the viewer's`() {
        val cycled = reduce(opened("🔥", "💯"), ReactionBoostViewModel.Event.CycleEmoji)

        val state = reduce(cycled, ReactionBoostViewModel.Event.OwnReactionsChanged(listOf("👍", "🔥", "💯")))

        assertEquals("💯", state.emoji)
    }

    @Test
    fun `taking back the chosen reaction moves to the next most recent`() {
        val cycled = reduce(opened("🔥", "💯"), ReactionBoostViewModel.Event.CycleEmoji)

        val state = reduce(cycled, ReactionBoostViewModel.Event.OwnReactionsChanged(listOf("🔥")))

        assertEquals("🔥", state.emoji)
    }

    @Test
    fun `no reaction left disables the swipe`() {
        val picked = reduce(opened("🔥"), ReactionBoostViewModel.Event.SelectAmount(100))
        assertTrue(picked.canConfirm)

        val state = reduce(picked, ReactionBoostViewModel.Event.OwnReactionsChanged(emptyList()))

        assertEquals(null, state.emoji)
        assertFalse(state.canConfirm)
    }

    @Test
    fun `a DM boosts without a creator and a group needs one`() {
        val creator = List<Byte>(32) { 9 }
        assertEquals(BoostChat.Dm, ReactionBoostViewModel.boostChatOf(ChatType.CONTACT_DM, null))
        assertEquals(BoostChat.Dm, ReactionBoostViewModel.boostChatOf(ChatType.TIP_DM, null))
        assertEquals(BoostChat.Group(creator), ReactionBoostViewModel.boostChatOf(ChatType.GROUP, creator))
        assertEquals(null, ReactionBoostViewModel.boostChatOf(ChatType.GROUP, null))
        assertEquals(null, ReactionBoostViewModel.boostChatOf(ChatType.UNKNOWN, creator))
    }

    @Test
    fun `the subtitle follows who the boost pays`() {
        val creator = List<Byte>(32) { 9 }
        val member = List<Byte>(32) { 2 }
        val other = List<Byte>(32) { 3 }
        val group = BoostChat.Group(creator)
        fun copy(payer: List<Byte>, sender: List<Byte>, chat: BoostChat) = ReactionBoostViewModel.splitCopyOf(
            chat,
            ReactionBoostSplit.plan(payer, sender, chat, 100),
        )

        assertEquals(ReactionBoostViewModel.SplitCopy.Dm, copy(member, other, BoostChat.Dm))
        assertEquals(ReactionBoostViewModel.SplitCopy.Shared, copy(member, other, group))
        assertEquals(ReactionBoostViewModel.SplitCopy.Whole, copy(creator, other, group))
        assertEquals(ReactionBoostViewModel.SplitCopy.Whole, copy(member, creator, group))
        assertEquals(null, ReactionBoostViewModel.splitCopyOf(group, ReactionBoostSplit.plan(member, member, group, 100)))
        assertEquals(null, ReactionBoostViewModel.splitCopyOf(null, null))
    }

    @Test
    fun `an unresolved boost names what was missing`() {
        fun reason(hasAuthor: Boolean, type: ChatType?, hasCreator: Boolean) =
            ReactionBoostViewModel.unresolvedReason(hasAuthor, type, hasCreator)

        assertEquals("group has no creator", reason(true, ChatType.GROUP, false))
        assertEquals("no chat metadata", reason(true, null, false))
        assertEquals("unknown chat type", reason(true, ChatType.UNKNOWN, true))
        assertEquals("no author", reason(false, ChatType.TIP_DM, false))
        assertEquals("no author, group has no creator", reason(false, ChatType.GROUP, false))
    }
}
