package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.services.models.UserProfile
import com.getcode.opencode.model.core.ID

/**
 * One activity entry, resolved for its details screen: what to draw, plus what the screen's own
 * actions act on.
 *
 * [details] is everything the UI reads and nothing more. The rest is deliberately kept out of it:
 * cancelling a cash link needs the gift-card vault, which lives in the entry's metadata, and opening
 * the conversation needs the counterparty the screen just drew. Both travel with the state they were
 * resolved alongside, so the screen can never act on a different entry than the one it is showing.
 *
 * @param counterpartyId The other party's id, taken from the metadata rather than from
 * [counterparty] — a profile carries its own id only when it was fetched, and this is the id the
 * conversation is opened with.
 */
data class ResolvedTransaction(
    val details: TransactionDetails,
    val message: ActivityFeedMessage,
    val counterpartyId: ID?,
    val counterparty: UserProfile?,
)
