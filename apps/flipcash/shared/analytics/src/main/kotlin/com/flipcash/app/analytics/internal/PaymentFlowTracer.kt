package com.flipcash.app.analytics.internal

import com.flipcash.app.analytics.FlowTrace
import com.getcode.services.flipcash.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.perf.performance

/** Emits a completed payment flow as a backend performance trace. */
internal interface PaymentFlowTracer {
    fun record(trace: FlowTrace, success: Boolean)
}

/**
 * Firebase Performance implementation. Creates a custom trace named after the
 * flow, attaches each stage duration as a metric plus a success attribute, and
 * stops it immediately (the metrics carry the timing; the trace's own wall-clock
 * is incidental). No-ops in debug builds to avoid polluting dashboards from dev.
 */
internal class FirebasePaymentFlowTracer : PaymentFlowTracer {
    override fun record(trace: FlowTrace, success: Boolean) {
        if (BuildConfig.DEBUG) return
        runCatching {
            val fbTrace = Firebase.performance.newTrace(trace.name)
            fbTrace.start()
            trace.metrics.forEach { (name, ms) -> fbTrace.putMetric(sanitize(name), ms) }
            fbTrace.putAttribute("success", success.toString())
            fbTrace.stop()
        }
    }

    private fun sanitize(name: String): String =
        name.replace(METRIC_NAME_ILLEGAL_CHARS, "_").trimStart('_').take(MAX_METRIC_NAME_LENGTH)

    private companion object {
        // Firebase rejects a leading underscore and caps metric names at 100 chars.
        // Our span names are short and start with a letter; we replace any illegal
        // char and cap length defensively so an unexpected name can never be rejected.
        val METRIC_NAME_ILLEGAL_CHARS = Regex("[^A-Za-z0-9_]")
        const val MAX_METRIC_NAME_LENGTH = 100
    }
}
