package com.getcode.model.intents

import android.util.Log
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.KinAmount
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.*
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import timber.log.Timber

class IntentPublicTransfer(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val destination: PublicKey,
    private val amount: KinAmount,

    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPublicPayment(
                TransactionService.SendPublicPaymentMetadata.newBuilder()
                    .setDestination(destination.bytes.toSolanaAccount())
                    .setIsWithdrawal(true)
                    .setExchangeData(
                        TransactionService.ExchangeData.newBuilder()
                            .setQuarks(amount.kin.quarks)
                            .setCurrency(amount.rate.currency.name.lowercase())
                            .setExchangeRate(amount.rate.fx)
                            .setNativeAmount(amount.fiat)
                    )
            )
            .build()
    }

    companion object {
        fun newInstance(
            organizer: Organizer,
            destination: PublicKey,
            amount: KinAmount,
        ): IntentPublicTransfer {
            val id = PublicKey.generate()
            val currentTray = organizer.tray.copy()
            val startBalance = currentTray.availableBalance.toKinTruncating()

            // 1. Transfer all funds in the primary account
            // directly to the destination. This is a public
            // transfer so no buckets involved and no rotation
            // required.

            val transfer = ActionTransfer.newInstance(
                kind = ActionTransfer.Kind.NoPrivacyTransfer,
                intentId = id,
                amount = amount.kin,
                source = currentTray.owner.getCluster(),
                destination = destination
            )

            currentTray.decrement(AccountType.Primary, kin = amount.kin)

            val endBalance = currentTray.availableBalance.toKinTruncating()

            if (startBalance - endBalance != amount.kin)  {
                Timber.e(
                    "Expected: ${amount.kin}; actual = ${startBalance - endBalance}; " +
                            "difference: ${startBalance.quarks - currentTray.availableBalance.quarks - amount.kin.quarks}"
                )
                throw IntentPublicTransferException.BalanceMismatchException()
            }

            return IntentPublicTransfer(
                id = id,
                organizer = organizer,
                destination = destination,
                amount = amount,
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                },
                resultTray = currentTray,
            )

        }
    }
}

sealed class IntentPublicTransferException: Exception() {
    class BalanceMismatchException: IntentPublicTransferException()
}