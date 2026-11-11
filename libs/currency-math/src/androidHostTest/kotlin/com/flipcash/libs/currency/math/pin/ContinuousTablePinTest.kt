package com.flipcash.libs.currency.math.pin

import com.flipcash.libs.currency.math.internal.curves.ContinuousBondingCurve
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [ContinuousBondingCurve.formattedTable] to the output it had while it was built on
 * `String.format` and `java.text.DecimalFormat`, so moving it to common code can't change a character.
 */
class ContinuousTablePinTest {
    @Test
    fun formattedTableIsUnchanged() {
        val table = ContinuousBondingCurve.getOrThrow().formattedTable()
        val digest = MessageDigest.getInstance("SHA-256").digest(table.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(106, table.lines().size)
        assertEquals(PINNED_SHA256, digest)
    }

    private companion object {
        const val PINNED_SHA256 = "28d856008a6e1091c6fe0386fcddf711bd4f00ee81d9ffa259c4f8f81f3c2f61"
    }
}
