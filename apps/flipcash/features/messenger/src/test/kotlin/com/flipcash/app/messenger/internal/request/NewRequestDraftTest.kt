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

    private fun draft(memberCount: Int, rosterViewerAt: Int = 3, total: Fiat = usd(52_000)) = run {
        // The viewer sits mid-roster to show the creator still goes first.
        val others = (1 until memberCount).map(::id)
        val roster = others.toMutableList().apply { add(rosterViewerAt.coerceAtMost(size), viewer) }
            .map { ChatMember(it, mockk<UserProfile>(relaxed = true), emptyList()) }
        NewRequestDraft(
            viewerId = viewer,
            members = NewRequestDraft.members(viewer, roster),
            title = "Saturday Request",
            total = total,
        )
    }

    // 14
    @Test
    fun `520 between everyone asks 25 and sends 26 shares of 20 creator first`() {
        val draft = draft(26)

        assertEquals(People.EveryoneIncludingYou, draft.people)
        assertTrue(draft.people.markedPaid)
        assertEquals(25, draft.requestedCount)
        assertEquals(26, draft.shares.size)
        assertEquals(viewer, draft.shares.first())
        assertEquals((1 until 26).map(::id), draft.shares.drop(1))
        assertEquals(draft.shares, draft.split!!.map { it.userId })
        assertEquals(List(26) { twenty }, draft.split!!.map { it.amount })
        assertEquals(twenty, draft.largestShare)
    }

    // 15
    @Test
    fun `40 between the creator and one other reads one person including you`() {
        val draft = draft(26, total = usd(4_000)).copy(selected = setOf(viewer, id(4)))

        assertEquals(People.SomeIncludingYou(1), draft.people)
        assertEquals(1, draft.requestedCount)
        assertEquals(listOf(viewer, id(4)), draft.shares)
        assertEquals(listOf(twenty, twenty), draft.split!!.map { it.amount })
        assertEquals(twenty, draft.largestShare)
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
    fun `100 between the creator and two others puts the extra cent on the first other`() {
        val draft = draft(5, total = usd(10_000)).copy(selected = setOf(viewer, id(1), id(2)))

        assertEquals(listOf(viewer, id(1), id(2)), draft.split!!.map { it.userId })
        assertEquals(listOf(usd(3_333), usd(3_334), usd(3_333)), draft.split!!.map { it.amount })
        assertEquals(usd(3_334), draft.largestShare)
        assertEquals(usd(3_333), draft.smallestShare)
        assertTrue(draft.isUnevenSplit)
        assertTrue(draft.canSend)
    }

    // 53
    @Test
    fun `100_02 between the creator and two others is three even shares`() {
        val draft = draft(5, total = usd(10_002)).copy(selected = setOf(viewer, id(1), id(2)))

        assertEquals(listOf(usd(3_334), usd(3_334), usd(3_334)), draft.split!!.map { it.amount })
        assertEquals(usd(3_334), draft.smallestShare)
        assertFalse(draft.isUnevenSplit)
    }

    // 50
    @Test
    fun `100_02 between three others without the creator is three even shares`() {
        val draft = draft(5, total = usd(10_002)).copy(selected = setOf(id(1), id(2), id(3)))

        val amounts = draft.split!!.map { it.amount }
        assertEquals(listOf(usd(3_334), usd(3_334), usd(3_334)), amounts)
        assertEquals(10_002L * 10_000, amounts.sumOf { it.quarks })
    }

    // 51
    @Test
    fun `2 cents between three people cannot be sent`() {
        val draft = draft(5, total = usd(2)).copy(selected = setOf(viewer, id(1), id(2)))

        assertNull(draft.split)
        assertFalse(draft.canSend)
        assertNull(draft.toSplit(ChatId(byteArrayOf(1)), sealed(), now))
    }

    // 52
    @Test
    fun `40 to one other without the creator is a single share of 40`() {
        val draft = draft(5, total = usd(4_000)).copy(selected = setOf(id(2)))

        assertEquals(listOf(NewShare(id(2), usd(4_000))), draft.split)
        assertEquals(usd(4_000), draft.largestShare)
    }

    @Test
    fun `the remainder skips the creator wherever the creator sits`() {
        // 5 cents over 3 shares leaves 2 over: both go to the others, never the creator.
        val shares = listOf(id(1), viewer, id(2))

        val split = splitTotal(usd(5), shares, viewer)!!

        assertEquals(listOf(usd(2), usd(1), usd(2)), split.map { it.amount })
    }

    @Test
    fun `a total typed as a double still splits to the cent`() {
        // 100.02 * 1e6 lands just under a whole quark.
        val split = splitTotal(Fiat(100.02, CurrencyCode.USD), listOf(id(1), id(2), id(3)), viewer)!!

        assertEquals(List(3) { usd(3_334) }, split.map { it.amount })
    }

    @Test
    fun `a currency with no minor unit splits in whole units`() {
        val yen = Fiat(quarks = 1_000L * 1_000_000, currencyCode = CurrencyCode.JPY)

        val split = splitTotal(yen, listOf(viewer, id(1), id(2)), viewer)!!

        assertEquals(listOf(333L, 334L, 333L), split.map { it.amount.quarks / 1_000_000 })
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

    private fun sealed() = PaymentRequestText.Encrypted(1, ByteArray(24), ByteArray(32))
}
