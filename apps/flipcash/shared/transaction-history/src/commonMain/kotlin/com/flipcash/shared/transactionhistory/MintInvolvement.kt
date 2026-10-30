package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.SwappedCryptoMetadata
import com.getcode.solana.keys.Mint

/**
 * Whether this entry belongs on [mint]'s activity history.
 *
 * Usually that is just the entry's own mint. A convert is the exception: it moves value out of one
 * token and into another, so it shows on both. Its amount carries the source mint; the destination
 * lives in the swap metadata.
 *
 * A withdrawal can also carry swap metadata, but there the destination leaves the app, so it counts
 * only against the source. Kept in sync with `MessageDao.observeRecentForMint`, which applies the
 * same rule in SQL for the non-paged previews.
 *
 * Compared by bytes: `toMint` is a plain `PublicKey` while the filter is a [Mint], and
 * `KeyType.equals` is javaClass-sensitive, so the two never compare equal as objects.
 */
internal fun ActivityFeedMessage.involves(mint: Mint): Boolean =
    amount?.mint == mint || convertOf(metadata)?.toMint?.bytes == mint.bytes

/** The swap of a *convert*, specifically — not any entry that happens to carry swap metadata. */
internal fun convertOf(meta: MessageMetadata?): SwappedCryptoMetadata? =
    (meta as? MessageMetadata.SwappedCrypto)?.swap
