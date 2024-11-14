package com.getcode.model.intents

import com.codeinc.gen.transaction.v2.CodeTransactionService
import com.codeinc.gen.transaction.v2.CodeTransactionService.ExtendedPaymentMetadata
import com.getcode.model.KinAmount
import com.getcode.model.generate
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.network.repository.toSolanaAccount
import com.getcode.services.model.ExtendedMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import com.getcode.utils.toByteString

class IntentPublicPayment(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val sourceCluster: AccountCluster,
    private val destination: PublicKey,
    private val amount: KinAmount,
    val resultTray: Tray,
    val metadata: ExtendedMetadata? = null,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): CodeTransactionService.Metadata {
        val pubPayBuilder = CodeTransactionService.SendPublicPaymentMetadata.newBuilder()
            .setSource(sourceCluster.vaultPublicKey.bytes.toSolanaAccount())
            .setDestination(destination.bytes.toSolanaAccount())
            .setIsWithdrawal(true)
            .setExchangeData(
                CodeTransactionService.ExchangeData.newBuilder()
                    .setQuarks(amount.kin.quarks)
                    .setCurrency(amount.rate.currency.name.lowercase())
                    .setExchangeRate(amount.rate.fx)
                    .setNativeAmount(amount.fiat)
            )

        if (metadata != null) {
            when (metadata) {
                is ExtendedMetadata.Any -> pubPayBuilder.setExtendedMetadata(
                    ExtendedPaymentMetadata.newBuilder()
                        .setValue(com.google.protobuf.Any.newBuilder()
                            .setTypeUrl(metadata.typeUrl)
                            .setValue(metadata.data.toByteString())))
                else -> Unit
            }
        }

        return CodeTransactionService.Metadata.newBuilder()
            .setSendPublicPayment(pubPayBuilder.build())
            .build()
    }

    companion object {
        fun newInstance(
            organizer: Organizer,
            source: AccountType,
            destination: PublicKey,
            amount: KinAmount,
            extendedMetadata: ExtendedMetadata? = null,
        ): IntentPublicPayment {
            val id = PublicKey.generate()
            val currentTray = organizer.tray.copy()
            val sourceCluster = organizer.tray.cluster(source)

            // 1. Transfer all funds in the primary account
            // directly to the destination. This is a public
            // transfer so no buckets involved and no rotation
            // required.

            val transfer = ActionTransfer.newInstance(
                kind = ActionTransfer.Kind.NoPrivacyTransfer,
                intentId = id,
                amount = amount.kin,
                source = sourceCluster,
                destination = destination
            )

            currentTray.decrement(source, kin = amount.kin)

            return IntentPublicPayment(
                id = id,
                organizer = organizer,
                sourceCluster = sourceCluster,
                destination = destination,
                amount = amount,
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                },
                resultTray = currentTray,
                metadata = extendedMetadata
            )
        }
    }
}