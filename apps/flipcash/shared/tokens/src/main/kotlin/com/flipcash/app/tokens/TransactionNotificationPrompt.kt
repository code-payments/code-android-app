package com.flipcash.app.tokens

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces the "notify me when complete" ask that used to live on the Processing screen. Fast
 * completions skip that screen, so after one succeeds the app asks once, ever, whether the user
 * wants to hear about transactions that finish while they are away.
 *
 * Flows call [onTransactionCompletedInPlace]; the app-level host collects [requests], checks the
 * live permission state (which needs a composition) and calls [markShown] when it presents.
 */
@Singleton
class TransactionNotificationPrompt internal constructor(
    private val prefs: SharedPreferences,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val _requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    val hasBeenShown: Boolean get() = prefs.getBoolean(KEY_SHOWN, false)

    /** Asks the host to consider showing the explainer. A no-op once it has been shown. */
    fun onTransactionCompletedInPlace() {
        if (!hasBeenShown) _requests.tryEmit(Unit)
    }

    /** Persists that the explainer was presented. "Not Now" counts; it is never shown again. */
    fun markShown() {
        prefs.edit().putBoolean(KEY_SHOWN, true).apply()
    }

    companion object {
        /** Gives the exiting flow time to leave before the explainer appears over the new screen. */
        const val SETTLE_DELAY_MS = 600L
        private const val PREFS_NAME = "transaction_notification_prompt"
        private const val KEY_SHOWN = "explainer_shown"
    }
}
