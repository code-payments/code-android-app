package com.flipcash.app.payments

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint

enum class PaymentAction { Buy, Pay }

sealed interface PurchaseMethod {
    data object CoinbaseOnRamp : PurchaseMethod
    data class CashReserves(val balance: LocalFiat) : PurchaseMethod
    data object PhantomWallet : PurchaseMethod
    data object OtherWallet: PurchaseMethod
}

data class PurchaseMethodMetadata(
    val mint: Mint? = null,
    val purchaseAmount: Fiat? = null,
    val feeAmount: Fiat? = null,
    val showReserves: Boolean = true,
    val paymentAction: PaymentAction = PaymentAction.Buy,
    val canUseOtherWallets: Boolean = false,
)

data class PurchaseMethodSelection(
    val method: PurchaseMethod,
    val metadata: PurchaseMethodMetadata,
)
