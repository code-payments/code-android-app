package com.flipcash.analytics.events

import com.flipcash.analytics.event

/** The gallery scan path, and the tip card. */
object ScanEvents {
    fun galleryImagePicked() = event("Gallery Scan: Image Picked")

    /** @param tier which rung of the crop ladder decoded, 1 to 3. */
    // DRIFT: iOS sends this as Gallery Scan: Code Found, with Type {Kik, QR}, Tier as
    // {wholeImage, quadrant, window}, Zoom, and Elapsed in seconds instead of Time in ms.
    fun gallerySucceeded(tier: Int, zoom: Double, timeMillis: Long) = event("Gallery Scan: Succeeded") {
        number("Tier", tier.toDouble())
        number("Zoom", zoom)
        number("Time", timeMillis)
    }

    /** @param exhausted true when the budget ran out rather than the ladder ending. */
    // DRIFT: iOS sends this as Gallery Scan: Nothing Found, with State {Exhausted, Cancelled,
    // Route Refused} and Elapsed in seconds instead of Time in ms and Exhausted.
    fun galleryFailed(timeMillis: Long, exhausted: Boolean) = event("Gallery Scan: Failed") {
        number("Time", timeMillis)
        flag("Exhausted", exhausted)
    }

    fun tipCardScanned() = event("Tip Card Scanned")

    fun tipCardPresented() = event("Tip Card Presented")
}
