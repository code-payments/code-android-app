package com.flipcash.app.messenger.internal

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.shared.chat.ChatDraftReply
import com.flipcash.shared.chat.ChatDraftSnapshot
import com.flipcash.shared.chat.ChatDraftSnippet
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.chatDraftOf
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.financial.Fiat
import com.getcode.view.LoadingSuccessState
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two answers the chat screen derives rather than stores: whether it is being read from outside
 * the group, and whether there is an invite to hand out.
 *
 * Both are read by more than one renderer — the blur and the placeholder ask the first, the empty
 * state and the transcript's invite action ask the second — so what is pinned here is that each has
 * exactly one source and that a DM is never mistaken for a gated group.
 */
class ChatViewModelStateTest {

    private val groupUuid = UUID.fromString("0189d5f1-3c6a-7b4e-8f21-9c3d4e5f6a7b")
    private val groupChatId = ChatId(groupUuid.bytes)
    private val unmet = ChatRuleRequirement.MinimumBalance(Fiat(100.0), emptyList())

    private fun group(isMember: Boolean?) = ChatSubject.Group(
        chatId = groupChatId,
        groupTitle = "Ballers",
        picture = null,
        memberCount = 1L,
        rules = null,
        isMember = isMember,
    )

    private val dm = ChatSubject.Contact(
        ChatParticipant.Contact(
            DeviceContact(
                e164 = "+15551234567",
                androidContactId = 1L,
                displayName = "Ada Lovelace",
                photoUri = null,
                displayNumber = "(555) 123-4567",
            )
        )
    )

    @Test
    fun `a group the viewer is outside of is a gated preview`() {
        assertTrue(
            ChatViewModel.State(
                subject = group(isMember = false),
                groupAccess = GroupAccess.Blocked(unmet),
            ).isGatedPreview
        )
        // Eligible is still outside: the transcript stays withheld until the join lands, which is
        // what makes "Join Chat" and "Buy More" the same screen with two buttons.
        assertTrue(
            ChatViewModel.State(
                subject = group(isMember = false),
                groupAccess = GroupAccess.Eligible,
            ).isGatedPreview
        )
    }

    @Test
    fun `the blur is on before the gate has decided anything`() {
        // The reason this reads the subject and not the access: access arrives through the balance
        // and staff flows, so it is null for their first frames, while the info card draws as soon
        // as the subject resolves. Off the access alone, a withheld transcript rendered sharp and
        // then blurred — which is showing the thing it is meant to withhold.
        assertTrue(
            ChatViewModel.State(
                subject = group(isMember = false),
                groupAccess = null,
            ).isGatedPreview
        )
        // The same frame for a member is the other answer, not a pending one: nothing about their
        // balance can put them outside a group they are in.
        assertFalse(
            ChatViewModel.State(
                subject = group(isMember = true),
                groupAccess = null,
            ).isGatedPreview
        )
    }

    @Test
    fun `a member reads the group unblurred`() {
        assertFalse(
            ChatViewModel.State(
                subject = group(isMember = true),
                groupAccess = GroupAccess.Membered,
            ).isGatedPreview
        )
    }

    @Test
    fun `the gate holds while the join button is showing its checkmark`() {
        // The roster can confirm membership before the checkmark has been drawn. Holding the gate
        // for as long as the success is up keeps the blur and the button in place for the beat, so
        // the composer does not arrive over a transcript that is still blurred.
        assertTrue(
            ChatViewModel.State(
                subject = group(isMember = true),
                groupAccess = GroupAccess.Membered,
                joinProgress = LoadingSuccessState(success = true),
            ).isGatedPreview
        )
    }

    @Test
    fun `clearing the join state hands the member the group like any other`() {
        // Success is cleared once the hold elapses, and that is the handover: nothing else about
        // this state changed.
        assertFalse(
            ChatViewModel.State(
                subject = group(isMember = true),
                groupAccess = GroupAccess.Membered,
                joinProgress = LoadingSuccessState(),
            ).isGatedPreview
        )
    }

