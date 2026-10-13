package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import com.flipcash.services.models.chat.ChatMember
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class PrivateGroupMembersTest {

    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun member(id: Byte, joinedAgo: kotlin.time.Duration?) = ChatMember(
        userId = listOf(id),
        userProfile = mockk(relaxed = true),
        pointers = emptyList(),
        joinedAt = joinedAgo?.let { now - it },
    )

    @Test
    fun `creator is pinned first, then newest joins, unknown join time last`() {
        val creator = member(1, 30.days)
        val old = member(2, 10.days)
        val unknown = member(3, null)
        val recent = member(4, 2.hours)

        val ordered = privateGroupMembers(listOf(old, unknown, creator, recent), creator = listOf(1))

        assertEquals(listOf<Byte>(1, 4, 2, 3), ordered.map { it.userId.single() })
    }

    @Test
    fun `no creator known keeps newest first`() {
        val a = member(1, 3.days)
        val b = member(2, 1.hours)

        assertEquals(listOf(b, a), privateGroupMembers(listOf(a, b), creator = null))
    }

    @Test
    fun `join age buckets match the members list wording`() {
        fun age(ago: kotlin.time.Duration) = JoinAge.of(now - ago, now)

        assertEquals(JoinAge.JustNow, age(30.seconds))
        assertEquals(JoinAge.Minutes(5), age(5.minutes))
        assertEquals(JoinAge.Hours(2), age(2.hours))
        assertEquals(JoinAge.Yesterday, age(30.hours))
        assertEquals(JoinAge.Days(3), age(3.days))
        assertEquals(JoinAge.LastWeek, age(8.days))
        assertEquals(JoinAge.Weeks(3), age(21.days))
        assertEquals(JoinAge.LastMonth, age(40.days))
        assertEquals(JoinAge.Months(3), age(95.days))
    }

    @Test
    fun `a join time ahead of the clock reads just now`() {
        assertEquals(JoinAge.JustNow, JoinAge.of(now + 1.minutes, now))
    }

    @Test
    fun `encryption shows a closed lock only for a private group`() {
        assertEquals(Icons.Filled.Lock, GroupProfileMenuAction.Encryption.icon(isPrivate = true))
        assertEquals(Icons.Outlined.LockOpen, GroupProfileMenuAction.Encryption.icon(isPrivate = false))
    }
}
