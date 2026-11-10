package com.flipcash.app.puppet

import com.flipcash.app.puppet.chats.CHATS_ROUTE
import com.flipcash.app.puppet.chats.ChatListFixture
import com.flipcash.app.puppet.chats.chatsSession
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.libs.puppet.PuppetHost

/**
 * The Flipcash app's Puppet host: every hosted screen, keyed by the route `launch` takes. A new
 * root screen adds its route here; a screen reached by a push adds its node to [HostedRoutes].
 *
 * Every session runs on [dispatchers], and [settle] runs before each response. [chatsFixture]
 * builds the Chats fixture for each launch of [CHATS_ROUTE]; tests pass one to keep a handle on it.
 * The network guard is process-wide, so a session never inherits blocked calls from an earlier
 * one in the same JVM.
 */
fun flipcashHost(
    dispatchers: DispatcherProvider,
    settle: suspend () -> Unit,
    chatsFixture: () -> ChatListFixture = { ChatListFixture(dispatchers) },
): PuppetHost {
    NetworkGuard.install()
    return PuppetHost(
        routes = mapOf(
            CHATS_ROUTE to { stack ->
                NetworkGuard.drain()
                val fixture = chatsFixture()
                chatsSession(fixture, HostedRoutes(fixture, stack)::push, settle)
            },
        ),
        violations = NetworkGuard::drain,
    )
}
