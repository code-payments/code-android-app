package com.flipcash.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.flipcash.app.devicelink.DesktopLinkHost
import com.flipcash.app.devicelink.ui.DesktopLinkScreen
import com.flipcash.app.theme.FlipcashTheme
import com.getcode.theme.CodeTheme
import java.net.Inet4Address
import java.net.NetworkInterface

/** The main window when signed in; otherwise the link screen, which stores the phone's seed and signs in. */
@Composable
fun DesktopApp(graph: DesktopGraph, signedIn: Boolean) {
    var linked by remember { mutableStateOf(signedIn) }
    if (linked) {
        DesktopMainContent(graph)
    } else {
        DesktopLinkContent(graph, onSignedIn = { linked = true })
    }
}

@Composable
fun DesktopLinkContent(graph: DesktopGraph, onSignedIn: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<DesktopLinkHost.State?>(null) }
    LaunchedEffect(attempt) {
        val host = lanAddress()
        if (host == null) {
            state = DesktopLinkHost.State.Failed("This computer isn't on a local network")
            return@LaunchedEffect
        }
        DesktopLinkHost(host).link().collect { next ->
            state = next
            if (next is DesktopLinkHost.State.Linked) {
                graph.seedVault.write(next.entropyB64)
                graph.signIn(next.entropyB64)
                    .onSuccess { onSignedIn() }
                    .onFailure { state = DesktopLinkHost.State.Failed("Linked, but sign-in failed: ${it.message}") }
            }
        }
    }
    FlipcashTheme {
        Box(Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
            DesktopLinkScreen(state, onRetry = { attempt++ })
        }
    }
}

/**
 * The address the phone dials: the first site-local IPv4 address on an interface that is up. The
 * one piece of linking that stays in the desktop app, since the phone never needs its own address.
 */
internal fun lanAddress(): String? = NetworkInterface.getNetworkInterfaces().asSequence()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .flatMap { it.inetAddresses.asSequence() }
    .filterIsInstance<Inet4Address>()
    .firstOrNull { it.isSiteLocalAddress }
    ?.hostAddress
