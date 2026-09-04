package com.flipcash.shared.transactionhistory

import com.getcode.util.resources.ResourceHelper

/**
 * The one line under the details heading — the other side of the movement, for the kinds whose
 * heading doesn't already carry it.
 *
 * A person-to-person entry has no line here: the person *is* the heading
 * ([TransactionDetails.heading]), and a name repeated under itself says nothing. The same goes for
 * a cash link, which its heading already names, and for a withdrawal or deposit, whose other side
 * is an account address — a value to check rather than a phrase to read, so it goes to
 * [TransactionAccount] and the card.
 *
 * What is left is the kinds whose other side isn't a face and isn't in the heading: a buy was paid
 * *with* something, a sell came back *for* something, a convert has a destination mint, a pool
 * payment has a pool.
 *
 * Collected here rather than inline in the mapper so the screen's fixtures and the mapper agree on
 * what that line says for each [TransactionKind].
 */
object TransactionSubtitles {

    /**
     * [TransactionKind.GaveCash] / [TransactionKind.ReceivedCash] — a bill handed over face to
     * face. The metadata carries neither a user id nor a phone number because the hand-off never
     * exchanges identities, so "who" is genuinely unanswerable; how it moved is the useful fact.
     */
    fun inPerson(resources: ResourceHelper): String =
        resources.getString(R.string.label_txnDetails_inPerson)

    /** [TransactionKind.Buy] — the mint that was spent, which the amount alone never says. */
    fun paidWith(resources: ResourceHelper, tokenName: String): String =
        resources.getString(R.string.label_txnDetails_paidWith, tokenName)

    /** [TransactionKind.Sell] — the mint that came back. */
    fun soldFor(resources: ResourceHelper, tokenName: String): String =
        resources.getString(R.string.label_txnDetails_soldFor, tokenName)

    /**
     * [TransactionKind.Convert] — both mints, in the same "from → to" shape the activity row's
     * convert title already uses, so the row and the screen it opens read alike.
     */
    fun converted(resources: ResourceHelper, from: String, to: String): String =
        resources.getString(R.string.title_activity_convert, from, to)

    /** [TransactionKind.PoolPayment] — the pool that was paid. */
    fun pool(name: String): String? = name.takeIf { it.isNotBlank() }
}
