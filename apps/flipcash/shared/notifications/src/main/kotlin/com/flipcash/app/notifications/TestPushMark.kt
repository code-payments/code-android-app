package com.flipcash.app.notifications

import com.flipcash.services.models.NotificationPayload
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Whether this phone is waiting on its own test push, so [NotificationService] can post it on
 * [NotificationChannels.CHANNEL_TEST_PUSH] and it pops up as a heads-up banner.
 *
 * The test push carries no payload, so nothing in it says it is a test. The notification status
 * page marks one as in flight just before it asks the server to send it, since the push can arrive
 * before the response. The mark clears when the push arrives, when the send fails, or [Lifetime]
 * after it was set. In memory only: a test outlives neither the process nor the page.
 */
@Singleton
class TestPushMark(private val timeSource: TimeSource.WithComparableMarks) {

    @Inject
    constructor() : this(TimeSource.Monotonic)

    private var expiresAt: ComparableTimeMark? = null

    val isMarked: Boolean
        @Synchronized get() = expiresAt?.hasNotPassedNow() == true

    @Synchronized
    fun mark() {
        expiresAt = timeSource.markNow() + Lifetime
    }

    @Synchronized
    fun clear() {
        expiresAt = null
    }

    /**
     * True if a push with [payload] is the test this phone is waiting on: its payload is absent or
     * empty, and the mark is set. Clears the mark when it is.
     */
    @Synchronized
    fun consume(payload: NotificationPayload?): Boolean {
        if (!payload.isEmpty() || !isMarked) return false
        expiresAt = null
        return true
    }

    companion object {
        val Lifetime = 30.seconds

        // An empty `Payload` encodes to no bytes and arrives as no payload; this also covers one
        // that decodes to all defaults.
        private fun NotificationPayload?.isEmpty(): Boolean =
            this == null || this == NotificationPayload(navigation = null)
    }
}
