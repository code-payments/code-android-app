package com.flipcash.app.messenger.internal.pins

import com.flipcash.app.messenger.internal.request.DeckPlacement
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.Pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class ChatPinsTest {

    private val alex = listOf<Byte>(1)
    private val sentAt = Instant.fromEpochSeconds(1_000)

    private fun pin(id: Long, deleted: Boolean = false) = Pin(
        messageId = id,
        pinnedBy = alex,
        pinnedAt = sentAt,
        message = ChatMessage(
            messageId = id,
            senderId = alex,
            content = listOf(
                if (deleted) MessageContent.Deleted(sentAt, alex) else MessageContent.Text("message $id"),
            ),
            timestamp = sentAt,
            unreadSeq = 0,
            eventSequence = id,
        ),
    )

    // PinsChanged moves the stored summary; the list is read again only past the held version.
    @Test
    fun `a summary newer than the held list is stale`() {
        val held = ChatPins().read(listOf(pin(3)), readVersion = 4)

        assertTrue(held.isStale(5))
        assertFalse(held.isStale(4))
        assertFalse(held.isStale(3))
    }

    // Chat feeds leave pin_summary unset; that says nothing about pins.
    @Test
    fun `a missing summary never reads as stale or clears pins`() {
        val held = ChatPins().read(listOf(pin(3)), readVersion = 4)

        assertFalse(held.isStale(null))
        assertEquals(listOf(3L), held.visible.map { it.messageId })
    }

    @Test
    fun `before the first read any summary is stale`() {
        assertTrue(ChatPins().isStale(0))
    }

    // Two reads racing: the one made at an older version lands last and must not win.
    @Test
    fun `a read at an older version doesn't replace a newer one`() {
        val newer = ChatPins().read(listOf(pin(3), pin(2)), readVersion = 6)

        val after = newer.read(listOf(pin(2)), readVersion = 5)

        assertEquals(6L, after.version)
        assertEquals(listOf(3L, 2L), after.pins.map { it.messageId })
    }

    @Test
    fun `a read at a newer version replaces the list, emptying it included`() {
        val held = ChatPins().read(listOf(pin(3)), readVersion = 4)

        val after = held.read(emptyList(), readVersion = 5)

        assertEquals(emptyList<Pin>(), after.pins)
        assertNull(after.bar(0))
    }

    // Deleted pinned messages stay pinned server-side; the client hides them.
    @Test
    fun `the bar skips a deleted latest pin for the next`() {
        val pins = ChatPins().read(listOf(pin(5, deleted = true), pin(3), pin(1)), readVersion = 1)

        val bar = pins.bar(0) as PinBar.Showing

        assertEquals(3L, bar.pin.messageId)
        assertEquals(1, bar.position)
        assertEquals(2, bar.of)
    }

    @Test
    fun `a deleted pin still counts as pinned`() {
        val pins = ChatPins().read(listOf(pin(5, deleted = true), pin(3)), readVersion = 1)

        assertTrue(5L in pins.pinnedIds)
    }

    // A delete moves no pin version, so the list isn't read again; the stored tombstone hides it.
    @Test
    fun `a pinned message deleted after the read hides from its stored copy`() {
        val read = ChatPins().read(listOf(pin(5), pin(3)), readVersion = 1)

        val pins = read.copy(stored = mapOf(5L to pin(5, deleted = true).message))

        assertEquals(listOf(3L), pins.visible.map { it.messageId })
        assertEquals(1, (pins.bar(0) as PinBar.Showing).of)
        assertTrue(5L in pins.pinnedIds)
    }

    @Test
    fun `a later read keeps the stored copies`() {
        val pins = ChatPins()
            .read(listOf(pin(5), pin(3)), readVersion = 1)
            .copy(stored = mapOf(5L to pin(5, deleted = true).message))
            .read(listOf(pin(5), pin(3)), readVersion = 2)

        assertEquals(listOf(3L), pins.visible.map { it.messageId })
    }

    @Test
    fun `only deleted pins hide the bar`() {
        val pins = ChatPins().read(listOf(pin(5, deleted = true)), readVersion = 1)

        assertNull(pins.bar(0))
    }

    @Test
    fun `a tap steps to the next older pin and wraps to the newest`() {
        val pins = ChatPins().read(listOf(pin(5), pin(3), pin(1)), readVersion = 1)

        assertEquals(1, pins.stepAfter(0))
        assertEquals(2, pins.stepAfter(1))
        assertEquals(0, pins.stepAfter(2))
    }

    @Test
    fun `the bar waits on the list when the summary says there are pins`() {
        assertEquals(PinBar.Fetching, ChatPins(summaryCount = 2).bar(0))
        assertNull(ChatPins(summaryCount = 0).bar(0))
    }

    // C1: no requests drawn, the bar is under the top bar.
    @Test
    fun `without a request deck pins take the bar`() {
        assertEquals(PinSlot.Bar, pinSlot(hasPins = true, deckRest = null))
    }

    // C6: the strip holds the space under the top bar, so pins move to its trailing slot.
    @Test
    fun `while the strip is out pins are a top bar button`() {
        assertEquals(PinSlot.TopBarButton, pinSlot(hasPins = true, deckRest = DeckPlacement.Strip))
    }

    // C6b and C7b: scrolled or swiped into the pill, the pill has the trailing slot and the bar returns.
    @Test
    fun `once the strip is the pill pins take the bar`() {
        assertEquals(PinSlot.Bar, pinSlot(hasPins = true, deckRest = DeckPlacement.Pill))
    }

    // C7a: the pill grows into the trailing slot as the strip collapses, so the button leaves first.
    @Test
    fun `a strip partway into the pill takes the button away and holds the bar back`() {
        assertEquals(PinSlot.None, pinSlot(hasPins = true, deckRest = DeckPlacement.Strip, collapsing = true))
    }

    @Test
    fun `nothing pinned takes no slot`() {
        assertEquals(PinSlot.None, pinSlot(hasPins = false, deckRest = DeckPlacement.Strip))
        assertEquals(PinSlot.None, pinSlot(hasPins = false, deckRest = null))
    }

    @Test
    fun `the list is done when its last visible pin leaves`() {
        assertTrue(ChatPins().read(emptyList(), readVersion = 5).pinnedListDone(pinAfterUnpin = null))
        val tombstonesOnly = ChatPins().read(listOf(pin(3, deleted = true)), readVersion = 5)
        assertTrue(tombstonesOnly.pinnedListDone(pinAfterUnpin = null))
    }

    @Test
    fun `the list stays while pins remain`() {
        assertFalse(ChatPins().read(listOf(pin(3)), readVersion = 5).pinnedListDone(pinAfterUnpin = null))
    }

    // L1b: an unpin frees the slot, the waiting message lands, and only then does the list close.
    @Test
    fun `a list opened full is done once the waiting message is pinned`() {
        assertFalse(ChatPins().read(emptyList(), readVersion = 5).pinnedListDone(pinAfterUnpin = 9))
        assertFalse(ChatPins().read(listOf(pin(3)), readVersion = 5).pinnedListDone(pinAfterUnpin = 9))
        assertTrue(ChatPins().read(listOf(pin(3), pin(9)), readVersion = 6).pinnedListDone(pinAfterUnpin = 9))
    }

    // Segments stack bottom up from the latest pin; the two-segment window scrolls with the lit one.
    @Test
    fun `one pin keeps the single rule`() {
        assertNull(showing(position = 1, of = 1).segments())
    }

    @Test
    fun `segments for each step through 2, 3 and 5 pins`() {
        // of to, per position: (visible bottom first, lit)
        val expected = mapOf(
            2 to listOf(1..2 to 1, 1..2 to 2),
            3 to listOf(1..2 to 1, 1..2 to 2, 2..3 to 3),
            5 to listOf(1..2 to 1, 1..2 to 2, 2..3 to 3, 3..4 to 4, 4..5 to 5),
        )
        for ((of, steps) in expected) {
            steps.forEachIndexed { index, (visible, lit) ->
                val segments = showing(position = index + 1, of = of).segments()!!
                assertEquals("$of pins, step ${index + 1}: visible", visible, segments.visible)
                assertEquals("$of pins, step ${index + 1}: lit", lit, segments.lit)
                assertEquals(of, segments.count)
            }
        }
    }

    @Test
    fun `tapping steps the lit segment up and wraps back to the latest`() {
        val pins = ChatPins().read((5 downTo 1).map { pin(it.toLong()) }, readVersion = 1)
        var index = 0
        val lit = (1..6).map {
            val segments = (pins.bar(index) as PinBar.Showing).segments()!!
            index = pins.stepAfter(index)
            segments.lit
        }
        assertEquals(listOf(1, 2, 3, 4, 5, 1), lit)
    }

    private fun showing(position: Int, of: Int) = PinBar.Showing(pin = pin(position.toLong()), position = position, of = of)
}
