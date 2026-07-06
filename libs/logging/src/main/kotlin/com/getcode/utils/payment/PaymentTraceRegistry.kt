package com.getcode.utils.payment

import com.getcode.utils.TraceType
import com.getcode.utils.trace

/**
 * Process-global registry of in-flight [PaymentTrace]s, keyed by the rendezvous public key
 * string. Mirrors the existing `TraceManager` object pattern so no Hilt wiring is needed from
 * the (non-Hilt) scanner path or from the service layer.
 *
 * On [finish] the trace is logged as a structured summary via the existing `trace()` (so it
 * lands in the file log, Bugsnag breadcrumbs, and logStream) and then evicted.
 */
object PaymentTraceRegistry {

    /** Backstop so an abandoned flow (opened, never finished) cannot leak unboundedly. */
    const val MAX_IN_FLIGHT = 16

    // insertion-ordered so we can evict the oldest; guarded by [lock].
    private val lock = Any()
    private val inFlight = LinkedHashMap<String, PaymentTrace>()

    fun open(correlationId: String, flow: PaymentFlow): PaymentTrace {
        val trace = PaymentTrace(correlationId, flow)
        synchronized(lock) {
            inFlight.remove(correlationId)
            inFlight[correlationId] = trace
            while (inFlight.size > MAX_IN_FLIGHT) {
                val oldest = inFlight.keys.firstOrNull() ?: break
                inFlight.remove(oldest)
            }
        }
        return trace
    }

    fun get(correlationId: String): PaymentTrace? = synchronized(lock) { inFlight[correlationId] }

    /** Close a trace: log its summary, evict it, and return it (or null if unknown). */
    fun finish(correlationId: String, success: Boolean, error: Throwable? = null): PaymentTrace? {
        val trace = synchronized(lock) { inFlight.remove(correlationId) } ?: return null
        logSummary(trace, success, error)
        return trace
    }

    /** Returns the structured metadata map for a finished trace. Extracted for testability. */
    internal fun summaryMetadata(paymentTrace: PaymentTrace, success: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "flow" to paymentTrace.flow.name,
            "correlationId" to paymentTrace.correlationId,
            "totalMs" to paymentTrace.totalMs,
        )
        map.putAll(paymentTrace.durations())
        return map
    }

    private fun logSummary(paymentTrace: PaymentTrace, success: Boolean, error: Throwable?) {
        trace(
            tag = "PaymentTrace",
            message = "${paymentTrace.flow} ${if (success) "OK" else "FAIL"} total=${paymentTrace.totalMs}ms",
            type = if (success) TraceType.Process else TraceType.Error,
            error = error,
            metadata = {
                summaryMetadata(paymentTrace, success).forEach { (k, v) -> k to v }
            },
        )
    }

    /** Test-only reset. */
    internal fun clearForTest() = synchronized(lock) { inFlight.clear() }
}
