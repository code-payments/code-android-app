package com.flipcash.app.notifications

import com.flipcash.services.models.NotificationCategory
import com.flipcash.services.models.NotificationPayload
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class TestPushMarkTest {

    private val time = TestTimeSource()
    private val mark = TestPushMark(time)

    @Test
    fun `a push with no payload while marked is the test, and clears the mark`() {
        mark.mark()

        assertTrue(mark.consume(null))
        assertFalse(mark.isMarked)
        // Only the one push: the next is an ordinary one.
        assertFalse(mark.consume(null))
    }

    @Test
    fun `a push with an all-defaults payload while marked is the test`() {
        mark.mark()

        assertTrue(mark.consume(NotificationPayload(navigation = null)))
    }

    @Test
    fun `a push with no payload while unmarked is not the test`() {
        assertFalse(mark.consume(null))
    }

    @Test
    fun `a push with a payload while marked is not the test, and leaves the mark`() {
        mark.mark()

        assertFalse(mark.consume(NotificationPayload(navigation = null, category = NotificationCategory.CHAT)))
        assertTrue(mark.isMarked)
    }

    @Test
    fun `the mark lapses 30 s after it was set`() {
        mark.mark()

        time += TestPushMark.Lifetime - 1.milliseconds
        assertTrue(mark.isMarked)

        time += 1.milliseconds
        assertFalse(mark.isMarked)
        assertFalse(mark.consume(null))
    }

    @Test
    fun `clear drops the mark`() {
        mark.mark()
        mark.clear()

        assertFalse(mark.consume(null))
    }
}
