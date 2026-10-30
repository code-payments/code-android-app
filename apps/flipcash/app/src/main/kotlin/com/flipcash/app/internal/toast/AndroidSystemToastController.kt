package com.flipcash.app.internal.toast

import android.content.Context
import android.widget.Toast
import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.core.toast.SystemToastController
import com.getcode.ui.components.toast.FloatingToastHostState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shows the app's toasts on the floating toast host while the app's UI is up, and as a system
 * toast only when no host is attached (the app is in the background).
 *
 * A toast raised as a sheet closes waits for the sheet to clear, since a covered host drops it.
 */
internal class AndroidSystemToastController(
    private val context: Context,
) : SystemToastController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var host: FloatingToastHostState? = null
    private var pending: Job? = null
    private var activeToast: Toast? = null

    /** Called by the app's root while its floating toast host is composed. */
    fun attach(host: FloatingToastHostState) {
        this.host = host
    }

    fun detach(host: FloatingToastHostState) {
        if (this.host === host) this.host = null
    }

    override fun showToast(message: String, replacePrevious: Boolean) {
        show(message, SnackbarDuration.Long, Toast.LENGTH_LONG)
    }

    override fun showToast(messageRes: Int, vararg args: Any, replacePrevious: Boolean) {
        show(context.getString(messageRes, *args), SnackbarDuration.Short, Toast.LENGTH_SHORT)
    }

    override fun showQuantityToast(pluralRes: Int, quantity: Int, vararg args: Any, replacePrevious: Boolean) {
        show(context.resources.getQuantityString(pluralRes, quantity, *args), SnackbarDuration.Short, Toast.LENGTH_SHORT)
    }

    // The floating host shows one toast at a time and a new one replaces the last, so
    // replacePrevious holds either way.
    private fun show(message: String, duration: SnackbarDuration, systemLength: Int) {
        pending?.cancel()
        pending = scope.launch {
            val host = host
            val uncovered = host != null &&
                withTimeoutOrNull(COVER_WAIT_MILLIS) { snapshotFlow { host.isCovered }.first { !it } } != null
            if (host != null && uncovered) {
                host.show(message = message, duration = duration)
            } else {
                activeToast?.cancel()
                activeToast = Toast.makeText(context, message, systemLength).also { it.show() }
            }
        }
    }

    private companion object {
        // Long enough for a sheet's exit, short enough that the toast still reads as its result.
        const val COVER_WAIT_MILLIS = 1_000L
    }
}

/** The controller the app root attaches its floating toast host to. Null in previews and tests. */
internal val LocalAndroidSystemToastController = staticCompositionLocalOf<AndroidSystemToastController?> { null }
