package com.flipcash.app.onramp.internal.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.AppRoute
import com.flipcash.features.onramp.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType

sealed interface OnRampProviderDestination {
    data class ExternalWalletConnection(
        val wallet: OnRampProvider.UsesDeeplinks
    ) : OnRampProviderDestination
    data class Screen(val screen: AppRoute) : OnRampProviderDestination
}

data class OnRampProviderItem(
    val provider: OnRampProvider.Defined,
    val destination: OnRampProviderDestination,
    val isLoading: Boolean = false,
) {
    val icon: Painter
        @Composable get() = when (provider) {
            is OnRampProvider.Coinbase -> when (provider.type) {
                OnRampType.Virtual -> painterResource(R.drawable.ic_debit_card)
                OnRampType.PhysicalDebit -> painterResource(R.drawable.ic_debit_card)
                OnRampType.PhysicalCredit -> painterResource(R.drawable.ic_debit_card)
            }

            OnRampProvider.ManualDeposit -> painterResource(R.drawable.ic_wallet)
            OnRampProvider.Phantom -> painterResource(R.drawable.ic_phantom_wallet)
            OnRampProvider.Backpack -> painterResource(R.drawable.ic_backpack_wallet)
            OnRampProvider.Solflare -> painterResource(R.drawable.ic_solflare_wallet)
        }

    val title: String
        @Composable get() = when (provider) {
            is OnRampProvider.Coinbase -> when (provider.type) {
                OnRampType.Virtual -> stringResource(R.string.title_onrampProviderCoinbaseVirtual)
                OnRampType.PhysicalDebit -> stringResource(R.string.title_onrampProviderCoinbaseDebit)
                OnRampType.PhysicalCredit -> stringResource(R.string.title_onrampProviderCoinbaseCredit)
            }

            is OnRampProvider.ManualDeposit -> stringResource(R.string.title_onrampProviderManualDeposit)
            OnRampProvider.Phantom -> stringResource(R.string.title_onrampProviderPhantom)
            OnRampProvider.Backpack -> stringResource(R.string.title_onrampProviderBackpack)
            OnRampProvider.Solflare -> stringResource(R.string.title_onrampProviderSolflare)
        }
}


