package com.flipcash.app.payments

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.Mint

enum class PaymentAction { Buy, Pay }

sealed interface PurchaseMethod {
    data object CoinbaseOnRamp : PurchaseMethod
    data class CashReserves(val balance: LocalFiat) : PurchaseMethod
    data object PhantomWallet : PurchaseMethod
}

data class PurchaseMethodMetadata(
    val mint: Mint? = null,
    val purchaseAmount: Fiat? = null,
    val paymentAction: PaymentAction = PaymentAction.Buy
)

data class PurchaseMethodSelection(
    val method: PurchaseMethod,
    val metadata: PurchaseMethodMetadata,
)
