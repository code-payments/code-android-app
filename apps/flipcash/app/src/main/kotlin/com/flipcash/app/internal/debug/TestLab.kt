package com.flipcash.app.internal.debug

import android.content.Context
import android.provider.Settings
import com.flipcash.app.updates.ReleaseStage

/**
 * Whether this process runs on a Firebase Test Lab device, which is where Play's pre-launch
 * reports run every build uploaded to a track.
 *
 * Those robots crawl the app on emulators and cloud phones, some of them broken in ways no user's
 * device is: the internal build 4804 sent Bugsnag a `NoSuchMethodError` from Play Billing and a
 * null `getClass()` in `OpenCodeExchange.streamRates` from an x86_64 "Pixel 8 Pro" with 2 GB of
 * memory. Reported under the track's stage, they alert as if a tester had crashed.
 */
internal object TestLab {

    private const val SETTING = "firebase.test.lab"

    /** Test Lab sets this system setting to `"true"` on every device it runs. */
    fun isRunning(context: Context): Boolean = try {
        Settings.System.getString(context.contentResolver, SETTING) == "true"
    } catch (_: Throwable) {
        false
    }
}

/** The stage Bugsnag files an event under when the device belongs to Test Lab. */
internal const val TestLabStage = "TestLab"

/**
 * The release stage to report. A Test Lab device reports [TestLabStage] whatever track the build
 * is on, so its events stay searchable but fall outside the Internal and Production alerts.
 */
internal fun bugsnagReleaseStage(stage: ReleaseStage?, onTestLab: Boolean): String = when {
    onTestLab -> TestLabStage
    else -> stage?.name ?: "Unknown"
}
