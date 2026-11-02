package com.flipcash.services.models.paymentrequest

import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * A payment request: `paymentrequest.v1.PaymentRequestMetadata`, of the one kind the app knows, a
 * split in a group chat.
 *
 * [status] is as the server last sent it. Read [resolvedStatus] and [resolvedClosedAt] instead: an
 * OPEN request past its [endsAt] has expired by the clock, which the server never announces.
 *
 * @param version advances by one on every change; the store keeps the greater (see [newerOf]).
 */
data class PaymentRequest(
    val id: ID,
    val creator: ID,
    val createdAt: Instant,
    val status: PaymentRequestStatus,
    val closedAt: Instant?,
    val version: Long,
    val kind: Kind,
) {
    /** Where the request was made and what it asks. An arm the app does not know is [Unsupported]. */
    sealed interface Kind {
        data class GroupSplit(val chatId: ChatId, val split: Split) : Kind
        data object Unsupported : Kind
    }

    val split: Split? get() = (kind as? Kind.GroupSplit)?.split
    val chatId: ChatId? get() = (kind as? Kind.GroupSplit)?.chatId

    fun resolvedStatus(now: Instant): PaymentRequestStatus {
        val endsAt = split?.endsAt
        return if (status == PaymentRequestStatus.Open && endsAt != null && endsAt <= now) {
            PaymentRequestStatus.Expired
        } else {
            status
        }
    }

    fun resolvedClosedAt(now: Instant): Instant? =
        if (status == PaymentRequestStatus.Open && resolvedStatus(now) == PaymentRequestStatus.Expired) split?.endsAt
        else closedAt

    fun isOpen(now: Instant): Boolean = resolvedStatus(now) == PaymentRequestStatus.Open

    /** Whether the deck still shows it: a split, open or ended less than 24 hours ago. */
    fun isInDeck(now: Instant): Boolean {
        if (split == null) return false
        if (isOpen(now)) return true
        val closed = resolvedClosedAt(now) ?: return false
        return now - closed < ENDED_VISIBLE_FOR
    }

    companion object {
        val ENDED_VISIBLE_FOR = 24.hours
    }
}

enum class PaymentRequestStatus { Open, Completed, Closed, Expired, Unknown }

/**
 * Named members each owe the creator [Share.amount], all in [currency].
 *
 * Sums are in [Fiat.quarks] so "$20 paid, $520 requested" is exact rather than a sum of doubles.
 */
data class Split(
    val text: PaymentRequestText,
    val currency: CurrencyCode,
    val endsAt: Instant,
    val shares: List<Share>,
    // The sealed form [text] was opened from, so persistence stores the text as the server sent it
    // (see [encodePaymentRequest]). Null when the text was never sealed or is still sealed.
    val sealedText: PaymentRequestText.Encrypted? = null,
) {
    val requested: Fiat get() = Fiat(quarks = shares.sumOf { it.amount.quarks }, currencyCode = currency)
    val paid: Fiat get() = Fiat(quarks = shares.filter { it.isPaid }.sumOf { it.amount.quarks }, currencyCode = currency)

    /** Paid over requested, the creator's share on both sides; 0 for a split with nothing asked. */
    val paidFraction: Float
        get() {
            val total = requested.quarks
            return if (total <= 0L) 0f else paid.quarks.toFloat() / total
        }

    fun shareOf(userId: ID): Share? = shares.firstOrNull { it.userId == userId }
}

data class Share(
    val userId: ID,
    val amount: Fiat,
    val paid: PaidShare?,
) {
    val isPaid: Boolean get() = paid != null
}

/** How a share was paid. Once paid it stays paid. */
data class PaidShare(val paidAt: Instant, val how: How) {
    enum class How { Payment, Manual, Creator, Unknown }
}

/**
 * The request's title and description. A private group's arrives [Encrypted] and is opened once,
 * on its way into the store, into [Plain] or [Undecryptable].
 */
sealed interface PaymentRequestText {
    data class Plain(val title: String, val description: String) : PaymentRequestText

    class Encrypted(val scheme: Int, val nonce: ByteArray, val ciphertext: ByteArray) : PaymentRequestText

    /** The text failed to open, or its scheme is unknown. The request renders as unsupported. */
    data object Undecryptable : PaymentRequestText

    companion object {
        /** `EncryptedPaymentRequestText.Scheme.CHAT_KEY_XCHACHA20POLY1305`. */
        const val SCHEME_CHAT_KEY_XCHACHA20POLY1305 = 1
    }
}

/** Builds a split's share amount from the wire's `native_amount`, rounded to the nearest quark. */
fun shareAmount(nativeAmount: Double, currency: CurrencyCode): Fiat =
    Fiat(quarks = Math.round(nativeAmount * Fiat.MULTIPLIER), currencyCode = currency)

/**
 * The copy to keep when [incoming] arrives for a request [held] already: the greater version, and
 * at an equal version EXPIRED over OPEN, since expiry moves no version.
 */
