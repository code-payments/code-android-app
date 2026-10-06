package com.flipcash.app.menu.internal

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

// Robolectric because the pattern comes from android.text.format.DateFormat, which a plain JVM
// test only has as a stub.
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JoinedLabelTest {

    // 2026-10-06T12:00:00Z
    private val joinedAt = Instant.fromEpochSeconds(1_791_288_000)

    @Test
    fun `joined is the full month and year`() {
        assertEquals("October 2026", joinedLabel(joinedAt, Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun `joined follows the locale`() {
        assertEquals("octubre de 2026", joinedLabel(joinedAt, Locale.forLanguageTag("es"), ZoneOffset.UTC))
    }

    @Test
    fun `no join date means no label`() {
        assertNull(joinedLabel(null, Locale.US, ZoneOffset.UTC))
    }
}
