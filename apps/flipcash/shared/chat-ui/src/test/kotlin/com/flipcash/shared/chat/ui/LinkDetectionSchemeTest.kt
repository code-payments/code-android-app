package com.flipcash.shared.chat.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A link's scheme in any case, which `link_detection.json` has no vector for yet. Kept apart from
 * [LinkDetectionVectorTest] because that file only replays the synced fixture.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkDetectionSchemeTest {

    @Test
    fun `an uppercase scheme is lowercased, not prefixed with a second one`() {
        val text = "HTTPS://flipcash.com/someone"
        assertEquals(
            listOf(DetectedUrl(0, text.length, "https://flipcash.com/someone")),
            detectUrls(text),
        )
    }

    @Test
    fun `a mixed-case scheme is lowercased and the rest is left as written`() {
        val text = "see Https://Flipcash.com/Someone"
        assertEquals(
            listOf(DetectedUrl(4, text.length, "https://Flipcash.com/Someone")),
            detectUrls(text),
        )
    }

    @Test
    fun `a link with no scheme still gets https`() {
        val text = "flipcash.com/someone"
        assertEquals(
            listOf(DetectedUrl(0, text.length, "https://flipcash.com/someone")),
            detectUrls(text),
        )
    }
}
