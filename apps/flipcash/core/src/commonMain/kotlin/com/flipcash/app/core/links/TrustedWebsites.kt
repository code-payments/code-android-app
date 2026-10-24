package com.flipcash.app.core.links

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A host the user chose not to be warned about again, from the "You're Leaving Flipcash" prompt.
 * [host] is exactly what the prompt showed (see [LinkDestination.External.host]); [addedAtMillis] is
 * epoch milliseconds.
 */
data class TrustedWebsite(val host: String, val addedAtMillis: Long)

/**
 * The hosts that skip the external-link warning. One list for the device, shared by every account
 * on it and kept across Log Out and Switch Accounts; it lives only on this device.
 *
 * Matching is exact: a host here covers that host and nothing under it.
 */
interface TrustedWebsites {
    /** Every trusted host, newest first. Current as of the last write, so a tap can read it. */
    val websites: StateFlow<List<TrustedWebsite>>

    fun trust(host: String)

    fun remove(host: String)
}

/** No hosts, and nothing kept. For previews and tests that don't provide the real list. */
object NoTrustedWebsites : TrustedWebsites {
    override val websites: StateFlow<List<TrustedWebsite>> = MutableStateFlow(emptyList())

    override fun trust(host: String) = Unit

    override fun remove(host: String) = Unit
}

val LocalTrustedWebsites = staticCompositionLocalOf<TrustedWebsites> { NoTrustedWebsites }
