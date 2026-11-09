package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import com.flipcash.app.messenger.internal.request.NewRequestDraft.People
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.paymentrequest.NewShare
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class NewRequestDraftTest {

    private val viewer = id(0)
    private val twenty = usd(2_000)
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun id(n: Int): ID = listOf((n shr 8).toByte(), n.toByte())

    /** [cents] US cents, built from quarks so no double rounding creeps in. */
    private fun usd(cents: Long) = Fiat(quarks = cents * 10_000, currencyCode = CurrencyCode.USD)

    private fun draft(memberCount: Int, rosterViewerAt: Int = 3, amount: Fiat = twenty) = run {
        // The viewer sits mid-roster to show the creator still goes first.
        val others = (1 until memberCount).map(::id)
        val roster = others.toMutableList().apply { add(rosterViewerAt.coerceAtMost(size), viewer) }
            .map { ChatMember(it, mockk<UserProfile>(relaxed = true), emptyList()) }
        NewRequestDraft(
            viewerId = viewer,
            members = NewRequestDraft.members(viewer, roster),
            title = "Saturday Request",
            amount = amount,
        )
    }

    // 14
    @Test
    fun `20 each between everyone asks 25 and sends 26 shares of 20 creator first`() {
        val draft = draft(26)

        assertEquals(People.EveryoneIncludingYou, draft.people)
        assertTrue(draft.people.markedPaid)
        assertEquals(25, draft.requestedCount)
        assertEquals(26, draft.shares.size)
        assertEquals(viewer, draft.shares.first())
        assertEquals((1 until 26).map(::id), draft.shares.drop(1))
        assertEquals(draft.shares, draft.split!!.map { it.userId })
        assertEquals(List(26) { twenty }, draft.split!!.map { it.amount })
        assertEquals(usd(52_000), draft.totalAmount)
    }

    // 15
    @Test
    fun `20 each for the creator and one other reads one person including you`() {
        val draft = draft(26).copy(selected = setOf(viewer, id(4)))

        assertEquals(People.SomeIncludingYou(1), draft.people)
        assertEquals(1, draft.requestedCount)
        assertEquals(listOf(viewer, id(4)), draft.shares)
        assertEquals(listOf(twenty, twenty), draft.split!!.map { it.amount })
        assertEquals(usd(4_000), draft.totalAmount)
    }

    // 16
    @Test
    fun `creator deselected reads everyone except you and leaves the creator out`() {
        val start = draft(26)
        val draft = start.copy(selected = start.toggled(viewer)!!)

        assertEquals(People.EveryoneExceptYou, draft.people)
        assertFalse(draft.people.markedPaid)
        assertEquals(25, draft.shares.size)
        assertFalse(viewer in draft.shares)
    }

    // 17
    @Test
    fun `only the creator selected cannot continue`() {
        val draft = draft(26).copy(selected = setOf(viewer))

        assertFalse(draft.canContinue)
        assertNull(draft.toSplit(ChatId(byteArrayOf(1)), sealed(), now))
    }

    // 18
    @Test
    fun `checking a 101st share is refused and the selection is unchanged`() {
        val draft = draft(150)
        assertEquals(People.Choose, draft.people)
        assertFalse(draft.canContinue)

        val hundred = draft.members.take(100).toSet()
        val full = draft.copy(selected = hundred)

        assertNull(full.toggled(draft.members[100]))
        assertEquals(hundred, full.selected)
        assertEquals(hundred.minusElement(draft.members[5]), full.toggled(draft.members[5]))
    }

    @Test
    fun `some members without the creator count them all`() {
        val draft = draft(26).copy(selected = setOf(id(1), id(2)))

        assertEquals(People.Some(2), draft.people)
        assertFalse(draft.people.markedPaid)
    }

    @Test
    fun `a preset resolves against the moment send is tapped`() {
        val draft = draft(5).copy(deadline = Deadline.Preset.FourWeeks)

        val split = draft.toSplit(ChatId(byteArrayOf(1)), sealed(), now)!!

        assertEquals(now + 28.days - 5.minutes, split.endsAt)
        assertEquals(CurrencyCode.USD, split.currency)
        assertEquals(draft.split, split.shares)
    }

    // 49
    @Test
    fun `creator unchecked leaves 25 shares of 20 and a 500 total`() {
        val start = draft(26)
        val draft = start.copy(selected = start.toggled(viewer)!!)

        assertEquals(25, draft.shares.size)
        assertEquals(List(25) { twenty }, draft.split!!.map { it.amount })
        assertEquals(usd(50_000), draft.totalAmount)
    }

    // 50
    @Test
    fun `every share is the entered amount with no rounding`() {
        val draft = draft(5, amount = usd(3_334)).copy(selected = setOf(viewer, id(1), id(2)))

        assertEquals(listOf(viewer, id(1), id(2)), draft.split!!.map { it.userId })
        assertEquals(List(3) { usd(3_334) }, draft.split!!.map { it.amount })
        assertEquals(usd(10_002), draft.totalAmount)
        assertTrue(draft.canSend)
    }

    // 51
    @Test
    fun `nobody selected has no shares and cannot be sent`() {
        val draft = draft(5).copy(selected = emptySet())

        assertNull(draft.split)
        assertFalse(draft.canSend)
        assertNull(draft.toSplit(ChatId(byteArrayOf(1)), sealed(), now))
    }

    @Test
    fun `a single other without the creator is one share of the amount`() {
        val draft = draft(5).copy(selected = setOf(id(2)))

        assertEquals(listOf(NewShare(id(2), twenty)), draft.split)
        assertEquals(twenty, draft.totalAmount)
    }

    @Test
    fun `presets and the custom range`() {
        assertEquals(now + 7.days, Deadline.Week.endsAt(now))
        assertEquals(now + 1.days, Deadline.Preset.Day.endsAt(now))
        assertEquals(now + 3.days, Deadline.Preset.ThreeDays.endsAt(now))
        assertEquals(now + 14.days, Deadline.Preset.TwoWeeks.endsAt(now))
        assertEquals((now + 1.hours)..(now + 28.days - 5.minutes), Deadline.customRange(now))
        assertEquals(now + 2.days, Deadline.Custom(now + 2.days).endsAt(now + 1.days))
    }

    // 61
    @Test
    fun `the name field opens empty over the weekday default, and back keeps it`() {
        val draft = draft(5).copy(title = "Monday Request", defaultTitle = "Monday Request")

        assertEquals("", draft.nameFieldText)
        assertEquals("Monday Request", draft.defaultTitle)
        // Done is disabled while the field is empty: an empty or blank entry saves nothing.
        assertNull(draft.renamed("", RequestMoneyViewModel.MAX_TITLE))
        assertNull(draft.renamed("   ", RequestMoneyViewModel.MAX_TITLE))
        // Back without Done never renames, so the title stays the default.
        assertEquals("Monday Request", draft.title)
        assertTrue(draft.canSend)
    }

    // 62
    @Test
    fun `a saved title pre-fills the name field`() {
        val draft = draft(5).copy(title = "Monday Request", defaultTitle = "Monday Request")
        val renamed = draft.renamed("  Rent ", RequestMoneyViewModel.MAX_TITLE)!!

        assertEquals("Rent", renamed.title)
        assertEquals("Rent", renamed.nameFieldText)
        assertEquals("Monday Request", renamed.defaultTitle)
    }

    @Test
    fun `saving the default text itself still pre-fills it next time`() {
        val renamed = draft(5).renamed("Saturday Request", RequestMoneyViewModel.MAX_TITLE)!!

        assertEquals("Saturday Request", renamed.nameFieldText)
    }

    @Test
    fun `a saved title is cut to the limit`() {
        assertEquals("a".repeat(RequestMoneyViewModel.MAX_TITLE), draft(5).renamed("a".repeat(RequestMoneyViewModel.MAX_TITLE + 5), RequestMoneyViewModel.MAX_TITLE)!!.title)
    }

    private fun sealed() = PaymentRequestText.Encrypted(1, ByteArray(24), ByteArray(32))
}
