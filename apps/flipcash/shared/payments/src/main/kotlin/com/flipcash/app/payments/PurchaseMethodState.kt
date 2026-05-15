package com.flipcash.app.payments

import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.opencode.model.financial.LocalFiat

data class PurchaseMethodState(
    val coinbaseOnRampAvailable: Boolean = false,
    val reservesBalance: LocalFiat = LocalFiat.Zero,
    val preferredProvider: OnRampProvider.Defined? = null,
    val canUseOtherWallets: Boolean = false,
) {
    val hasReserves: Boolean
        get() = reservesBalance.underlyingTokenAmount.valueNonZero()

    val availableMethods: List<PurchaseMethod>
        get() = buildList {
            if (coinbaseOnRampAvailable) add(PurchaseMethod.CoinbaseOnRamp)
            if (hasReserves) add(PurchaseMethod.CashReserves(reservesBalance))
            add(PurchaseMethod.PhantomWallet)
            if (canUseOtherWallets) add(PurchaseMethod.OtherWallet)
        }
}
