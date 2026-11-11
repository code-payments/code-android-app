package com.getcode.opencode.pin

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every pinned case as this device computes it must match pins/android-api36.txt, the reference that
 * Node is held to. The dump also goes to logcat (tag PINDUMP, `index|line`), because AGP uninstalls
 * the test APK after the run; to regenerate the fixture, run this test and write the logged lines,
 * sorted by index, without the index, to that file.
 */
@RunWith(AndroidJUnit4::class)
class DevicePinDumpTest {

    private val tags = listOf("en-US", "de-DE", "en-IN")

    private fun dump(): List<String> {
        val previous = Locale.getDefault()
        val locale = try {
            tags.flatMap { tag ->
                Locale.setDefault(Locale.forLanguageTag(tag))
                LocaleFormattingCases.cases().map { (name, out) -> "locale|$tag|$name|$out" }
            }
        } finally {
            Locale.setDefault(previous)
        }
        return PinDump.lines(locale)
    }

    @Test
    fun everyCaseMatchesTheAndroidPins() {
        val lines = dump()
        Log.i("PINDUMP", "api ${Build.VERSION.SDK_INT} ${Build.FINGERPRINT}")
        lines.forEachIndexed { i, line -> Log.i("PINDUMP", "$i|$line") }
        Log.i("PINDUMP", "end")

        val pinned = InstrumentationRegistry.getInstrumentation().context.assets
            .open("pins/android-api36.txt").bufferedReader().use { it.readText() }
        assertEquals(emptyList<String>(), PinCheck.differences(PinCheck.parse(pinned.lines()), PinCheck.parse(lines)))
    }
}
