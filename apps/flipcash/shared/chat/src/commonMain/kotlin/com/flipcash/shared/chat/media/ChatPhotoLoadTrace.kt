package com.flipcash.shared.chat.media

import com.flipcash.services.models.chat.MediaItemRendition
import kotlin.time.Duration

/**
 * One chat photo load, step by step, for the trace log: how long the URL mint, the CDN's response
 * headers, the body transfer and the decrypt each took, which CloudFront edge served it, and which
 * step failed and why.
 *
 * Holds the URL's host only. The signed query string authorizes the download, so it never reaches
 * the log.
 */
internal class ChatPhotoLoadTrace(photo: ChatPhoto) {
    val key: String = photo.cacheKey
    val role: MediaItemRendition.Role = photo.rendition.role
    val width: Int? = photo.rendition.blob?.image?.width
    val sealed: Boolean = photo.sealed

    var cacheHit: Boolean = false

    /** The step running now, so a failure or cancel can say where it happened. */
    var step: Step = Step.Url

    /** One per URL tried: the first, then the re-mint after a failed download. */
    val attempts: MutableList<Attempt> = mutableListOf()

    /** Opening a sealed photo; null for a plain one. */
    var decrypt: Duration? = null

    var outcome: Outcome? = null
    var total: Duration? = null

    enum class Step(val label: String) {
        Url("url"),
        Download("download"),
        Decrypt("decrypt"),
    }

    sealed interface Outcome {
        data object Ok : Outcome
        data class Failed(val step: Step, val error: String) : Outcome
        data class Cancelled(val step: Step) : Outcome
    }

    class Attempt {
        /** The `GetBlobs` call that minted this URL; null when the stored URL was still good. */
        var mint: Duration? = null
        var host: String? = null

        /** From the request until the response headers arrived, connection setup included. */
        var ttfb: Duration? = null

        /** Reading the body after the headers. */
        var transfer: Duration? = null
        var status: Int? = null
        var bytes: Int? = null

        /** CloudFront's `X-Cache`: whether the edge had the object (`Hit from cloudfront`) or went to origin. */
        var xCache: String? = null

        /** CloudFront's `X-Amz-Cf-Pop`: the edge location that answered, e.g. `ORD58-P16`. */
        var pop: String? = null
        var error: String? = null
    }

    fun newAttempt(): Attempt = Attempt().also { attempts += it }

    /** Records [error] as the outcome: [Outcome.Cancelled] for a cancel, else [Outcome.Failed]. */
    fun fail(error: Throwable, cancelled: Boolean) {
        outcome = if (cancelled) Outcome.Cancelled(step) else Outcome.Failed(step, error.failureName())
    }

    fun message(): String = buildString {
        append("Chat photo ").append(key)
        when (val outcome = outcome) {
            Outcome.Ok -> append(" outcome=ok")
            is Outcome.Failed -> append(" outcome=failed step=").append(outcome.step.label).append(" error=").append(outcome.error)
            is Outcome.Cancelled -> append(" outcome=cancelled step=").append(outcome.step.label)
            null -> append(" outcome=unknown")
        }
        total?.let { append(" total=").append(it.ms()) }
        append(" role=").append(role)
        width?.let { append(" width=").append(it) }
        append(" sealed=").append(sealed)
        append(" cache=").append(if (cacheHit) "hit" else "miss")
        attempts.forEachIndexed { index, attempt ->
            append(" | try").append(index + 1)
            append(attempt.mint?.let { " mint=${it.ms()}" } ?: " url=stored")
            attempt.host?.let { append(" host=").append(it) }
            attempt.ttfb?.let { append(" ttfb=").append(it.ms()) }
            attempt.transfer?.let { append(" transfer=").append(it.ms()) }
            attempt.status?.let { append(" status=").append(it) }
            attempt.bytes?.let { append(" bytes=").append(it) }
            attempt.xCache?.let { append(" x-cache=\"").append(it).append('"') }
            attempt.pop?.let { append(" pop=").append(it) }
            attempt.error?.let { append(" error=").append(it) }
        }
        decrypt?.let { append(" | decrypt=").append(it.ms()) }
    }

    private fun Duration.ms() = "${inWholeMilliseconds}ms"
}

/**
 * A name for this failure that is safe to log: class names and, for a sealed photo, the open
 * failure. Never the message, which for a network or `GetBlobs` error can carry the request.
 */
internal fun Throwable.failureName(): String = when (this) {
    is ChatPhotoUnavailable.Open -> "Open($reason)"
    is ChatPhotoUnavailable -> cause?.failureName() ?: className()
    else -> cause?.let { "${className()}(${it.className()})" } ?: className()
}

private fun Throwable.className(): String = this::class.simpleName ?: "Throwable"
