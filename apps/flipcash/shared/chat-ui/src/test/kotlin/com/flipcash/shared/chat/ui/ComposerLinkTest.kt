package com.flipcash.shared.chat.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the composer's link sheet accepts, and the ranges it hands the formatting helpers. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ComposerLinkTest {

    @Test
    fun `exactly one link is accepted`() {
        assertEquals("https://flipcash.com", maskableUrlOrNull("https://flipcash.com"))
        assertEquals("https://flipcash.com/a?b=1", maskableUrlOrNull("  https://flipcash.com/a?b=1 "))
    }

    @Test
    fun `anything that is not exactly one link is rejected`() {
        assertNull(maskableUrlOrNull(""))
        assertNull(maskableUrlOrNull("hello"))
        assertNull(maskableUrlOrNull("see https://flipcash.com"))
        assertNull(maskableUrlOrNull("https://a.com https://b.com"))
    }

    @Test
    fun `links and mentions in a draft are protected`() {
        val ranges = protectedRangesOf("hi @jeff see https://flipcash.com")
        assertEquals(2, ranges.size)
    }
}
