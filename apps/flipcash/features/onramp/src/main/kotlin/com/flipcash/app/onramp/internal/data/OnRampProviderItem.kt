package com.flipcash.app.onramp.internal.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.features.onramp.R
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.OnRampType

sealed interface OnRampProviderDestination {
    data object PhantomConnection: OnRampProviderDestination
    data class Screen(val screen: NavScreenProvider): OnRampProviderDestination
}
data class OnRampProviderItem(
    val provider: OnRampProvider.Defined,
    val destination: OnRampProviderDestination
) {
    val icon: Painter
        @Composable get() = when (provider) {
            is OnRampProvider.Coinbase -> when (provider.type) {
                OnRampType.Virtual -> painterResource(R.drawable.ic_debit_card)
                OnRampType.PhysicalDebit -> painterResource(R.drawable.ic_debit_card)
                OnRampType.PhysicalCredit -> painterResource(R.drawable.ic_debit_card)
            }

            OnRampProvider.CryptoDeposit -> painterResource(R.drawable.ic_wallet)
            OnRampProvider.Phantom ->  painterResource(R.drawable.ic_phantom)
        }

    val title: String
        @Composable get() = when (provider) {
            is OnRampProvider.Coinbase -> when (provider.type) {
                OnRampType.Virtual -> "Debit Card with Google Pay"
                OnRampType.PhysicalDebit -> "Debit Card"
                OnRampType.PhysicalCredit -> "Credit Card"
            }

            is OnRampProvider.CryptoDeposit -> "Crypto Wallet"
            OnRampProvider.Phantom -> "Phantom"
        }

    val description: String
        @Composable get() = when (provider) {
            is OnRampProvider.Coinbase -> when (provider.type) {
                OnRampType.Virtual -> "Add cash to your wallet from your debit card"
                OnRampType.PhysicalDebit -> "Add cash to your wallet from your debit card"
                OnRampType.PhysicalCredit -> "Add cash to your wallet from your credit card"
            }

            is OnRampProvider.CryptoDeposit -> "Deposit USDC from your crypto wallet"
            OnRampProvider.Phantom -> "Add cash to your wallet from Phantom"
        }
}


