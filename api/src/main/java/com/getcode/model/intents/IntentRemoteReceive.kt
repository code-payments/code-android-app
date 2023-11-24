package com.getcode.model.intents

import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.Kin
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray

class IntentRemoteReceive(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val giftCard: GiftCardAccount,
    private val amount: Kin,
    private val isVoidingGiftCard: Boolean,

    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setReceivePaymentsPublicly(
                TransactionService.ReceivePaymentsPubliclyMetadata.newBuilder()
                    .setSource(giftCard.cluster.timelockAccounts.vault.publicKey.bytes.toSolanaAccount())
                    .setQuarks(amount.quarks)
                    .setIsRemoteSend(true)
                    .setIsIssuerVoidingGiftCard(isVoidingGiftCard)
            )
            .build()
    }

    companion object {
        fun newInstance(
            context: Context,
            organizer: Organizer,
            giftCard: GiftCardAccount,
            amount: Kin,
            isVoidingGiftCard: Boolean
        ): IntentRemoteReceive {
            val intentId = PublicKey.generate()
            val currentTray = organizer.tray.copy()
            val startBalance = currentTray.availableBalance

            val giftCardWithdraw = ActionWithdraw.newInstance(
                kind = ActionWithdraw.Kind.NoPrivacyWithdraw(amount),
                cluster = giftCard.cluster,
                destination = organizer.incomingVault
            )

            currentTray.increment(AccountType.Incoming, amount)

            val endBalance = currentTray.availableBalance
            if (endBalance - startBalance != amount) {
                throw IntentRemoteReceiveException.BalanceMismatchException()
            }

            return IntentRemoteReceive(
                id = intentId,
                organizer = organizer,
                giftCard = giftCard,
                amount = amount,
                actionGroup = ActionGroup().apply {
                    actions = listOf(giftCardWithdraw)
                },
                resultTray = currentTray,
                isVoidingGiftCard = isVoidingGiftCard
            )
        }
    }

    sealed class IntentRemoteReceiveException : Exception() {
        class BalanceMismatchException : IntentRemoteReceiveException()
    }
}