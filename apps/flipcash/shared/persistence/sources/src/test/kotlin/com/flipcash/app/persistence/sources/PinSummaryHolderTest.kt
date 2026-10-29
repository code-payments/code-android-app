package com.flipcash.app.persistence.sources

import com.flipcash.services.models.chat.PinSummary
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PinSummaryHolderTest {

    private val holder = PinSummaryHolder()
    private val v3 = PinSummary(count = 2, version = 3)

    @Test
    fun `a first read is held`() {
        assertEquals(v3, holder.merge("ab", v3))
        assertEquals(v3, holder.get("ab"))
    }

    @Test
    fun `a list result without a summary keeps the held one`() {
        holder.merge("ab", v3)

        assertEquals(v3, holder.merge("ab", null))
        assertEquals(v3, holder.get("ab"))
    }

    @Test
    fun `nothing held and nothing incoming stays empty`() {
        assertNull(holder.merge("ab", null))
        assertNull(holder.get("ab"))
    }

    @Test
    fun `an older summary keeps the held one`() {
        holder.merge("ab", v3)

        assertEquals(v3, holder.merge("ab", PinSummary(count = 1, version = 2)))
    }

    @Test
    fun `an equal version replaces the held summary`() {
        holder.merge("ab", v3)
        val same = PinSummary(count = 2, version = 3)

        assertEquals(same, holder.merge("ab", same))
    }

    @Test
    fun `a newer summary replaces the held one`() {
        holder.merge("ab", v3)
        val newer = PinSummary(count = 3, version = 4)

        assertEquals(newer, holder.merge("ab", newer))
    }

    @Test
    fun `chats are held independently`() {
        holder.merge("ab", v3)

        assertNull(holder.get("cd"))
    }

    @Test
    fun `a pins event applies when nothing is held and its version is above zero`() {
        assertTrue(holder.applyChanged("ab", PinSummary(count = 1, version = 1)))
        assertEquals(1, holder.get("ab")?.version)
    }

    @Test
    fun `a pins event at version zero with nothing held is dropped`() {
        assertFalse(holder.applyChanged("ab", PinSummary(version = 0)))
        assertNull(holder.get("ab"))
    }

    @Test
    fun `a pins event applies only when strictly newer`() {
        holder.merge("ab", v3)

        assertFalse(holder.applyChanged("ab", PinSummary(count = 1, version = 2)))
        assertFalse(holder.applyChanged("ab", PinSummary(count = 9, version = 3)))
        assertEquals(v3, holder.get("ab"))

        val v4 = PinSummary(count = 3, version = 4)
        assertTrue(holder.applyChanged("ab", v4))
        assertEquals(v4, holder.get("ab"))
    }
}
