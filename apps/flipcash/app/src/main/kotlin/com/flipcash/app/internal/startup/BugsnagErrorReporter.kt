package com.flipcash.app.internal.startup

import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Severity
import com.getcode.opencode.model.core.errors.ValidationException
import com.getcode.utils.CodeServerError
import com.getcode.utils.ErrorReporter

class BugsnagErrorReporter : ErrorReporter {
    override fun report(error: Throwable, cause: Throwable, isNotifiable: Boolean) {
        if (!Bugsnag.isStarted()) return
        Bugsnag.notify(error) { event ->
            // WARNING = needs active attention (also drives the Slack filter);
            // INFO = recorded for reference only, excluded from Slack.
            event.severity = if (isNotifiable) Severity.WARNING else Severity.INFO
            groupingHashFor(error, cause)?.let { event.groupingHash = it }
            true
        }
    }

    internal companion object {
        /**
         * Bugsnag groups on the top stack frame, which for a [CodeServerError] is the line that
         * built it (`SubmitIntentError.typed` and friends). That line moves between releases, so
         * each release would open a new group for the same server result. Group on the cause
         * instead:
         *
         * - [ValidationException]: field path and rule (see [ValidationException.groupingHash]).
         * - Any other [CodeServerError]: class name plus its [normalized][normalizeMessage]
         *   message. The message carries the server's reason, and one class can carry several
         *   (`SubmitIntentError.StaleState` has six, some notifiable and some not), so the class
         *   name alone would merge them. The class name is safe to hash because
         *   `proguard-rules.pro` keeps the names of public `Throwable` subclasses.
         *
         * The reported [error] is checked before [cause], matching the exception Bugsnag shows.
         * Anything else returns null and keeps Bugsnag's stack grouping, where the stack is the
         * signal.
         */
        fun groupingHashFor(error: Throwable, cause: Throwable): String? {
            ((error as? ValidationException) ?: (cause as? ValidationException))
                ?.let { return it.groupingHash }

            val serverError = (error as? CodeServerError) ?: (cause as? CodeServerError)
                ?: return null
            // `message` is CodeServerError's own property, without the "CodeServerError: "
            // prefix it passes to Throwable.
            return "${serverError.javaClass.name}:${normalizeMessage(serverError.message)}"
        }

        /**
         * Replaces the values servers embed in reason strings (amounts, account addresses, list
         * indexes) with placeholders, so two failures with the same reason share a group.
         */
        fun normalizeMessage(message: String?): String =
            message.orEmpty()
                .replace(ListIndex, "")
                .replace(Base58Token, "<address>")
                .replace(Number, "<n>")

        private val ListIndex = Regex("""\[\d+]""")

        // Solana addresses and signatures: 32+ base58 characters. Requiring a letter keeps a
        // long amount in the Number bucket, so an amount's length can't change the hash.
        private val Base58Token =
            Regex("""\b(?=[1-9A-HJ-NP-Za-km-z]*[A-HJ-NP-Za-km-z])[1-9A-HJ-NP-Za-km-z]{32,}\b""")

        private val Number = Regex("""\d+(?:\.\d+)?""")
    }
}
