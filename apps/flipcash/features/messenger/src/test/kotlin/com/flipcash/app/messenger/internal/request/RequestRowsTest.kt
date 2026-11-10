package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.separatorBetweenItems
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.paymentrequest.PaidShare
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestStatus
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.flipcash.services.models.paymentrequest.Share
import com.flipcash.services.models.paymentrequest.Split
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SeparatorConfig
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Where share payment rows collapse and where the marked-paid and closed rows go, and what their
 * rings show (spec "Share payment rows" and "Marked paid and closed rows", cases 64–67, 69, 70 and
 * 75–82). The copy is in `RequestRowsCopyTest`.
 */
class RequestRowsTest {

    private val chat = ChatId(ByteArray(32) { 3 })
    private val alex: ID = List(16) { 1 }
    private val b: ID = List(16) { 2 }
    private val c: ID = List(16) { 3 }
    private val d: ID = List(16) { 4 }
    private val names = mapOf(alex to "Alex", b to "Sam", c to "Jordan", d to "Riley")
    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val ten = Fiat(10.0, CurrencyCode.USD)
    private val requestId: ID = List(16) { 9 }

    private fun at(minutes: Int) = t0 + minutes.minutes

    private fun request(
        id: ID = requestId,
        status: PaymentRequestStatus = PaymentRequestStatus.Open,
        closedAt: Instant? = null,
        inChat: ChatId = chat,
        paid: Map<ID, PaidShare> = emptyMap(),
    ) = PaymentRequest(
        id = id,
        creator = alex,
        createdAt = t0,
        status = status,
        closedAt = closedAt,
        version = 1,
        kind = PaymentRequest.Kind.GroupSplit(
            chatId = inChat,
            split = Split(
                text = PaymentRequestText.Plain("Tahoe cabin", ""),
                currency = CurrencyCode.USD,
                endsAt = t0 + 240.hours,
                shares = listOf(
                    Share(alex, ten, PaidShare(t0, PaidShare.How.Creator)),
                    Share(b, ten, paid[b]),
                    Share(c, ten, paid[c]),
                    Share(d, ten, paid[d]),
                ),
            ),
        ),
    )

    private fun payment(at: Instant) = PaidShare(at, PaidShare.How.Payment)
    private fun mark(at: Instant) = PaidShare(at, PaidShare.How.Manual)

    private fun share(id: Long, payer: ID, at: Instant, request: ID = requestId, fromSelf: Boolean = false) =
        ChatListItem.SharePayment(
            messageId = id,
            contentIndex = 0,
            timestamp = at,
            isFromSelf = fromSelf,
            requestId = request,
            payerId = payer,
            payerName = names.getValue(payer),
            receiverId = alex,
            receiverName = "Alex",
            receiverIsViewer = false,
            amount = ten,
        )

