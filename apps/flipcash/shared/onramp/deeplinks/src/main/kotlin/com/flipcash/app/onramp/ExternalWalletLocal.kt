package com.flipcash.app.onramp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.onramp.internal.ExternalWalletDeeplinkState
import com.getcode.solana.rpc.RpcConfig

@Composable
fun rememberExternalWalletState(
    rpcConfig: RpcConfig
): ExternalWalletDeeplinkState {
    val userManager = LocalUserManager.currentOrThrow
    val scope = rememberCoroutineScope()
    return remember(userManager, scope, rpcConfig) {
        ExternalWalletDeeplinkState(
            userManager,
            scope,
            rpcConfig.rpcUrl,
            rpcConfig.networkDriver
        )
    }
}

val LocalExternalWalletState =
    compositionLocalOf<ExternalWalletDeeplinkState> { throw IllegalStateException() }