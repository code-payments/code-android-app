package com.flipcash.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame-timing benchmark for scrolling the Discover-tab token leaderboard — the screen
 * that ANR'd on a low-end device (Bugsnag 6a46563b). Compares an unprofiled build against
 * one compiled from the baseline profile so the profile's effect on first-scroll jank is
 * measurable. Headline metric: P95 frameOverrunMs (negative = on time).
 *
 * Requires an already-authenticated device (run the BaselineProfileGenerator once, or
 * log in, first — the setup navigates from a cold launch to the Discover leaderboard).
 * On an emulator, results are suppressed-but-runnable (see the module's suppressErrors);
 * report medians from a physical low-end device for real numbers.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LeaderboardScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollNoCompilation() = scroll(CompilationMode.None())

    @Test
    fun scrollBaselineProfile() =
        scroll(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun scroll(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                // Navigate to the Discover leaderboard (device must be authenticated).
                device.wait(Until.findObject(By.text("Discover")), TIMEOUT)?.click()
                device.wait(Until.findObject(By.res("discovery_leaderboard")), LIST_TIMEOUT)
                device.waitForIdle()
            },
        ) {
            // Re-find each fling: the LazyColumn recomposes on scroll, which would
            // otherwise invalidate a held UiObject2 (StaleObjectException).
            repeat(3) { flingLeaderboard(Direction.UP) }
            repeat(3) { flingLeaderboard(Direction.DOWN) }
        }
    }

    private fun MacrobenchmarkScope.flingLeaderboard(direction: Direction) {
        val list = device.findObject(By.res("discovery_leaderboard")) ?: return
        try {
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
        } catch (_: StaleObjectException) {
            // recomposed mid-fling; next call re-finds
        }
        device.waitForIdle()
    }

    companion object {
        private const val PACKAGE_NAME = "com.flipcash.app.android"
        private const val TIMEOUT = 5_000L
        private const val LIST_TIMEOUT = 15_000L
    }
}