    @Test
    fun `a group whose membership is unknown stays withheld`() {
        // A chat hydrated by id — an invite link or a push tap into a group the device has never
        // synced — cannot know whether the viewer is in it, because GetChat does not say. Withheld
        // is the safe half of that: a member whose feed has not landed yet reads the blur for a
        // beat, where the other way round would hand a non-member the transcript outright.
        assertTrue(
            ChatViewModel.State(
                subject = group(isMember = null),
                groupAccess = null,
            ).isGatedPreview
        )
        // Still withheld once the balance answers: what the button offers is decided by the access,
        // not the blur.
        assertTrue(
            ChatViewModel.State(
                subject = group(isMember = null),
                groupAccess = GroupAccess.Eligible,
            ).isGatedPreview
        )
    }

    @Test
    fun `an unknown membership has no invite to share`() {
        // The link is an action, not a view: offering it would invite people into a group this
        // device cannot yet say the viewer belongs to.
        assertNull(ChatViewModel.State(subject = group(isMember = null)).groupInviteUrl)
    }

    @Test
    fun `a DM has no gate to render`() {
        // The null access is the whole point: a default would blur every contact conversation.
        assertFalse(ChatViewModel.State(subject = dm, groupAccess = null).isGatedPreview)
        assertFalse(ChatViewModel.State().isGatedPreview)
    }

    @Test
    fun `only a group the viewer has joined has an invite to share`() {
        assertEquals(
            "https://app.flipcash.com/chat/$groupUuid",
            ChatViewModel.State(subject = group(isMember = true)).groupInviteUrl,
        )
        assertNull(ChatViewModel.State(subject = group(isMember = false)).groupInviteUrl)
        assertNull(ChatViewModel.State(subject = dm).groupInviteUrl)
        assertNull(ChatViewModel.State().groupInviteUrl)
    }

    @Test
    fun `a chat id with no uuid form yields no link rather than a malformed one`() {
        // A DM's id is a 32-byte hash. Reaching this through a Group subject is unreachable in the
        // app, but the property is what the invite action reads, so it answers rather than throws.
        val hashed = ChatSubject.Group(
            chatId = ChatId(ByteArray(32) { 9 }.toList()),
            groupTitle = "Ballers",
            picture = null,
            memberCount = 1L,
            rules = null,
            isMember = true,
        )
        assertNull(ChatViewModel.State(subject = hashed).groupInviteUrl)
    }

    @Test
    fun `the reserve is named on a button but not beside an amount`() {
        val dollars = RuleCurrency(name = "Dollars", isReserve = true)
        // The button still has something to say — "Buy More Dollars" is a thing to go and do.
        assertEquals("Dollars", dollars.name)
        // The requirement line does not: "$100 of Dollars" says dollars twice.
        assertNull(dollars.nameInRequirement)
    }

    @Test
    fun `every other token is named in both places`() {
        val jeffy = RuleCurrency(name = "Jeffy", isReserve = false)
        assertEquals("Jeffy", jeffy.name)
        // Without the name, "$100" would not say which holding clears the bar.
        assertEquals("Jeffy", jeffy.nameInRequirement)
    }

    /**
     * The other half of the strip's independence from the transcript: a citation that was stored
     * and read back has to render the same as the one the transcript mapped. Both accents are
     * derived rather than stored, so what has to survive the row is the sender id they come from.
     */
    @Test
    fun `a stored reply strip comes back rendering the same`() {
        val quote = ChatQuote(
            messageId = 42L,
            authorName = "Ana",
            snippet = ChatQuoteSnippet.Text("are we still on for 5?"),
            accent = null,
            nameAccent = null,
            senderIdHex = "0f1e2d3c4b5a6978",
        )

        val restored = quote.toDraftReply().toChatQuote()

        assertEquals(quote.messageId, restored.messageId)
        assertEquals(quote.authorName, restored.authorName)
        assertEquals(quote.snippet, restored.snippet)
        assertEquals(quote.senderIdHex, restored.senderIdHex)
        // Derived, so they arrive non-null on the way back even though the fixture above left them
        // unset — which is the point: the colours are a function of the id, not of the transcript.
        assertNotNull(restored.accent)
        assertNotNull(restored.nameAccent)
    }

