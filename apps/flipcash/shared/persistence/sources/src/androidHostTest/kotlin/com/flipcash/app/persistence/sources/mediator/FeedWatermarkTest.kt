package com.flipcash.app.persistence.sources.mediator

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The merge rule for a feed paged from several sources at once. Two independently-paged feeds
 * both ordered by recency are only merged correctly down to the shallower of their two tails —
 * below that point one source may still be holding items that belong higher than items already
 * fetched from the other.
 */
class FeedWatermarkTest {

    private fun watermark() = FeedWatermark(listOf(DM, GROUP))

    @Test
    fun `a source that has never been paged goes first`() {
        val subject = watermark()

        assertEquals(DM, subject.next()?.key)
    }

    @Test
    fun `the shallowest source is the one to page next`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = true)
        subject.advance(GROUP, tail = 100, token = null, hasMore = true)

        // DM stopped at 500 and GROUP has already reached 100, so everything between them is
        // known from GROUP and unknown from DM. DM is what the merged list is waiting on.
        assertEquals(DM, subject.next()?.key)
    }

    @Test
    fun `an exhausted source is never paged again`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = false)
        subject.advance(GROUP, tail = 100, token = null, hasMore = true)

        assertEquals(GROUP, subject.next()?.key)
    }

    @Test
    fun `the watermark ignores a source that has run out`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = false)
        subject.advance(GROUP, tail = 100, token = null, hasMore = true)

        // DM has nothing left below 500, so it constrains nothing. GROUP is the only source
        // that can still produce something, and it is complete down to 100.
        assertEquals(100L, subject.watermark)
    }

    @Test
    fun `the watermark is unknown until every live source has produced a tail`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = true)

        assertNull(subject.watermark)
    }

    @Test
    fun `pagination is complete only when every source has run out`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = false)
        assertFalse(subject.isComplete)

        subject.advance(GROUP, tail = 100, token = null, hasMore = false)
        assertTrue(subject.isComplete)
    }

    /**
     * One source failing must not stop the others paging — that is the asymmetry this replaces,
     * where a CONTACT_DM failure failed the whole sync. It does block completeness, and so blocks
     * the removal reconciliation that needs a whole feed to be sound.
     */
    @Test
    fun `a failed source is skipped but still holds completeness back`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = true)
        subject.fail(GROUP)

        assertEquals(DM, subject.next()?.key)

        subject.advance(DM, tail = 100, token = null, hasMore = false)
        assertNull(subject.next())
        assertFalse(subject.isComplete)
        assertEquals(listOf(GROUP), subject.failedKeys)
    }

    @Test
    fun `a source that answers again is no longer failed`() {
        val subject = watermark()
        subject.fail(GROUP)
        subject.advance(GROUP, tail = 100, token = null, hasMore = true)

        assertEquals(emptyList(), subject.failedKeys)
    }

    /**
     * An empty page still reports whether the source has more; it just has no tail to contribute.
     * Taking its non-existent tail would reset the source to "never paged" and restart it.
     */
    @Test
    fun `an empty page leaves the tail where it was`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = true)
        subject.advance(DM, tail = null, token = null, hasMore = true)
        subject.advance(GROUP, tail = 100, token = null, hasMore = true)

        assertEquals(DM, subject.next()?.key)
    }

    @Test
    fun `reset puts every source back to never paged`() {
        val subject = watermark()
        subject.advance(DM, tail = 500, token = null, hasMore = false)
        subject.fail(GROUP)

        subject.reset()

        assertEquals(DM, subject.next()?.key)
        assertFalse(subject.isComplete)
        assertEquals(emptyList(), subject.failedKeys)
    }

    private companion object {
        const val DM = "CONTACT_DM"
        const val GROUP = "GROUP"
    }
}
