package com.flipcash.app.internal.startup

import android.content.Context
import androidx.startup.Initializer
import com.flipcash.app.internal.time.SntpClient
import com.getcode.utils.ClockSource
import com.getcode.utils.TraceManager
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Best-effort SNTP measurement of how far the device clock has drifted from real time, taken once at
 * startup. The result is recorded on [TraceManager] (so it appears in the exported log header) and
 * emitted as a launch breadcrumb. A large drift is the likely cause of `INVALID_TIMESTAMP` request
 * rejections, so capturing it on launch gives a diagnostic signal even before the event stream
 * connects (and even when a bad clock would prevent that stream from authenticating at all).
 *
 * Depends on [TraceInitializer] so [TraceManager] is initialized before we record/trace.
 */
class ClockDriftInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val offsetMillis = SntpClient.queryClockOffsetMillis()
            if (offsetMillis == null) {
                trace(tag = "clock", message = "Clock drift on launch: unavailable (SNTP query failed)")
                return@launch
            }
            // drift = device time - true time = negative of the NTP offset (true - device).
            val driftMillis = -offsetMillis
            TraceManager.recordClockDrift(driftMillis, source = ClockSource.Sntp)
            trace(
                tag = "clock",
                message = "Clock drift on launch",
                metadata = {
                    "driftMs" to driftMillis
                    "source" to "sntp"
                },
            )
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> {
        return listOf(TraceInitializer::class.java)
    }
}
