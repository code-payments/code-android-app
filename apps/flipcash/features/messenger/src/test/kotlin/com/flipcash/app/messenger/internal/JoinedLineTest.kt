package com.flipcash.app.messenger.internal

import com.getcode.util.resources.AndroidResources
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** The profile screen and a person card both read this, so its wording is pinned here once. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JoinedLineTest {

    @Test
    fun `a join date reads as the month and year`() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val resources = AndroidResources(RuntimeEnvironment.getApplication())

        // 2024-03-09T16:00:00Z
        assertEquals("Joined March 2024", joinedLine(Instant.fromEpochSeconds(1_710_000_000), resources))
    }
}
