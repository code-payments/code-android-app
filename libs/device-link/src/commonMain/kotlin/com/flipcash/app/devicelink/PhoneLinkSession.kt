package com.flipcash.app.devicelink

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [PhoneLinkController] for callers that can't collect a flow or call a suspend function, which in
 * practice means Swift through SharedCore. It owns its own scope, so call [close] when the screen
 * goes away.
 *
 * The seed comes from the caller at [confirm], not from a store: iOS already holds it. It is kept
 * only until the controller reads it, which happens after the user has confirmed the codes match,
 * so the read-after-confirm order of [PhoneLinkController] still holds.
 *
 * [onState] is called on a background thread; Swift callers hop to the main actor themselves.
 */
class PhoneLinkSession(private val onState: (PhoneLinkController.State) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var entropy: String? = null
    private val controller = PhoneLinkController(scope) { entropy.also { entropy = null } }

    init {
        scope.launch { controller.state.collect { onState(it) } }
    }

    /** Hands a scanned QR payload to the flow. Returns false if it isn't a link invite. */
    fun scanned(text: String): Boolean {
        if (LinkInvite.parse(text) == null) return false
        controller.onScanned(text)
        return true
    }

    /** The user saw the same code on both screens: send [entropyB64] to the desktop. */
    fun confirm(entropyB64: String) {
        entropy = entropyB64
        controller.confirm()
    }

    /** Drops the pending link, if any, and goes back to scanning. */
    fun cancel() = controller.cancel()

    fun close() {
        entropy = null
        scope.cancel()
    }
}
