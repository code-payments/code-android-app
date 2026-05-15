package com.flipcash.app.onramp

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletConnection
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Signature

sealed interface ExternalWalletOnRampState {
    data object Idle : ExternalWalletOnRampState

    data class Started(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
    ) : ExternalWalletOnRampState

    data class Connecting(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
    ) : ExternalWalletOnRampState

    data class Connected(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
        val connection: ExternalWalletConnection,
        val encryptionPublicKey: List<Byte>,
    ) : ExternalWalletOnRampState

    data class Signing(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
        val connection: ExternalWalletConnection,
        val encryptionPublicKey: List<Byte>,
        val unsignedTransaction: List<Byte>,
        val amount: VerifiedFiat,
        val fee: LocalFiat,
        val token: Token?,
        val swapId: SwapId?,
    ) : ExternalWalletOnRampState

    data class Signed(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
        val connection: ExternalWalletConnection,
        val signedTransaction: String,
        val signature: Signature,
        val amount: VerifiedFiat,
        val fee: LocalFiat,
        val token: Token?,
        val swapId: SwapId?,
    ) : ExternalWalletOnRampState

    data class Transacting(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
        val token: Token?,
        val swapId: SwapId?,
    ) : ExternalWalletOnRampState

    data class Transacted(
        val origin: AppRoute,
        val provider: OnRampProvider.UsesDeeplinks,
        val token: Token?,
        val swapId: SwapId?,
    ) : ExternalWalletOnRampState

    data class Failed(
        val error: DeeplinkOnRampError,
        val origin: AppRoute?,
        val provider: OnRampProvider.UsesDeeplinks?,
    ) : ExternalWalletOnRampState
}
