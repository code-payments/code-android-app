package com.flipcash.app.scanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Composable
fun rememberSheetAutoResign(resignAfter: Duration = 1.minutes): SheetLifecycleHandler {
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigator = LocalCodeNavigator.current
    val sheetLifecycleHandler = remember(lifecycleOwner, navigator, resignAfter) {
        SheetLifecycleHandler(navigator, lifecycleOwner, resignAfter)
    }

    return sheetLifecycleHandler
}

class SheetLifecycleHandler(
    private val navigator: CodeNavigator,
    private val lifecycleOwner: LifecycleOwner,
    private val timeout: Duration = 3.minutes
): LifecycleEventObserver {
    private var backgroundTimer: Timer? = null

    fun handle() {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private fun onAppBackgrounded() {
        // Cancel any existing timer
        backgroundTimer?.cancel()

        // Create new timer for 3 minutes
        backgroundTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    trace(
                        tag = "Sheets",
                        message = "Resigning sheet after ${timeout.inWholeSeconds} seconds",
                        type = TraceType.Process
                    )
                    navigator.hide()
                }
            }, timeout.inWholeMilliseconds)
        }
    }

    private fun onAppForegrounded() {
        // Cancel timer when app comes back to foreground
        backgroundTimer?.cancel()
        backgroundTimer = null
    }

    private fun deinit() {
        // Clean up timer and observer
        backgroundTimer?.cancel()
        backgroundTimer = null
        lifecycleOwner.lifecycle.removeObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME ->  onAppForegrounded()
            Lifecycle.Event.ON_STOP -> onAppBackgrounded()
            Lifecycle.Event.ON_DESTROY -> deinit()
            else -> Unit
        }
    }
}
