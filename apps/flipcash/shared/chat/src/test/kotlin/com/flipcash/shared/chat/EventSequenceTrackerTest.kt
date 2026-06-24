package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventSequenceTrackerTest {

    private val chatId = ChatId("aabbccdd")

    @Test
    fun `in-order sequences advance contiguous frontier`() {
        val tracker = EventSequenceTracker()

        val r1 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1))
        assertEquals(1, r1.newContiguousSequence)
        assertFalse(r1.hasGap)

        val r2 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(2))
        assertEquals(2, r2.newContiguousSequence)
        assertFalse(r2.hasGap)

        val r3 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(3))
        assertEquals(3, r3.newContiguousSequence)
        assertFalse(r3.hasGap)
    }

    @Test
    fun `batch of in-order sequences advances in one call`() {
        val tracker = EventSequenceTracker()
        val result = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 2, 3))
        assertEquals(3, result.newContiguousSequence)
        assertFalse(result.hasGap)
    }

    @Test
    fun `gap is detected when sequence is skipped`() {
        val tracker = EventSequenceTracker()

        // Receive seq 1 (ok), then seq 3 (skipping 2)
        val r1 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1))
        assertEquals(1, r1.newContiguousSequence)
        assertFalse(r1.hasGap)

        val r2 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(3))
        assertEquals(1, r2.newContiguousSequence) // stuck at 1
        assertTrue(r2.hasGap)
        assertTrue(tracker.hasGap(chatId))
    }

    @Test
    fun `late arrival fills gap and advances frontier`() {
        val tracker = EventSequenceTracker()

        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1))
        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(3)) // gap at 2

        val result = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(2))
        assertEquals(3, result.newContiguousSequence)
        assertFalse(result.hasGap)
        assertFalse(tracker.hasGap(chatId))
    }

    @Test
    fun `multiple gaps only advance to first gap`() {
        val tracker = EventSequenceTracker()

        // Receive 1, 3, 5 — gaps at 2 and 4
        val result = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 3, 5))
        assertEquals(1, result.newContiguousSequence)
        assertTrue(result.hasGap)

        // Fill gap at 2 — advances to 3, still gap at 4
        val r2 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(2))
        assertEquals(3, r2.newContiguousSequence)
        assertTrue(r2.hasGap)

        // Fill gap at 4 — advances to 5, no more gaps
        val r3 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(4))
        assertEquals(5, r3.newContiguousSequence)
        assertFalse(r3.hasGap)
    }

    @Test
    fun `duplicate sequences are ignored`() {
        val tracker = EventSequenceTracker()

        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 2))
        val result = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 2))
        assertEquals(2, result.newContiguousSequence)
        assertFalse(result.hasGap)
    }

    @Test
    fun `dbCursor initializes the frontier`() {
        val tracker = EventSequenceTracker()

        // DB already at 5, incoming 6 should be contiguous
        val result = tracker.processSequences(chatId, dbCursor = 5, incomingSequences = listOf(6))
        assertEquals(6, result.newContiguousSequence)
        assertFalse(result.hasGap)
    }

    @Test
    fun `dbCursor advancing externally catches up tracker`() {
        val tracker = EventSequenceTracker()

        // First call: gap at 2
        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 3))
        assertTrue(tracker.hasGap(chatId))

        // External delta sync advanced DB to 5
        val result = tracker.processSequences(chatId, dbCursor = 5, incomingSequences = listOf(6))
        assertEquals(6, result.newContiguousSequence)
        assertFalse(result.hasGap) // old pending (3) is below new cursor, cleared
    }

    @Test
    fun `resetTo clears pending and sets frontier`() {
        val tracker = EventSequenceTracker()

        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 3, 5))
        assertTrue(tracker.hasGap(chatId))

        tracker.resetTo(chatId, 10)
        assertFalse(tracker.hasGap(chatId))

        // Next event should continue from 10
        val result = tracker.processSequences(chatId, dbCursor = 10, incomingSequences = listOf(11))
        assertEquals(11, result.newContiguousSequence)
        assertFalse(result.hasGap)
    }

    @Test
    fun `clear removes all state for a chat`() {
        val tracker = EventSequenceTracker()

        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 3))
        assertTrue(tracker.hasGap(chatId))

        tracker.clear(chatId)
        assertFalse(tracker.hasGap(chatId))
    }

    @Test
    fun `clearAll removes state for all chats`() {
        val tracker = EventSequenceTracker()
        val chatId2 = ChatId("11223344")

        tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 3))
        tracker.processSequences(chatId2, dbCursor = 0, incomingSequences = listOf(1, 5))

        tracker.clearAll()
        assertFalse(tracker.hasGap(chatId))
        assertFalse(tracker.hasGap(chatId2))
    }

    @Test
    fun `independent chats track separately`() {
        val tracker = EventSequenceTracker()
        val chatId2 = ChatId("11223344")

        val r1 = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(1, 2, 3))
        val r2 = tracker.processSequences(chatId2, dbCursor = 5, incomingSequences = listOf(7))

        assertEquals(3, r1.newContiguousSequence)
        assertFalse(r1.hasGap)

        assertEquals(5, r2.newContiguousSequence) // stuck at 5, gap at 6
        assertTrue(r2.hasGap)
    }

    @Test
    fun `out-of-order batch is handled correctly`() {
        val tracker = EventSequenceTracker()

        // Receive [3, 1, 2] all at once — should end up contiguous at 3
        val result = tracker.processSequences(chatId, dbCursor = 0, incomingSequences = listOf(3, 1, 2))
        assertEquals(3, result.newContiguousSequence)
        assertFalse(result.hasGap)
    }

    @Test
    fun `sequences at or below cursor are ignored`() {
        val tracker = EventSequenceTracker()

        val result = tracker.processSequences(chatId, dbCursor = 5, incomingSequences = listOf(3, 4, 5))
        assertEquals(5, result.newContiguousSequence)
        assertFalse(result.hasGap)
    }
}