    /**
     * Rule 5 against the real reducer rather than a model of it. `Event.EditMessage` is what moves
     * the user's words into the stash, and everything downstream reads them from there — so an
     * edit left mid-flight persists the draft it displaced and never the message being edited.
     */
    @Test
    fun `an edit persists the draft it displaced`() {
        val typed = ChatViewModel.State()
        typed.chatInputState.setTextAndPlaceCursorAtEnd("half a thought")

        val editing = ChatViewModel.updateStateForEvent(
            ChatViewModel.Event.EditMessage(messageId = 7L, text = "an older message")
        )(typed)
        // Entering an edit puts the message body in the field; the reducer stashes what was there.
        editing.chatInputState.setTextAndPlaceCursorAtEnd("an older message")

        assertEquals(
            ChatDraftSnapshot(text = "half a thought", replyTarget = null),
            editing.draftSnapshot(),
        )
    }

    /**
     * An edit takes the composer, reply strip included — so a reply aimed and then left for an edit
     * is not part of what the edit stashed. Pinned because it is the one place the persisted draft
     * is narrower than what was on screen a moment earlier, and it follows from a reducer rule
     * written for a different reason.
     */
    @Test
    fun `a reply aimed before an edit is not stashed with it`() {
        val aimed = ChatViewModel.updateStateForEvent(
            ChatViewModel.Event.ReplyToMessage(
                ChatQuote(
                    messageId = 42L,
                    authorName = "Ana",
                    snippet = ChatQuoteSnippet.Text("are we still on for 5?"),
                    accent = null,
                    nameAccent = null,
                )
            )
        )(ChatViewModel.State())
        aimed.chatInputState.setTextAndPlaceCursorAtEnd("half a thought")

        val editing = ChatViewModel.updateStateForEvent(
            ChatViewModel.Event.EditMessage(messageId = 7L, text = "an older message")
        )(aimed)

        assertNull(editing.replyingTo)
        assertEquals(
            ChatDraftSnapshot(text = "half a thought", replyTarget = null),
            editing.draftSnapshot(),
        )
    }

    /**
     * Cancelling an edit is the same answer by the path the app already had: the stash goes back in
     * the field, and from then on it is an ordinary draft.
     */
    @Test
    fun `cancelling an edit leaves an ordinary draft`() {
        val editing = ChatViewModel.updateStateForEvent(
            ChatViewModel.Event.EditMessage(messageId = 7L, text = "an older message")
        )(ChatViewModel.State().also { it.chatInputState.setTextAndPlaceCursorAtEnd("half a thought") })

        val ended = ChatViewModel.updateStateForEvent(ChatViewModel.Event.EditingEnded)(editing)
        ended.chatInputState.setTextAndPlaceCursorAtEnd("half a thought")

        assertNull(ended.editing)
        assertEquals(
            ChatDraftSnapshot(text = "half a thought", replyTarget = null),
            ended.draftSnapshot(),
        )
    }

    /**
     * The composer once a send has emptied it. The store deletes on this rather than storing it,
     * which is what keeps a chat typed in once and sent from restoring an empty draft for ever.
     */
    @Test
    fun `an emptied composer is no draft at all`() {
        val sent = ChatViewModel.updateStateForEvent(
            ChatViewModel.Event.CancelReply
        )(ChatViewModel.State())
        sent.chatInputState.setTextAndPlaceCursorAtEnd("")

        assertTrue(sent.draftSnapshot().isEmpty)
    }

    /** What the ViewModel's own `draftSnapshot` composes, over a state the reducer produced. */
    private fun ChatViewModel.State.draftSnapshot(): ChatDraftSnapshot = chatDraftOf(
        composerText = chatInputState.text.toString(),
        replyTarget = replyingTo?.toDraftReply(),
        editStash = editing?.stashedDraft,
    )
}
