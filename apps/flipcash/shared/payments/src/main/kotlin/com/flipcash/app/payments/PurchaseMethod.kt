package com.flipcash.app.payments

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint

sealed interface PurchaseMethod {
    data object CoinbaseOnRamp : PurchaseMethod
    data class CashReserves(val balance: LocalFiat) : PurchaseMethod
    data object PhantomWallet : PurchaseMethod
}

data class PurchaseMethodMetadata(
    val mint: Mint? = null,
    val token: Token? = null,
    val purchaseAmount: Fiat? = null,
    val isFundingShortfall: Boolean = false,
)

data class PurchaseMethodSelection(
    val method: PurchaseMethod,
    val metadata: PurchaseMethodMetadata,
)
