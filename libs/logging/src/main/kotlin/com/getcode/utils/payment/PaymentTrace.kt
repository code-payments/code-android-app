package com.getcode.utils.payment

import kotlin.time.TimeSource

/** Which side of a cash hand-off a trace represents. */
enum class PaymentFlow { Give, Grab }

/** One recorded stage of a payment flow. */
data class PaymentSpan(
    val name: String,
    val durationMs: Long,
    val success: Boolean,
    val error: Throwable? = null,
    val metadata: Map<String, Any> = emptyMap(),
)

/**
 * A single give/grab timeline, correlated by the rendezvous public key. Thread-safe: spans are
 * appended under a lock, so concurrent stages on the same flow don't corrupt the list.
 * Created/closed via [PaymentTraceRegistry]; construct directly only in tests.
 */
class PaymentTrace(
    val correlationId: String,
    val flow: PaymentFlow,
) {
    private val lock = Any()
    private val _spans = mutableListOf<PaymentSpan>()

    fun spans(): List<PaymentSpan> = synchronized(lock) { _spans.toList() }

    /**
     * name -> duration in whole milliseconds, preserving insertion order.
     * If the same name is recorded more than once, only the last occurrence appears in the map.
     */
    fun durations(): Map<String, Long> =
        synchronized(lock) { _spans.associate { it.name to it.durationMs } }

    val totalMs: Long get() = synchronized(lock) { _spans.sumOf { it.durationMs } }

    private fun add(span: PaymentSpan) = synchronized(lock) { _spans.add(span) }

    /** Record a stage whose duration was measured elsewhere (e.g. the native scan). */
    fun mark(name: String, durationMs: Long, metadata: Map<String, Any> = emptyMap()) {
        add(PaymentSpan(name = name, durationMs = durationMs, success = true, metadata = metadata))
    }

    /** Time [block] as a span. Non-local returns work because the function is inline. */
    inline fun <T> span(name: String, metadata: Map<String, Any> = emptyMap(), block: () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            val result = block()
            record(name, start.elapsedNow().inWholeMilliseconds, true, null, metadata)
            return result
        } catch (t: Throwable) {
            record(name, start.elapsedNow().inWholeMilliseconds, false, t, metadata)
            throw t
        }
    }

    /** Suspend variant of [span]. Inline so suspend calls inside [block] and non-local returns work. */
    suspend inline fun <T> spanSuspend(
        name: String,
        metadata: Map<String, Any> = emptyMap(),
        block: suspend () -> T,
    ): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            val result = block()
            record(name, start.elapsedNow().inWholeMilliseconds, true, null, metadata)
            return result
        } catch (t: Throwable) {
            record(name, start.elapsedNow().inWholeMilliseconds, false, t, metadata)
            throw t
        }
    }

    /** Public only so the inline [span]/[spanSuspend] can call it; do not call directly. */
    @PublishedApi
    internal fun record(
        name: String,
        durationMs: Long,
        success: Boolean,
        error: Throwable?,
        metadata: Map<String, Any>,
    ) = add(PaymentSpan(name, durationMs, success, error, metadata))
}

/**
 * Null-safe span: if the trace is absent, still run [block] (untimed).
 * Named [spanOrRun] rather than [span] to avoid the `?.`-safe-call footgun: a call written as
 * `trace?.span { }` on a nullable receiver resolves to the member and silently drops the block
 * when null; the distinct name forces callers to be explicit about the null-fallback intent.
 */
inline fun <T> PaymentTrace?.spanOrRun(
    name: String,
    metadata: Map<String, Any> = emptyMap(),
    block: () -> T,
): T = this?.span(name, metadata, block) ?: block()

/**
 * Null-safe suspend span. See [spanOrRun] for the naming rationale.
 */
suspend inline fun <T> PaymentTrace?.spanSuspendOrRun(
    name: String,
    metadata: Map<String, Any> = emptyMap(),
    block: suspend () -> T,
): T = if (this != null) spanSuspend(name, metadata, block) else block()
