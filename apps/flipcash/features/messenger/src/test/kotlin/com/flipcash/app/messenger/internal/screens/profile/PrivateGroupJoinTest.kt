package com.flipcash.app.messenger.internal.screens.profile

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.flipcash.shared.chat.RequestAge

class PrivateGroupJoinTest {

    private val at = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun `public group has no join action`() {
        assertNull(PrivateGroupJoin.resolve(isPrivate = false, isMember = false, requestedAt = null))
    }

    @Test
    fun `member of a private group has no join action`() {
        assertNull(PrivateGroupJoin.resolve(isPrivate = true, isMember = true, requestedAt = at))
    }

    @Test
    fun `non-member without a request can ask`() {
        assertEquals(PrivateGroupJoin.Request, PrivateGroupJoin.resolve(isPrivate = true, isMember = false, requestedAt = null))
    }

    @Test
    fun `unresolved membership is treated as not a member`() {
        assertEquals(PrivateGroupJoin.Request, PrivateGroupJoin.resolve(isPrivate = true, isMember = null, requestedAt = null))
    }

    @Test
    fun `non-member with a request is waiting`() {
        assertEquals(PrivateGroupJoin.Requested(at), PrivateGroupJoin.resolve(isPrivate = true, isMember = false, requestedAt = at))
    }

    @Test
    fun `request age buckets`() {
        assertEquals(RequestAge.JustNow, RequestAge.of(at, at + 59.seconds))
        assertEquals(RequestAge.Minutes(1), RequestAge.of(at, at + 1.minutes))
        assertEquals(RequestAge.Minutes(59), RequestAge.of(at, at + 59.minutes + 59.seconds))
        assertEquals(RequestAge.Hours(2), RequestAge.of(at, at + 2.hours + 30.minutes))
        assertEquals(RequestAge.Days(3), RequestAge.of(at, at + 3.days + 5.hours))
    }

    @Test
    fun `a clock behind the server reads as just now`() {
        assertEquals(RequestAge.JustNow, RequestAge.of(at, at - 5.minutes))
    }
}
