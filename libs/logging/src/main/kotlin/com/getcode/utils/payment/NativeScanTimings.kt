package com.getcode.utils.payment

import java.util.concurrent.atomic.AtomicReference

/**
 * A one-slot, thread-safe holder for the most recent native Kik-scan measurement.
 *
 * The scan runs on the camera-analysis thread before the payload (and thus the rendezvous) is
 * known, so we cannot attach it to a [PaymentTrace] at scan time. The scanner path [record]s the
 * latest sample here; the session delegate [consumeLatest]s it right after decoding the payload
 * and attaches it to the freshly-opened grab trace. Single-shot: consuming clears the slot so a
 * stale sample can't be attributed to a later, unrelated scan.
 */
object NativeScanTimings {

    data class Sample(
        val durationMs: Long,
        val hit: Boolean,
        val width: Int,
        val height: Int,
        val quality: Int,
    )

    private val latest = AtomicReference<Sample?>(null)

    fun record(sample: Sample) {
        latest.set(sample)
    }

    /** Returns and clears the latest sample, or null if none pending. */
    fun consumeLatest(): Sample? = latest.getAndSet(null)
}
