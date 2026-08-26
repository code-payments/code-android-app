package com.flipcash.app.internal.startup

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.startup.Initializer

/**
 * Warms up the CameraX provider so the scanner opens faster on first use.
 *
 * Purely an optimisation — a failure costs a cold camera open, nothing more, so it must not be able
 * to take down launch. `ProcessCameraProvider.getInstance` reaches `Context.getDeviceId()` on
 * API 34+, which throws `NoSuchMethodError` on runtimes that misreport their API level
 * (Bugsnag `6a8f47a7b5ee91bed8ac6cac`).
 *
 * Depends on [TraceInitializer] so a failure here is actually traceable.
 */
class CameraXInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        launchBestEffort(tag = "camerax") {
            ProcessCameraProvider.getInstance(context)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return listOf(TraceInitializer::class.java)
    }
}
