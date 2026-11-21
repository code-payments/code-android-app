package com.flipcash.shared.payments.request

import com.getcode.opencode.model.core.errors.SubmitIntentError
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long one wait on the pay path may run while the sheet can't be dismissed (group-collections
 * spec, pay step 4). For the payment submission it is the point where the outcome becomes unknown.
 */
internal val PAY_PATH_DEADLINE: Duration = 60.seconds

/**
 * The submission got no answer within [PAY_PATH_DEADLINE], or its stream timed out first: the
 * payment may or may not have landed. The sheet unlocks with "Payment Not Confirmed", and the next
 * tap asks for the intent id again before paying, so it can't pay twice.
 */
class PaymentNotConfirmed(cause: Throwable? = null) : Exception("payment not confirmed", cause)

/**
 * Runs [call], failing with a [TimeoutException] naming [what] when it takes longer than
 * [deadline]. A call that catches its own cancellation and returns that as a failure counts as
 * timed out too.
 */
internal suspend fun <T> bounded(
    what: String,
    deadline: Duration = PAY_PATH_DEADLINE,
    call: suspend () -> Result<T>,
): Result<T> {
    val result = withTimeoutOrNull(deadline) { call() }
    if (result == null || result.exceptionOrNull() is TimeoutCancellationException) {
        return Result.failure(TimeoutException("$what took longer than $deadline"))
    }
    return result
}

/** Runs [call] for at most [deadline]; false when it ran out. For waits whose outcome is ignored. */
internal suspend fun boundedOrSkip(deadline: Duration = PAY_PATH_DEADLINE, call: suspend () -> Unit): Boolean =
    withTimeoutOrNull(deadline) { call() } != null

/**
 * Runs a payment [submission] for at most [PAY_PATH_DEADLINE]. No answer by then, or a stream that
 * timed out first, fails with [PaymentNotConfirmed], since the payment may have landed. Any other
 * outcome is returned as it is.
 */
internal suspend fun submitWithinDeadline(submission: suspend () -> Result<Unit>): Result<Unit> {
    val result = withTimeoutOrNull(PAY_PATH_DEADLINE) { submission() }
        ?: return Result.failure(PaymentNotConfirmed())
    val error = result.exceptionOrNull()
    return when {
        error is TimeoutCancellationException -> Result.failure(PaymentNotConfirmed())
        error is SubmitIntentError.Other && error.cause is TimeoutException -> Result.failure(PaymentNotConfirmed(error))
        else -> result
    }
}
