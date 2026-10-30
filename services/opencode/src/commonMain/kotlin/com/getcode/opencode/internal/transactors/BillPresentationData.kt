package com.getcode.opencode.internal.transactors

/**
 * Bundles the scannable code data with the nonce used to derive it.
 *
 * Keeping these paired ensures the same nonce is reused when a bill is
 * re-presented (e.g. after a cancelled share-sheet), preserving the
 * original rendezvous key so the sender's messaging stream stays valid.
 */
data class BillPresentationData(
    val data: List<Byte>,
    val nonce: List<Byte>,
)
