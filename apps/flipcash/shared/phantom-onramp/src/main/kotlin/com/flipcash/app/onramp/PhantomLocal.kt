package com.flipcash.app.onramp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.onramp.internal.PhantomDepositState
import com.getcode.solana.rpc.RpcConfig

@Composable
fun rememberPhantomDepositState(
    rpcConfig: RpcConfig): PhantomDepositState {
    val userManager = LocalUserManager.currentOrThrow
    val scope = rememberCoroutineScope()
    return remember(userManager, scope, rpcConfig) {
        PhantomDepositState(
            userManager,
            scope,
            rpcConfig.rpcUrl,
            rpcConfig.networkDriver
        )
    }
}

val LocalPhantomDepositState =
    compositionLocalOf<PhantomDepositState> { throw IllegalStateException() }