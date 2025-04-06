package com.getcode.opencode.internal.intents

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.core.LocalFiat
import com.getcode.solana.keys.PublicKey

class IntentTransfer(
    override val id: PublicKey,
    private val sourceCluster: AccountCluster,
    private val destination: PublicKey,
    private val amount: LocalFiat,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPublicPayment(
                TransactionService.SendPublicPaymentMetadata.newBuilder()
                    .setSource(sourceCluster.vaultPublicKey.asSolanaAccountId())
                    .setDestination(destination.asSolanaAccountId())
                    .setIsWithdrawal(true)
                    .setExchangeData(
                        TransactionService.ExchangeData.newBuilder()
                            .setQuarks(amount.fiat.quarks.toLong())
                            .setCurrency(amount.rate.currency.name.lowercase())
                            .setExchangeRate(amount.rate.fx)
                            .setNativeAmount(amount.fiat.doubleValue)
                    )
            )
            .build()
    }

    sealed interface Destination {
        data class Local(val accountType: Model.AccountType): Destination
        data class External(val publicKey: PublicKey): Destination
    }
}

sealed class IntentPublicTransferException: Exception() {
    class BalanceMismatchException: IntentPublicTransferException()
}