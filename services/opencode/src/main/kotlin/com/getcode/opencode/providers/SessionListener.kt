package com.getcode.opencode.providers

import com.getcode.opencode.model.accounts.AccountCluster

/**
 * Listener for session lifecycle events.
 *
 * Implementations are notified when the user's session state changes,
 * allowing app-layer components to react to login/logout events
 * dispatched internally within the SDK.
 *
 * Register implementations via Hilt multibinding:
 * ```
 * @Binds @IntoSet
 * abstract fun bindMyListener(impl: MyImpl): SessionListener
 * ```
 */
interface SessionListener {
    suspend fun onUserLoggedIn(cluster: AccountCluster)
    suspend fun onBalanceUpdateRequested()
}