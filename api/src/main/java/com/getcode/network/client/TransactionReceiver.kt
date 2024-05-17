package com.getcode.network.client

import android.content.Context
import com.getcode.model.Kin
import com.getcode.model.intents.IntentPublicTransfer
import com.getcode.model.intents.IntentReceive
import com.getcode.model.intents.IntentRemoteReceive
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import timber.log.Timber
import javax.inject.Inject

class TransactionReceiver @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository
) {
    fun receiveRemotely(
        amount: Kin,
        organizer: Organizer,
        giftCard: GiftCardAccount,
        isVoiding: Boolean
    ): Completable {
        return Completable.defer {
            transactionRepository.receiveRemotely(
                context, amount, organizer, giftCard, isVoiding
            )
                .map {
                    if (it is IntentRemoteReceive) {
                        setTray(organizer, it.resultTray)
                    }
                }
                .ignoreElement()
        }
    }

    suspend fun receiveRemotelySuspend(
        giftCard: GiftCardAccount,
        amount: Kin,
        organizer: Organizer,
        isVoiding: Boolean
    ) {
        val intent = transactionRepository.receiveRemotely(
            context = context,
            amount = amount,
            organizer = organizer,
            giftCard = giftCard,
            isVoiding = isVoiding
        ).blockingGet()

        if (intent is IntentRemoteReceive) {
            setTray(organizer, intent.resultTray)
        }
    }

    fun receiveFromRelationship(organizer: Organizer, limit: Kin? = null): Kin {
        var receivedTotal = Kin.fromKin(0)

        runCatching loop@{
            organizer.relationshipsLargestFirst().onEach { relationship ->
                Timber.d("Receiving from relationships: domain ${relationship.domain.urlString} balance ${relationship.partialBalance}")

                // Ignore empty relationship accounts
                if (relationship.partialBalance > 0) {
                    val intent = transactionRepository.receiveFromRelationship(
                        relationship = relationship,
                        organizer = organizer
                    ).blockingGet()

                    receivedTotal += relationship.partialBalance

                    if (intent is IntentPublicTransfer) {
                        setTray(organizer, intent.resultTray)
                    }

                    // Bail early if a limit is set
                    if (limit != null && receivedTotal >= limit) {
                        return@loop // break loop
                    }
                }
            }
        }.onFailure {
            ErrorUtils.handleError(it)
            it.printStackTrace()
        }

        return receivedTotal
    }

    fun receiveFromIncoming(organizer: Organizer): Kin {
        val incomingBalance = availableIncomingAmount(organizer)
        return if (incomingBalance <= 0) {
            Kin.fromKin(0)
        } else {
            receiveFromIncoming(
                amount = incomingBalance,
                organizer = organizer
            ).blockingAwait()
            incomingBalance
        }
    }

    fun receiveFromIncomingCompletable(organizer: Organizer): Completable {
        val incomingBalance = availableIncomingAmount(organizer)
        return if (incomingBalance <= 0) {
            Completable.complete()
        } else {
            receiveFromIncoming(
                amount = incomingBalance,
                organizer = organizer
            )
        }
    }

    fun receiveFromIncoming(amount: Kin, organizer: Organizer): Completable {
        trace("receiveFromIncoming $amount", type = TraceType.Silent)
        return transactionRepository.receiveFromIncoming(
            context, amount, organizer
        ).map {
            if (it is IntentReceive) {
                setTray(organizer, it.resultTray)
            }
        }.ignoreElement()
    }

    suspend fun swapIfNeeded(organizer: Organizer) {
        transactionRepository.swapIfNeeded(organizer)
    }

    private fun setTray(organizer: Organizer, tray: Tray) {
        organizer.set(tray)
        balanceRepository.setBalance(organizer.availableBalance.toKinTruncatingLong().toDouble())
    }

    fun availableIncomingAmount(organizer: Organizer): Kin {
        return organizer.availableIncomingBalance.toKinTruncating()
    }
}