fun newerOf(held: PaymentRequest?, incoming: PaymentRequest): PaymentRequest = when {
    held == null -> incoming
    incoming.version > held.version -> incoming
    incoming.version < held.version -> held
    held.status == PaymentRequestStatus.Expired && incoming.status == PaymentRequestStatus.Open -> held
    // A held copy that failed to decrypt yields to one that opened, at the same version.
    else -> incoming
}

/**
 * What a creator sends to open a split in a private group. [text] is already sealed under the
 * group's chat key; [shares] lists who is asked and for how much, in roster order, the creator
 * first when included.
 */
data class NewSplitRequest(
    val chatId: ChatId,
    val text: PaymentRequestText.Encrypted,
    val currency: CurrencyCode,
    val endsAt: Instant,
    val shares: List<NewShare>,
)

/** One member's part of a [NewSplitRequest]: a whole number, at least one, of the currency's smallest unit. */
data class NewShare(val userId: ID, val amount: Fiat)

/**
 * The intent id a viewer's share payment must use. Anyone who learns it before the share is paid
 * can use it up, so it never prints: keep it in memory for the one payment and never log it.
 */
class PaymentIntentId(val bytes: ByteArray) {
    override fun toString(): String = "PaymentIntentId(redacted)"
}

/**
 * What a request's status line says: the time left to pay, rounded up, or how it ended.
 *
 * The bands are uneven on purpose: [HoursLeft] runs from 1 hour to 24 hours inclusive, so
 * [DaysLeft] is never 1 and a 1-week request reads 7 days.
 */
sealed interface StatusLine {
    data class DaysLeft(val days: Int) : StatusLine
    data class HoursLeft(val hours: Int) : StatusLine
    data class MinutesLeft(val minutes: Int) : StatusLine
    data object PaidInFull : StatusLine
    data object Closed : StatusLine
    data object Expired : StatusLine
}

fun PaymentRequest.statusLine(now: Instant): StatusLine = when (resolvedStatus(now)) {
    PaymentRequestStatus.Completed -> StatusLine.PaidInFull
    PaymentRequestStatus.Closed -> StatusLine.Closed
    PaymentRequestStatus.Expired -> StatusLine.Expired
    PaymentRequestStatus.Open, PaymentRequestStatus.Unknown -> {
        val left = (split?.endsAt ?: now) - now
        val minute = 60_000L
        val hour = 60 * minute
        val ms = left.inWholeMilliseconds
        fun ceilDiv(a: Long, b: Long) = ((a + b - 1) / b).toInt()
        when {
            ms > 24 * hour -> StatusLine.DaysLeft(ceilDiv(ms, 24 * hour))
            ms >= hour -> StatusLine.HoursLeft(ceilDiv(ms, hour))
            else -> StatusLine.MinutesLeft(maxOf(1, ceilDiv(ms, minute)))
        }
    }
}

/**
 * The viewer's share to pay on this request now: unpaid, on an open request, or null. The creator
 * owes nothing, even on a share of their own that reads unpaid.
 */
fun PaymentRequest.unpaidShareOf(viewerId: ID?, now: Instant): Share? =
    if (viewerId == null || viewerId == creator || !isOpen(now)) null
    else split?.shareOf(viewerId)?.takeIf { !it.isPaid }

/** Where the viewer stands on a request, shown on every deck card. */
sealed interface ViewerStanding {
    data class Paid(val share: Fiat) : ViewerStanding
    data class Owes(val share: Fiat) : ViewerStanding
    data object Creator : ViewerStanding
    data object NoShare : ViewerStanding
}

/**
 * [viewerId]'s standing: the creator first, then a paid share (however it was paid), then a share
 * still owed on an open request. An unpaid share on an ended request owes nothing.
 */
fun PaymentRequest.viewerStanding(viewerId: ID?, now: Instant): ViewerStanding {
    if (viewerId == null) return ViewerStanding.NoShare
    if (viewerId == creator) return ViewerStanding.Creator
    val share = split?.shareOf(viewerId) ?: return ViewerStanding.NoShare
    return when {
        share.isPaid -> ViewerStanding.Paid(share.amount)
        isOpen(now) -> ViewerStanding.Owes(share.amount)
        else -> ViewerStanding.NoShare
    }
}

/**
 * The app-bar deck's default order: open requests [viewerId] owes on, then other open requests,
 * each by deadline, nearest first, then requests that ended within the last 24 hours, most
 * recently ended first. Anything else is left out.
 */
fun deckOrder(requests: Collection<PaymentRequest>, now: Instant, viewerId: ID? = null): List<PaymentRequest> {
    val inDeck = requests.filter { it.isInDeck(now) }
    val (open, ended) = inDeck.partition { it.isOpen(now) }
    val (owed, other) = open.partition { it.unpaidShareOf(viewerId, now) != null }
    return owed.sortedBy { it.split?.endsAt } +
        other.sortedBy { it.split?.endsAt } +
        ended.sortedByDescending { it.resolvedClosedAt(now) }
}
