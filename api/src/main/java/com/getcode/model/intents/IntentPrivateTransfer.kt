package com.getcode.model.intents

import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.Fee
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.SocialUser
import com.getcode.model.chat.ChatIdV2
import com.getcode.model.chat.Platform
import com.getcode.model.intents.actions.ActionFeePayment
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toPublicKey
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import timber.log.Timber

sealed interface PrivateTransferMetadata {
    data class Tip(val socialUser: SocialUser): PrivateTransferMetadata
    data class Chat(val socialUser: SocialUser): PrivateTransferMetadata
}

class IntentPrivateTransfer(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val destination: PublicKey,
    // Amount requested to transfer
    private val grossAmount: KinAmount,
    // Amount after fees are paid
    private val netAmount: KinAmount,
    private val fee: Kin,
    private val additionalFees: List<Fee>,
    private val isWithdrawal: Boolean,
    private val metadata: PrivateTransferMetadata?,
    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPrivatePayment(
                TransactionService.SendPrivatePaymentMetadata.newBuilder().apply {
                    setDestination(this@IntentPrivateTransfer.destination.bytes.toSolanaAccount())
                    setIsWithdrawal(this@IntentPrivateTransfer.isWithdrawal)
                    setExchangeData(
                        TransactionService.ExchangeData.newBuilder()
                            .setQuarks(grossAmount.kin.quarks)
                            .setCurrency(grossAmount.rate.currency.name.lowercase())
                            .setExchangeRate(grossAmount.rate.fx)
                            .setNativeAmount(grossAmount.fiat)
                    )

                    when (metadata) {
                        is PrivateTransferMetadata.Chat -> {
                            setIsChat(true)
                            setChatId(ChatIdV2.newBuilder()
                                .setValue(metadata.socialUser.chatId.toByteString())
                            )
                        }
                        is PrivateTransferMetadata.Tip -> {
                            setIsTip(true)
                            setTippedUser(TransactionService.TippedUser.newBuilder()
                                .setPlatformValue(when (Platform.named(metadata.socialUser.platform)) {
                                    Platform.Unknown -> ChatService.Platform.UNKNOWN_PLATFORM_VALUE
                                    Platform.Twitter -> ChatService.Platform.TWITTER_VALUE
                                })
                                .setUsername(metadata.socialUser.username)
                            )
                        }
                        null -> Unit
                    }
                }
            )
            .build()
    }

    companion object {
        fun newInstance(
            rendezvousKey: PublicKey,
            organizer: Organizer,
            destination: PublicKey,
            amount: KinAmount,
            fee: Kin,
            additionalFees: List<Fee>,
            isWithdrawal: Boolean,
            metadata: PrivateTransferMetadata?,
        ): IntentPrivateTransfer {
            if (fee > amount.kin) {
                throw IntentPrivateTransferException.InvalidFeeException()
            }

            // Compute all the fees that will be
            // paid out of this transaction
            val concreteFees = additionalFees.map {
                val _fee = amount.kin.calculateFee(it.bps)
                _fee to it.destination
            }

            var netKin = amount.kin - fee

            // Apply the fee to the gross amount
            concreteFees.onEach { (fee, destination) ->
                netKin -= fee
            }

            val netAmount = KinAmount.newInstance(kin = netKin, rate = amount.rate)

            val currentTray = organizer.tray.copy()
            val startBalance = currentTray.availableBalance

            // 1. Move all funds from bucket accounts into the
            // outgoing account and prepare to transfer

            val transfers = currentTray.transfer(amount = amount.kin).map { transfer ->
                val sourceCluster = currentTray.cluster(transfer.from)

                // If the transfer is to another bucket, it's an internal
                // exchange. Otherwise, it is considered a transfer.
                if (transfer.to is AccountType.Bucket) {
                    ActionTransfer.newInstance(
                        kind = ActionTransfer.Kind.TempPrivacyExchange,
                        intentId = rendezvousKey,
                        amount = transfer.kin,
                        source = sourceCluster,
                        destination = currentTray.slot((transfer.to as AccountType.Bucket).type).getCluster().vaultPublicKey
                    )
                } else {
                    ActionTransfer.newInstance(
                        kind = ActionTransfer.Kind.TempPrivacyTransfer,
                        intentId = rendezvousKey,
                        amount = transfer.kin,
                        source = sourceCluster,
                        destination = currentTray.outgoing.getCluster().vaultPublicKey
                    )
                }
            }

            val feePayments = mutableListOf<ActionFeePayment>()

            // Code Fee
            if (fee > 0) {
                feePayments.add(
                    ActionFeePayment.newInstance(
                        kind = ActionFeePayment.Kind.Code,
                        cluster = currentTray.outgoing.getCluster(),
                        amount = fee
                    )
                )
            }

            concreteFees.onEach { (feeAmount, destination) ->
                feePayments.add(
                    ActionFeePayment.newInstance(
                        kind = ActionFeePayment.Kind.ThirdParty(destination),
                        cluster = currentTray.outgoing.getCluster(),
                        amount = feeAmount,
                    )
                )
            }

            // 2. Transfer all collected funds from the temp
            // outgoing account to the destination account

            val outgoing = ActionWithdraw.newInstance(
                kind = ActionWithdraw.Kind.NoPrivacyWithdraw(netAmount.kin),
                cluster = currentTray.outgoing.getCluster(),
                destination = destination,
                metadata = metadata
            )

            // 3. Redistribute the funds to optimize for a
            // subsequent payment out of the buckets

            val redistributes = currentTray.redistribute().map { exchange ->
                ActionTransfer.newInstance(
                    kind = ActionTransfer.Kind.TempPrivacyExchange,
                    intentId = rendezvousKey,
                    amount = exchange.kin,
                    source = currentTray.cluster(exchange.from),
                    destination = currentTray.cluster(exchange.to!!).vaultPublicKey
                    // Exchanges always provide destination accounts
                )
            }

            // 4. Rotate the outgoing account

            currentTray.incrementOutgoing()
            val newOutgoing = currentTray.outgoing

            val rotation = listOf(
                ActionOpenAccount.newInstance(
                    owner = organizer.tray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey(),
                    type = AccountType.Outgoing,
                    accountCluster = newOutgoing.getCluster()
                ),
                ActionWithdraw.newInstance(
                    kind = ActionWithdraw.Kind.CloseDormantAccount(AccountType.Outgoing),
                    cluster = newOutgoing.getCluster(),
                    destination = currentTray.owner.getCluster().vaultPublicKey
                )
            )

            val endBalance = currentTray.availableBalance

            if (startBalance - endBalance != amount.kin)  {
                Timber.e(
                    "Expected: ${amount.kin}; actual = ${startBalance - endBalance}; " +
                            "difference: ${startBalance.quarks - currentTray.availableBalance.quarks - amount.kin.quarks}"
                )
                throw IntentPrivateTransferException.BalanceMismatchException()
            }

            val group = ActionGroup()

            group.actions += transfers
            group.actions += listOf(
                *feePayments.toTypedArray(),
                outgoing,
                *redistributes.toTypedArray(),
                *rotation.toTypedArray()
            )

            return IntentPrivateTransfer(
                id = rendezvousKey,
                organizer = organizer,
                destination = destination,
                grossAmount = amount,
                netAmount = netAmount,
                fee = fee,
                additionalFees = additionalFees,
                isWithdrawal = isWithdrawal,
                metadata = metadata,
                actionGroup = group,
                resultTray = currentTray,
            )

        }
    }
}

sealed class IntentPrivateTransferException: Exception() {
    class BalanceMismatchException: IntentPrivateTransferException()
    class InvalidFeeException: IntentPrivateTransferException()
}