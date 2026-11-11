package com.getcode.opencode.pin

import kotlin.test.Test
import kotlin.test.assertEquals

/** What this target is checked against, and the locale cases it can run. */
internal class PinTarget(
    /** The fixture this target must match exactly. */
    val fixture: String,
    /** The `locale|...` lines this target can produce; tags it can't switch to are skipped. */
    val localeLines: List<String>,
    val localeTags: Set<String>,
)

internal expect fun pinTarget(): PinTarget

/**
 * Every formatting, parsing and amount case on this target matches its pinned dump. Node is held to
 * android-api36.txt, the Android output, with no exceptions; the host JVM to host-jdk.txt, which
 * is how the host JDK differs from Android. Regenerate both with DevicePinDumpTest's instructions.
 */
class PinParityTest {

    @Test
    fun everyCaseMatchesThePinnedOutput() {
        val target = pinTarget()
        val expected = PinCheck.parse(readTestResource(target.fixture).lines())
        val actual = PinCheck.parse(PinDump.lines(target.localeLines))
        val skipped = { key: String -> key.startsWith("locale|") && key.split('|')[1] !in target.localeTags }
        assertEquals(emptyList(), PinCheck.differences(expected, actual, skipped), "differences from ${target.fixture}")
    }
}
