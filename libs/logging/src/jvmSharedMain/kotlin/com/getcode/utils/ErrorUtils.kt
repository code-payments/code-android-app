package com.getcode.utils

import com.getcode.manager.TopBarManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

object ErrorUtils {
    private var isDisplayErrors = false
    private val reporters = mutableListOf<ErrorReporter>()
    private val classifiers = mutableListOf<ErrorClassifier>()

    fun addReporter(reporter: ErrorReporter) { reporters.add(reporter) }
    fun removeReporter(reporter: ErrorReporter) { reporters.remove(reporter) }

    fun addClassifier(classifier: ErrorClassifier) { if (classifier !in classifiers) classifiers.add(classifier) }
    fun removeClassifier(classifier: ErrorClassifier) { classifiers.remove(classifier) }

    fun setDisplayErrors(isDisplayErrors: Boolean) {
        ErrorUtils.isDisplayErrors = isDisplayErrors
    }

    private val ignoredErrors = listOf(
        UnknownHostException::class,
        TimeoutException::class,
        TimeoutCancellationException::class,
        CancellationException::class,
        ConnectException::class,
        SSLException::class,
        SocketException::class,
    )

    fun handleError(throwable: Throwable) {
        if (throwable is CancellationException) return
        if (isNetworkError(throwable)) return
        if (isGmsTransientError(throwable)) return

        val throwableCause: Throwable =
            if (throwable.cause != null && throwable is CodeServerError)
                throwable.cause ?: throwable
            else throwable

        if (classify(throwableCause) == ErrorClass.Ignore) return

        PlatformLog.error(throwable)

        if (isLogOnly(throwable)) return

        if ((isDisplayErrors && !isSuppressibleError(throwable))) {

            TopBarManager.showMessage(
                "[Error] ${throwableCause.javaClass.simpleName}",
                "${throwableCause.message}",
                TopBarManager.TopBarMessageType.NEUTRAL
            )
        }

        if (BuildFlags.notifyErrors && shouldReport(throwable, throwableCause)) {
            val isNotifiable = when {
                throwable is ConditionallyNotifiable -> throwable.isNotifiable
                throwableCause is ConditionallyNotifiable -> throwableCause.isNotifiable
                else -> throwableCause !is CodeServerError
            }

            reporters.forEach { it.report(throwable, throwableCause, isNotifiable) }
        }
    }

    /**
     * Whether [throwable] is worth handing to the reporters at all. [UnreportedError] marks an
     * expected server result the caller already handles, so it stays in the log without opening a
     * Bugsnag error group.
     */
    internal fun shouldReport(throwable: Throwable, cause: Throwable): Boolean =
        throwable !is UnreportedError &&
                cause !is UnreportedError &&
                ignoredErrors.none { it.isInstance(throwable) } &&
                ignoredErrors.none { it.isInstance(cause) }

    private fun isNetworkError(throwable: Throwable): Boolean =
        throwable is TimeoutException ||
                throwable.cause is TimeoutException ||
                throwable is UnknownHostException ||
                throwable.cause is UnknownHostException ||
                throwable is SSLException ||
                throwable.cause is SSLException ||
                throwable is SocketException ||
                throwable.cause is SocketException

    /**
     * Error strings Google Play Services returns for a failed FCM registration. They describe the
     * device's GMS state or the FCM backend, not app code, so they stay out of Bugsnag. Firebase
     * wraps them as `IOException("FCM Registration failed!")` -> `ExecutionException` ->
     * `IOException(<code>)`, which is why the cause chain is walked.
     */
    private val gmsTransientMessages = setOf(
        "SERVICE_NOT_AVAILABLE",
        "FIS_AUTH_ERROR",
        "MISSING_INSTANCEID_SERVICE",
        "TOO_MANY_REGISTRATIONS",
        "AUTHENTICATION_FAILED",
        "INTERNAL_SERVER_ERROR",
        "InternalServerError",
        "PHONE_REGISTRATION_ERROR",
    )

    internal fun isGmsTransientError(throwable: Throwable): Boolean =
        generateSequence(throwable) { it.cause }
            .any { it is java.io.IOException && it.message in gmsTransientMessages }

    private fun classify(throwable: Throwable): ErrorClass? =
        classifiers.firstNotNullOfOrNull { it.classify(throwable) }

    /** A classified error, or one wrapping a classified cause, is logged but never shown or reported. */
    private fun isLogOnly(throwable: Throwable): Boolean =
        classify(throwable) != null || throwable.cause?.let(::classify) != null

    private fun isSuppressibleError(throwable: Throwable): Boolean =
        isPlatformSqlException(throwable) || throwable is SuppressibleException || throwable is TimeoutCancellationException
}

fun Throwable.isNetworkError(): Boolean =
    this is UnknownHostException || cause is UnknownHostException ||
    this is ConnectException || cause is ConnectException ||
    this is TimeoutException || cause is TimeoutException ||
    this is SSLException || cause is SSLException ||
    this is SocketException || cause is SocketException

data class SuppressibleException(override val message: String, override val cause: Throwable? = null) : Throwable(message, cause) {
    constructor(cause: Throwable) : this(cause.message.orEmpty(), cause)
}