package com.getcode.network.client

import android.content.Context
import com.getcode.model.Kin
import com.getcode.model.intents.IntentReceive
import com.getcode.model.intents.IntentRemoteReceive
import com.getcode.network.repository.BalanceRepository
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
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

    private var lastReceive: Long = 0L

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

    fun receiveFromIncomingIfRotationRequired(organizer: Organizer): Completable {
        // Server will set this to `true` if the account
        // has more than 1 transaction + other heuristics
        return if (organizer.shouldRotateIncoming) {
            receiveFromIncoming(organizer)
            Completable.complete()
        } else {
            Completable.complete()
        }
    }

    fun receiveFromIncoming(organizer: Organizer): Completable {
        val incomingBalance = organizer.availableIncomingBalance.toKinTruncating()
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
        val time = System.currentTimeMillis()
        val isPastThrottle = time - lastReceive > 1000 * 1 || lastReceive == 0L

        if (!isPastThrottle) return Completable.complete()
        lastReceive = time
        return transactionRepository.receiveFromIncoming(
            context, amount, organizer
        )
            .map { if (it is IntentReceive) {
                setTray(organizer, it.resultTray)
            } }
            .ignoreElement()
    }

    private fun setTray(organizer: Organizer, tray: Tray) {
        organizer.set(tray)
        balanceRepository.setBalance(organizer.availableBalance.toKinTruncatingLong().toDouble())
    }
}