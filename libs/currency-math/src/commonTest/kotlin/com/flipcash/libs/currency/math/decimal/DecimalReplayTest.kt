package com.flipcash.libs.currency.math.decimal

import com.flipcash.libs.currency.math.readTestResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays `java.math` results on every target. On the JVM and Android the decimal type is
 * `java.math` itself, so this mainly guards the fixture; on Node it is the check that
 * [CommonBigDecimal] over native `BigInt` gives the same values and scales as `java.math`.
 */
class DecimalReplayTest {
    @Test
    fun matchesJavaMath() {
        val cases = DecimalReplay.parse(readTestResource(DecimalReplay.FILE))
        assertTrue(cases.size > 3000, "only ${cases.size} cases")
        val mismatches = cases.mapNotNull { (op, args, expected) ->
            val actual = DecimalReplay.evaluate(op, args)
            if (actual == expected) null else "$op${args.joinToString("|", "|")}: expected $expected, got $actual"
        }
        assertEquals(emptyList(), mismatches.take(20), "${mismatches.size} of ${cases.size} cases differ")
    }
}
