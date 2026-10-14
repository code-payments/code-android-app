package com.flipcash.app.updates.internal

import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug builds only. Walks a [FakeAppUpdateManager] through Play's flexible download so the Settings
 * card can be seen in each state on an emulator. The marker file's text says where to hold:
 * `adb shell run-as com.flipcash.app.android sh -c 'echo 40 > files/debug-optional-update'`
 * holds the download at 40%, and `done` finishes it. Empty text accepts the dialog and holds at 0%.
 */
internal class DebugUpdateDriver(
    private val fake: FakeAppUpdateManager,
    private val marker: File,
) {
    private var downloading = false

    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(POLL_MILLIS)
            runCatching { step() }
        }
    }

    private fun step() {
        if (fake.isConfirmationDialogVisible) {
            fake.userAcceptsUpdate()
            fake.downloadStarts()
            fake.setTotalBytesToDownload(TOTAL_BYTES)
            fake.setBytesDownloaded(0)
            downloading = true
        }
        if (!downloading) return
        val text = marker.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        if (text == "done") {
            fake.setBytesDownloaded(TOTAL_BYTES)
            fake.downloadCompletes()
            downloading = false
        } else {
            val percent = text.toIntOrNull()?.coerceIn(0, 100) ?: 0
            fake.setBytesDownloaded(TOTAL_BYTES * percent / 100)
        }
    }

    private companion object {
        const val POLL_MILLIS = 500L
        const val TOTAL_BYTES = 100_000_000L
    }
}
