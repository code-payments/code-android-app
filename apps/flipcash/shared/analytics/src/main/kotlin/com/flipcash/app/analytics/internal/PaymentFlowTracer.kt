package com.flipcash.app.analytics.internal

import com.flipcash.app.analytics.FlowTrace
import com.getcode.services.flipcash.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.perf.performance
import javax.inject.Inject

/** Emits a completed payment flow as a backend performance trace. */
interface PaymentFlowTracer {
    fun record(trace: FlowTrace, success: Boolean)
}

/**
 * Firebase Performance implementation. Creates a custom trace named after the
 * flow, attaches each stage duration as a metric plus a success attribute, and
 * stops it immediately (the metrics carry the timing; the trace's own wall-clock
 * is incidental). No-ops in debug builds to avoid polluting dashboards from dev.
 */
internal class FirebasePaymentFlowTracer @Inject constructor() : PaymentFlowTracer {
    override fun record(trace: FlowTrace, success: Boolean) {
        if (BuildConfig.DEBUG) return
        val fbTrace = Firebase.performance.newTrace(trace.name)
        fbTrace.start()
        trace.metrics.forEach { (name, ms) -> fbTrace.putMetric(sanitize(name), ms) }
        fbTrace.putAttribute("success", success.toString())
        fbTrace.stop()
    }

    // Firebase metric names: <= 32 chars, [A-Za-z][A-Za-z0-9_]*. Span names already fit;
    // sanitize defensively so an unexpected name can never crash a payment.
    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_]"), "_").take(32)
}
