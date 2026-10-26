package com.flipcash.services.models.paymentrequest

import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The spec's shared test cases 1–13: amounts, the status line, the version merge and deck order. */
class PaymentRequestTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val chatId = ChatId(ByteArray(32) { 3 })
    private val creator: ID = List(16) { 1 }
    private lateinit var defaultLocale: Locale

    @Before
    fun pinLocale() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun member(n: Int): ID = List(16) { n.toByte() }

    private fun request(
        shares: List<Share> = listOf(share(1, paid = true), share(2)),
        status: PaymentRequestStatus = PaymentRequestStatus.Open,
        endsIn: Duration = 7.days,
        closedAt: Instant? = null,
        version: Long = 1,
        id: Int = 9,
    ) = PaymentRequest(
        id = List(16) { id.toByte() },
        creator = creator,
        createdAt = now - 1.hours,
        status = status,
        closedAt = closedAt,
        version = version,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = chatId,
            split = Split(
                text = PaymentRequestText.Plain("Dinner", ""),
                currency = CurrencyCode.USD,
                endsAt = now + endsIn,
                shares = shares,
            ),
        ),
    )

    private fun share(n: Int, amount: Int = 20, paid: Boolean = false) = Share(
        userId = member(n),
        amount = Fiat(amount, CurrencyCode.USD),
        paid = if (paid) PaidShare(now, PaidShare.How.Creator) else null,
    )

    private fun Fiat.text() = formatted(Fiat.FormattingRule.Truncated)

    @Test
    fun `1 - 26 shares of 20 with only the creator's paid`() {
        val split = request(shares = (1..26).map { share(it, paid = it == 1) }).split!!
        assertEquals("$20", split.paid.text())
        assertEquals("$520", split.requested.text())
        assertEquals(1f / 26, split.paidFraction, 1e-6f)
    }

    @Test
    fun `2 - 4 shares of 10, three paid, closed`() {
        val r = request(
            shares = (1..4).map { share(it, amount = 10, paid = it <= 3) },
            status = PaymentRequestStatus.Closed,
            closedAt = now - 1.hours,
        )
        assertEquals("$30", r.split!!.paid.text())
        assertEquals("$40", r.split!!.requested.text())
        assertEquals(StatusLine.Closed, r.statusLine(now))
        assertFalse(r.isOpen(now))
        assertFalse(r.split!!.shareOf(member(4))!!.isPaid)
    }

    @Test
    fun `3 - all shares paid`() {
        val r = request(
            shares = (1..3).map { share(it, paid = true) },
            status = PaymentRequestStatus.Completed,
            closedAt = now - 1.minutes,
        )
        assertEquals(StatusLine.PaidInFull, r.statusLine(now))
        assertEquals(1f, r.split!!.paidFraction)
        assertTrue(r.isInDeck(now))
        assertFalse(r.isOpen(now))
    }

    @Test
    fun `4 - 7 days left`() = assertEquals(StatusLine.DaysLeft(7), request(endsIn = 7.days).statusLine(now))

    @Test
    fun `5 - 6 days 1 hour left reads 7 days`() =
        assertEquals(StatusLine.DaysLeft(7), request(endsIn = 6.days + 1.hours).statusLine(now))

    @Test
    fun `6 - 23 hours 10 minutes left reads 24 hours`() =
        assertEquals(StatusLine.HoursLeft(24), request(endsIn = 23.hours + 10.minutes).statusLine(now))

    @Test
    fun `22 - exactly 24 hours left reads 24 hours, not 1 day`() =
        assertEquals(StatusLine.HoursLeft(24), request(endsIn = 24.hours).statusLine(now))

    @Test
    fun `7 - 1 hour left`() = assertEquals(StatusLine.HoursLeft(1), request(endsIn = 1.hours).statusLine(now))

    @Test
    fun `8 - 30 seconds left reads 1 minute`() =
        assertEquals(StatusLine.MinutesLeft(1), request(endsIn = 30.seconds).statusLine(now))

    @Test
    fun `9 - open past its deadline reads expired, closed at the deadline`() {
        val r = request(endsIn = (-1).seconds)
        assertEquals(PaymentRequestStatus.Expired, r.resolvedStatus(now))
        assertEquals(r.split!!.endsAt, r.resolvedClosedAt(now))
        assertEquals(StatusLine.Expired, r.statusLine(now))
        assertFalse(r.isOpen(now))
    }

    @Test
    fun `10 - an older version does not replace a newer one`() {
        val held = request(version = 5)
        assertEquals(held, newerOf(held, request(version = 4)))
    }

    @Test
    fun `11 - expired at the same version replaces open`() {
        val expired = request(version = 5, status = PaymentRequestStatus.Expired, closedAt = now)
        assertEquals(expired, newerOf(request(version = 5), expired))
    }

    @Test
    fun `12 - open at the same version does not replace expired`() {
        val expired = request(version = 5, status = PaymentRequestStatus.Expired, closedAt = now)
        assertEquals(expired, newerOf(expired, request(version = 5)))
    }

    @Test
    fun `13 - deck order`() {
        val in5d = request(id = 1, endsIn = 5.days)
        val in2d = request(id = 2, endsIn = 2.days)
        val closed3hAgo = request(id = 3, status = PaymentRequestStatus.Closed, closedAt = now - 3.hours)
        val expired30hAgo = request(id = 4, status = PaymentRequestStatus.Expired, closedAt = now - 30.hours)
        assertEquals(
            listOf(in2d, in5d, closed3hAgo),
            deckOrder(listOf(in5d, closed3hAgo, expired30hAgo, in2d), now),
        )
    }

    @Test
    fun `23 - a request the viewer owes on sorts before an earlier one they paid`() {
        val viewer = 2
        val owed5d = request(id = 1, endsIn = 5.days, shares = listOf(share(1, paid = true), share(viewer)))
        val paid2d = request(id = 2, endsIn = 2.days, shares = listOf(share(1), share(viewer, paid = true)))
        assertEquals(listOf(owed5d, paid2d), deckOrder(listOf(paid2d, owed5d), now, member(viewer)))
        assertEquals(Fiat(20, CurrencyCode.USD), owed5d.unpaidShareOf(member(viewer), now)?.amount)
    }

    @Test
    fun `24 - the creator, a payer, or an ended request owes nothing`() {
        val r = request(shares = listOf(share(1), share(2, paid = true)))
        assertNull(r.unpaidShareOf(creator, now))
        assertNull(r.unpaidShareOf(member(2), now))
        assertNull(r.unpaidShareOf(member(5), now))
        assertNull(r.unpaidShareOf(null, now))
        assertNull(request(status = PaymentRequestStatus.Expired).unpaidShareOf(member(2), now))
        assertNull(request(endsIn = (-1).minutes).unpaidShareOf(member(2), now))
    }

    @Test
    fun `a request with no split has no share for anyone`() {
        val r = request().copy(kind = PaymentRequest.Kind.Unsupported)
        assertNull(r.split)
        assertFalse(r.isInDeck(now))
    }
}
