package com.flipcash.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingLegacyMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = benchmark(CompilationMode.None())

    @Test
    fun startupPartialCompilation() = benchmark(
        CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3,
        )
    )

    @Test
    fun startupBaselineProfile() = benchmark(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    )

    @Test
    fun startupFullCompilation() = benchmark(CompilationMode.Full())

    private fun benchmark(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = "com.flipcash.app.android",
            // Legacy reads the ActivityManager logcat lines; StartupTimingMetric reads the
            // trace and is the one that reports timeToFullDisplayMs.
            metrics = listOf(StartupTimingMetric(), StartupTimingLegacyMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            pressHome()
            startActivityAndWait()
            // startActivityAndWait returns at first display. Without this the trace can stop
            // before the wallet reports fully drawn and TTFD goes missing. Bounded, so a launch
            // that lands somewhere else (logged out) costs one timeout rather than hanging.
            device.wait(Until.hasObject(By.res("wallet_content")), FULL_DISPLAY_TIMEOUT)
        }
    }

    private companion object {
        const val FULL_DISPLAY_TIMEOUT = 10_000L
    }
}
