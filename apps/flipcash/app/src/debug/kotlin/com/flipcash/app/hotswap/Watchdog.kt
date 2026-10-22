package com.flipcash.app.hotswap

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Debug-only main-thread watchdog. A background thread posts a ping to the main looper every
 * second; when the previous ping has not run for [STALL_MS] it logs the main thread's stack and
 * every other thread's (tag `HotSwap`) and hands the same text to [report]. It dumps again every
 * [REDUMP_MS] while the stall lasts, so a stack that moves can be told from one that does not.
 */
internal object Watchdog {
    private const val TAG = "HotSwap"
    private const val STALL_MS = 3000L
    private const val REDUMP_MS = 4000L
    private const val MAX_REPORT_CHARS = 60_000

    @Volatile private var started = false
    @Volatile private var lastRun = SystemClock.uptimeMillis()
    @Volatile private var pingPending = false

    fun start(report: (JSONObject) -> Unit) {
        if (started) return
        started = true
        val main = Looper.getMainLooper()
        val handler = Handler(main)
        Thread({
            var lastDump = 0L
            while (true) {
                val now = SystemClock.uptimeMillis()
                if (!pingPending) {
                    pingPending = true
                    handler.post { lastRun = SystemClock.uptimeMillis(); pingPending = false }
                }
                val stalledMs = now - lastRun
                if (stalledMs >= STALL_MS && now - lastDump >= REDUMP_MS) {
                    lastDump = now
                    dump(main.thread, stalledMs, report)
                }
                try { Thread.sleep(1000) } catch (_: InterruptedException) { return@Thread }
            }
        }, "hotswap-watchdog").apply { isDaemon = true }.start()
    }

    private fun dump(mainThread: Thread, stalledMs: Long, report: (JSONObject) -> Unit) {
        val mainStack = mainThread.stackTrace.joinToString("\n") { "    at $it" }
        Log.w(TAG, "main thread stalled ${stalledMs}ms, state=${mainThread.state}\n$mainStack")
        val others = StringBuilder()
        for ((t, st) in Thread.getAllStackTraces()) {
            if (t === mainThread) continue
            others.append("\"${t.name}\" ${t.state}\n")
            st.forEach { others.append("    at ").append(it).append('\n') }
        }
        others.toString().chunked(3500).forEach { Log.w(TAG, "threads at stall:\n$it") }
        report(
            JSONObject().put("event", "stall").put("stalledMs", stalledMs).put("mainState", mainThread.state.toString())
                .put("main", JSONArray(mainStack.lines())).put("others", others.toString().take(MAX_REPORT_CHARS))
                .put("at", System.currentTimeMillis())
        )
    }
}
