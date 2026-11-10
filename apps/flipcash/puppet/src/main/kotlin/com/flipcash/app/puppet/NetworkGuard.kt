package com.flipcash.app.puppet

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Blocks every connection that asks the default [ProxySelector] for a route, and records it.
 *
 * OkHttp and grpc-okhttp both ask before they connect, so this catches a client the fixture
 * didn't replace, which [FixtureGuard] can't see. Raw sockets (ktor's `aSocket`, used by device
 * link) don't ask and get past it; the hosted screens build none.
 *
 * The selector is process-wide. [install] is idempotent, and attempts are reported through the
 * host's `violations` as a `fixture_violation`, like [FixtureGuard]'s.
 */
object NetworkGuard {
    private val attempts = mutableListOf<String>()

    fun install() {
        if (ProxySelector.getDefault() !== Selector) ProxySelector.setDefault(Selector)
    }

    /** The connections blocked since the last drain, oldest first. */
    fun drain(): List<String> = synchronized(attempts) { attempts.toList().also { attempts.clear() } }

    private object Selector : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            synchronized(attempts) { attempts += "network: $uri" }
            throw IOException("puppet blocked a network connection to $uri")
        }

        override fun connectFailed(uri: URI, address: SocketAddress, e: IOException) = Unit
    }
}