    private fun text(id: Long, at: Instant) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("m$id"),
        isFromSelf = false,
        timestamp = at,
    )

    /** The share payment rows [newestFirst] draws, each as its run's message ids. */
    private fun drawnRuns(newestFirst: List<ChatListItem>): List<List<Long>> =
        newestFirst.indices.mapNotNull { i ->
            (newestFirst[i] as? ChatListItem.SharePayment)
                ?.let { sharePaymentRun(i, it, newestFirst.size) { index -> newestFirst[index] } }
                ?.map { it.messageId }
        }

    /** [messages] newest first with the notices for [requests] placed, as the paging pass places them. */
    private fun timeline(
        messages: List<ChatListItem.TranscriptMessage>,
        requests: List<PaymentRequest>,
        viewer: ID? = c,
        newestLoaded: Boolean = true,
    ): List<ChatListItem> {
        val events = requestEvents(requests, chat)
        fun notices(newer: ChatListItem.TranscriptMessage?, older: ChatListItem.TranscriptMessage?) =
            eventsBetween(newer, older, events).toNotices(
                chatId = chat,
                viewerId = viewer,
                config = SeparatorConfig.DayOnly,
                above = newer,
                nameOf = { names.getValue(it) },
                keyOf = { it.joinToString("") },
            )
        return buildList {
            if (newestLoaded) addAll(notices(null, messages.firstOrNull()))
            messages.forEachIndexed { i, message ->
                add(message)
                addAll(notices(message, messages.getOrNull(i + 1)))
            }
        }
    }

    private fun List<ChatListItem>.notices() = filterIsInstance<ChatListItem.RequestNotice>()

    // Case 64: 4 shares of $10, creator's paid at creation; B pays, then C.
    @Test
    fun `each share payment's ring is the request as of that payment`() {
        val split = request(paid = mapOf(b to payment(at(1)), c to payment(at(2)))).split!!
        assertEquals(0.5f, split.paidFractionAfterPayment(b, sentAt = at(1)))
        assertEquals(0.75f, split.paidFractionAfterPayment(c, sentAt = at(2)))
    }

    // Case 65: then the creator marks D paid.
    @Test
    fun `a later mark leaves earlier rings as they were and adds no share payment row`() {
        val request = request(paid = mapOf(b to payment(at(1)), c to payment(at(2)), d to mark(at(3))))
        val split = request.split!!
        assertEquals(0.5f, split.paidFractionAfterPayment(b, sentAt = at(1)))
        assertEquals(0.75f, split.paidFractionAfterPayment(c, sentAt = at(2)))

        val rows = timeline(listOf(share(2, c, at(2)), share(1, b, at(1)), text(0, t0)), listOf(request))
        assertEquals(2, rows.filterIsInstance<ChatListItem.SharePayment>().size)
    }

    @Test
    fun `a payment whose share the stored request does not show paid yet still counts in its own ring`() {
        val split = request().split!!
        assertEquals(0.5f, split.paidFractionAfterPayment(b, sentAt = at(1)))
    }

    // Case 66.
    @Test
    fun `three share payments in a row then a text then one more draw as two rows`() {
        val rows = listOf(
            share(5, b, at(5)),
            text(4, at(4)),
            share(3, d, at(3)),
            share(2, c, at(2)),
            share(1, b, at(1)),
        )
        assertEquals(listOf(listOf(5L), listOf(3L, 2L, 1L)), drawnRuns(rows))
    }

    @Test
    fun `share payments for different requests do not join a run`() {
        val other: ID = List(16) { 8 }
        val rows = listOf(share(2, c, at(2), request = other), share(1, b, at(1)))
        assertEquals(listOf(listOf(2L), listOf(1L)), drawnRuns(rows))
    }

    // Case 67.
    @Test
    fun `two share payments either side of midnight draw as two rows under their own dates`() {
        val newer = share(2, c, t0 + 30.hours)
        val older = share(1, b, t0)
        val date = separatorBetweenItems(newer, older, UnreadBoundary.None, SeparatorConfig.DayOnly)
        assertIs<ChatListItem.DateSeparator>(date)
        // The date header is an item between them, so neither run reaches the other.
        assertEquals(listOf(listOf(2L), listOf(1L)), drawnRuns(listOf(newer, date, older)))
    }

    @Test
    fun `a jump to a folded share payment resolves to the row that draws its run`() {
        val rows = listOf(text(4, at(4)), share(3, d, at(3)), share(2, c, at(2)), share(1, b, at(1)))
        assertEquals(1, sharePaymentRunHead(3, rows.size) { rows[it] })
        assertEquals(1, sharePaymentRunHead(1, rows.size) { rows[it] })
        assertEquals(0, sharePaymentRunHead(0, rows.size) { rows[it] })
    }

    // Case 69. Widths are in characters: 34 fit on the line.
    @Test
    fun `a long title is shortened with an ellipsis and the row stays on one line`() {
        val caption = "Jordan paid Alex \$40"
        val fit = fitRequestRow(
            names = listOf("Jordan", "Alex"),
            title = "Tahoe cabin weekend with everyone",
            canWrap = true,
            fits = { _, title -> "$caption · $title".length <= 34 },
        )
        assertEquals(false, fit.twoLines)
        assertEquals(listOf("Jordan", "Alex"), fit.names)
        assertEquals("Tahoe cabi…", fit.title)
    }

    // Case 70.
    @Test
    fun `a row that does not fit after both shortening steps goes to two lines with the full title`() {
        fun caption(names: List<String>) = "${names[0]} paid ${names[1]} \$125.00"
        val fit = fitRequestRow(
            names = listOf("Maximilian", "Alexandra"),
            title = "Tahoe cabin",
            canWrap = true,
            fits = { names, title -> "${caption(names)} · $title".length <= 34 },
            lineOneFits = { names -> caption(names).length <= 34 },
        )
        assertTrue(fit.twoLines)
        assertEquals("Tahoe cabin", fit.title)
        // Line one holds the caption alone, so the names come back to full length.
        assertEquals(listOf("Maximilian", "Alexandra"), fit.names)
    }

    // A closed row's title is an argument of its sentence; too wide, it still takes the second line.
    @Test
    fun `a closed row that does not fit after both shortening steps goes to two lines with the full title`() {
        val title = "Tahoe cabin weekend with everyone"
        val fit = fitRequestRow(
            names = listOf("Maximilian-Alexander"),
            title = title,
            canWrap = true,
            fits = { names, t -> "${names[0]} closed $t".length <= 20 },
            lineOneFits = { names -> "${names[0]} closed".length <= 20 },
        )
        assertTrue(fit.twoLines)
        assertEquals(title, fit.title)
        assertEquals(listOf("Maximilian-A…"), fit.names)
    }

    @Test
    fun `a row with no title stays on one line however wide it is`() {
        val fit = fitRequestRow(
            names = listOf("Maximilian", "Alexandra"),
            title = null,
            canWrap = true,
            fits = { _, _ -> false },
        )
        assertEquals(false, fit.twoLines)
        assertEquals(listOf("Maxim…", "Alexa…"), fit.names)
    }

    @Test
    fun `names shorten longest first and only after the title has reached its minimum`() {
        fun caption(names: List<String>) = "${names[0]} paid ${names[1]} \$40"
        val fit = fitRequestRow(
            names = listOf("Christopher", "Alex"),
            title = "Tahoe cabin",
            canWrap = true,
            fits = { names, title -> "${caption(names)} · $title".length <= 32 },
        )
        assertEquals(false, fit.twoLines)
        assertEquals("Tahoe c…", fit.title)
        assertEquals(listOf("Christ…", "Alex"), fit.names)
    }

    // Case 75: the row sits at the share's paid_at.
    @Test
    fun `a marked-paid row is placed at the share's paid_at`() {
        val request = request(paid = mapOf(b to mark(at(5))))
        val rows = timeline(listOf(text(3, at(9)), text(2, at(4)), text(1, at(1))), listOf(request))
        assertEquals(listOf(3L, null, 2L, 1L), rows.map { (it as? ChatListItem.TranscriptMessage)?.messageId })
        val notice = rows.notices().single()
        assertEquals(at(5), notice.timestamp)
        assertEquals("Tahoe cabin", notice.title)
        assertEquals(listOf("Sam"), (notice.kind as ChatListItem.RequestNotice.Kind.MarkedPaid).marked.map { it.name })
    }

    @Test
    fun `an event sharing a message's timestamp goes after that message`() {
        val request = request(paid = mapOf(b to mark(at(4))))
        val rows = timeline(listOf(text(2, at(4)), text(1, at(1))), listOf(request))
        assertIs<ChatListItem.RequestNotice>(rows[0])
        assertEquals(2L, (rows[1] as ChatListItem.TranscriptMessage).messageId)
    }

    @Test
    fun `the viewer and the creator are told apart on a marked-paid row`() {
        val request = request(paid = mapOf(b to mark(at(5))))
        val messages = listOf(text(1, at(1)))
        assertNull(timeline(messages, listOf(request), viewer = alex).notices().single().creatorName)
        val forSam = timeline(messages, listOf(request), viewer = b).notices().single()
        assertEquals("Alex", forSam.creatorName)
        assertTrue((forSam.kind as ChatListItem.RequestNotice.Kind.MarkedPaid).marked.single().isViewer)
    }

    // Case 76: 4 shares, creator's paid; B pays, then the creator marks C.
    @Test
    fun `a marked-paid row's ring is the request as of that mark`() {
        val request = request(paid = mapOf(b to payment(at(1)), c to mark(at(2))))
        assertEquals(0.5f, request.split!!.paidFractionAfterPayment(b, sentAt = at(1)))
        val notice = timeline(listOf(share(1, b, at(1))), listOf(request)).notices().single()
        assertEquals(0.75f, (notice.kind as ChatListItem.RequestNotice.Kind.MarkedPaid).progress)
    }

    // Case 77: the share now reads `Payment`, so its mark is gone.
    @Test
    fun `a mark replaced by the member's own payment leaves no marked-paid row`() {
        val marked = request(paid = mapOf(b to mark(at(5))))
        assertEquals(1, timeline(listOf(text(1, at(1))), listOf(marked)).notices().size)

        val replaced = request(paid = mapOf(b to payment(at(7))))
        val rows = timeline(listOf(share(2, b, at(7)), text(1, at(1))), listOf(replaced))
        assertTrue(rows.notices().isEmpty())
        assertEquals(listOf(listOf(2L)), drawnRuns(rows))
    }

    // Case 78.
    @Test
    fun `a closed request has a closed row at closed_at with no ring`() {
        val request = request(status = PaymentRequestStatus.Closed, closedAt = at(6))
        val rows = timeline(listOf(text(2, at(9)), text(1, at(1))), listOf(request))
        val notice = rows.notices().single()
        assertEquals(1, rows.indexOf(notice))
        assertEquals(at(6), notice.timestamp)
        assertEquals(ChatListItem.RequestNotice.Kind.Closed, notice.kind)
        assertEquals("Alex", notice.creatorName)
        assertNull(timeline(listOf(text(1, at(1))), listOf(request), viewer = alex).notices().single().creatorName)
    }

    // Case 79.
    @Test
    fun `a completed or expired request has no closed row`() {
        val completed = request(status = PaymentRequestStatus.Completed, closedAt = at(6))
        val expiredByServer = request(status = PaymentRequestStatus.Expired, closedAt = at(6))
        // Still OPEN as the server sent it, past its end: expired by the clock.
        val expiredByClock = request()
        for (request in listOf(completed, expiredByServer, expiredByClock)) {
            assertTrue(requestEvents(listOf(request), chat).isEmpty())
        }
    }

    // Case 80.
    @Test
    fun `three marks in a row collapse into one row`() {
        val request = request(paid = mapOf(b to mark(at(3)), c to mark(at(4)), d to mark(at(5))))
        val notice = timeline(listOf(text(1, at(1))), listOf(request), viewer = null).notices().single()
        val kind = notice.kind as ChatListItem.RequestNotice.Kind.MarkedPaid
        assertEquals(listOf("Riley", "Jordan", "Sam"), kind.marked.map { it.name })
        assertEquals(at(5), notice.timestamp)
        assertEquals(1f, kind.progress)
    }

    @Test
    fun `marks with a message between them stay separate rows`() {
        val request = request(paid = mapOf(b to mark(at(3)), c to mark(at(6))))
        val rows = timeline(listOf(text(2, at(5)), text(1, at(1))), listOf(request))
        assertEquals(2, rows.notices().size)
    }

    @Test
    fun `marks either side of midnight in one gap stay separate rows`() {
        val request = request(paid = mapOf(b to mark(t0 + 1.hours), c to mark(t0 + 31.hours)))
        val rows = timeline(listOf(text(1, t0)), listOf(request))
        assertEquals(2, rows.notices().size)
        assertIs<ChatListItem.DateSeparator>(
            separatorBetweenItems(rows[0], rows[1], UnreadBoundary.None, SeparatorConfig.DayOnly),
        )
    }

    @Test
    fun `a closed row between marks splits them`() {
        val other = request(id = List(16) { 8 }, status = PaymentRequestStatus.Closed, closedAt = at(4))
        val request = request(paid = mapOf(b to mark(at(3)), c to mark(at(5))))
        val rows = timeline(listOf(text(1, at(1))), listOf(request, other))
        assertEquals(3, rows.notices().size)
    }

    // Case 81: the notice is the newest row, above the newest message, and is no message itself.
    @Test
    fun `a marked-paid row that is the newest item is not a transcript message`() {
        val request = request(paid = mapOf(b to mark(at(5))))
        val rows = timeline(listOf(text(1, at(1))), listOf(request))
        val newest = rows.first()
        assertIs<ChatListItem.RequestNotice>(newest)
        assertTrue(newest !is ChatListItem.TranscriptMessage)
        assertNull(newest.above)
    }

    @Test
    fun `nothing is placed above the newest loaded message while newer messages are unloaded`() {
        val request = request(paid = mapOf(b to mark(at(5))))
        assertTrue(timeline(listOf(text(1, at(1))), listOf(request), newestLoaded = false).notices().isEmpty())
    }

    // Case 82.
    @Test
    fun `a mark older than the oldest loaded message has no row until paging loads around it`() {
        val request = request(paid = mapOf(b to mark(at(5))))
        val loaded = listOf(text(3, at(9)), text(2, at(8)))
        assertTrue(timeline(loaded, listOf(request)).notices().isEmpty())
        assertEquals(1, timeline(loaded + text(1, at(1)), listOf(request)).notices().size)
    }

    @Test
    fun `another chat's request places no rows`() {
        val elsewhere = request(inChat = ChatId(ByteArray(32) { 7 }), paid = mapOf(b to mark(at(5))))
        assertTrue(requestEvents(listOf(elsewhere), chat).isEmpty())
    }

    @Test
    fun `the unread divider is decided for the message above a notice, with the notice's date`() {
        val request = request(paid = mapOf(b to mark(at(5))))
        val newer = text(2, at(9))
        val older = text(1, at(1))
        val rows = timeline(listOf(newer, older), listOf(request))
        val notice = rows[1] as ChatListItem.RequestNotice
        // Above the notice only a date could go; the divider sits where it would without the notice.
        assertNull(separatorBetweenItems(newer, notice, UnreadBoundary.At(1, 1), SeparatorConfig.DayOnly))
        assertIs<ChatListItem.UnreadDivider>(
            separatorBetweenItems(notice, older, UnreadBoundary.At(1, 1), SeparatorConfig.DayOnly),
        )
    }
}
