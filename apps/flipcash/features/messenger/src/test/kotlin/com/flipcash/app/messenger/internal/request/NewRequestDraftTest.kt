package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.request.NewRequestDraft.Deadline
import com.flipcash.app.messenger.internal.request.NewRequestDraft.People
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
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
    private val twenty = Fiat(20.0, CurrencyCode.USD)
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun id(n: Int): ID = listOf((n shr 8).toByte(), n.toByte())

    private fun draft(memberCount: Int, rosterViewerAt: Int = 3) = run {
        // The viewer sits mid-roster to show the creator still goes first.
        val others = (1 until memberCount).map(::id)
        val roster = others.toMutableList().apply { add(rosterViewerAt.coerceAtMost(size), viewer) }
            .map { ChatMember(it, mockk<UserProfile>(relaxed = true), emptyList()) }
        NewRequestDraft(
            viewerId = viewer,
            members = NewRequestDraft.members(viewer, roster),
            title = "Saturday Request",
            perPerson = twenty,
        )
    }

    // 14
    @Test
    fun `everyone selected asks 25 and sends 26 shares creator first`() {
        val draft = draft(26)

        assertEquals(People.EveryoneIncludingYou, draft.people)
        assertTrue(draft.people.markedPaid)
        assertEquals(25, draft.requestedCount)
        assertEquals(26, draft.shares.size)
        assertEquals(viewer, draft.shares.first())
        assertEquals((1 until 26).map(::id), draft.shares.drop(1))
    }

    // 15
    @Test
    fun `creator and one other reads one person including you`() {
        val draft = draft(26).copy(selected = setOf(viewer, id(4)))

        assertEquals(People.SomeIncludingYou(1), draft.people)
        assertEquals(1, draft.requestedCount)
        assertEquals(listOf(viewer, id(4)), draft.shares)
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
        assertEquals(twenty, split.perPerson)
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
