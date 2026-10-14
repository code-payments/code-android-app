package com.flipcash.app.updates

/**
 * A newer store build that the user may install when they choose, through Play's flexible flow.
 * Separate from [AppUpdateController.availableUpdate], which blocks the app below the minimum
 * version.
 *
 * @param stalenessDays how long Play has known about the update on this device. Play reports null
 * until a day has passed, which counts as 0.
 */
data class OptionalUpdate(
    val versionCode: Int,
    val priority: Int,
    val stalenessDays: Int,
)

/** When the one-time "New version available" toast appears. One place, so both hosts agree. */
object UpdateToastTiming {
    /** Play priority (0–5) at or above which an update is worth interrupting for. */
    const val PRIORITY_THRESHOLD = 4

    /** Days behind at which any update is worth interrupting for. */
    const val STALENESS_DAYS = 7

    /** After the user comes back to a tab home, or opens a chat. */
    const val SETTLE_MILLIS = 1_000L

    /** Time spent on a tab home without leaving it. */
    const val DWELL_MILLIS = 3_000L

    /** In a chat the toast covers the newest messages, so it leaves sooner than on a home. */
    const val CHAT_TIMEOUT_MILLIS = 6_000L
}

/** Whether the update matters enough for the toast: marked priority, or the user has been behind a week. */
val OptionalUpdate.isWorthInterrupting: Boolean
    get() = priority >= UpdateToastTiming.PRIORITY_THRESHOLD ||
        stalenessDays >= UpdateToastTiming.STALENESS_DAYS

/**
 * Whether the toast should show for this update, given the version it last showed for. It shows
 * once per version: a tap, swipe or timeout all count as seen.
 */
fun OptionalUpdate.isToastEligible(lastShownVersionCode: Int?): Boolean =
    isWorthInterrupting && lastShownVersionCode != versionCode
