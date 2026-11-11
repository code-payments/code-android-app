package com.flipcash.app.puppet

import com.flipcash.app.puppet.chats.CHATS_ROUTE
import com.flipcash.app.puppet.chats.ChatListFixture
import com.flipcash.app.puppet.chats.chatsSession
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.libs.puppet.DEFAULT_SEED
import com.flipcash.libs.puppet.PuppetHost
import com.getcode.opencode.model.financial.TokenWithBalance

/**
 * The Flipcash app's Puppet host: every hosted screen, keyed by the route `launch` takes. A new
 * root screen adds its route here; a screen reached by a push adds its node to [HostedRoutes].
 *
 * Every session runs on [dispatchers], and [settle] runs before each response. [chatsFixture]
 * builds the Chats fixture for each launch of [CHATS_ROUTE] from the launched seed's balances;
 * tests pass one to keep a handle on it. [SEEDS] names every seed a launch can ask for.
 * The network guard is process-wide, so a session never inherits blocked calls from an earlier
 * one in the same JVM.
 */
fun flipcashHost(
    dispatchers: DispatcherProvider,
    settle: suspend () -> Unit,
    chatsFixture: (balances: List<TokenWithBalance>) -> ChatListFixture = { ChatListFixture(dispatchers, balances = it) },
): PuppetHost {
    NetworkGuard.install()
    return PuppetHost(
        routes = mapOf(
            CHATS_ROUTE to { stack, seed ->
                NetworkGuard.drain()
                val fixture = chatsFixture(SEEDS.getValue(seed))
                chatsSession(fixture, HostedRoutes(fixture, stack)::push, settle)
            },
        ),
        violations = NetworkGuard::drain,
        seeds = SEEDS.keys.toList(),
    )
}

/**
 * The seeds a launch can name, the default first: the chats are the same in each, the balances
 * differ. The shared Chats README lists what each one holds.
 */
val SEEDS: Map<String, List<TokenWithBalance>> = mapOf(
    DEFAULT_SEED to ChatListFixture.dollarsOnly,
    "community-holder" to ChatListFixture.holdingCommunity,
)
