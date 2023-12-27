package com.getcode.network.repository

import android.annotation.SuppressLint
import android.content.Context
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.AnalyticsManager
import com.getcode.manager.SessionManager
import com.getcode.model.CodePayload
import com.getcode.model.IntentMetadata
import com.getcode.model.KinAmount
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.establishRelationship
import com.getcode.network.client.fetchLimits
import com.getcode.network.client.pollIntentMetadata
import com.getcode.network.client.transfer
import com.getcode.network.client.transferWithResult
import com.getcode.network.exchange.Exchange
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Request(
    val amount: KinAmount,
    val payload: CodePayload,
)

class PaymentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exchange: Exchange,
    private val messagingRepository: MessagingRepository,
    private val client: Client,
    private val analytics: AnalyticsManager,
    private val balanceController: BalanceController,
) {
    fun attemptRequest(payload: CodePayload): Request? {
        val fiat = payload.fiat
        if (fiat == null) {
            Timber.d("payload does not contain Fiat value")
            return null
        }

        val rate = exchange.rateFor(fiat.currency)
        if (rate == null) {
            Timber.d("Unable to determine rate")
            return null
        }

        Timber.d("Rate for ${rate.currency.name}: ${rate.fx}")
        Timber.d("fiat value = ${fiat.amount}")

        val amount = KinAmount.fromFiatAmount(fiat.amount, rate.fx, fiat.currency)

        Timber.d("amount=${amount.fiat}, ${amount.kin}, ${amount.rate}")

        return Request(amount, payload)
    }

    @SuppressLint("CheckResult")
    suspend fun completePayment(amount: KinAmount, rendezvousKey: KeyPair) {
        // 1. ensure we have exchange rates and compute the fees for this transaction
        exchange.fetchRatesIfNeeded()

        var paymentAmount = amount
        return suspendCancellableCoroutine { cont ->
            runCatching {
                val rateUsd = exchange.rateForUsd() ?: throw PaymentError.NoExchangeData()

                val fee = KinAmount.fromFiatAmount(fiat = 0.01, rate = rateUsd)
                Timber.d("Computed fee for transaction=${fee.kin}")


                // 2. Between the time the kin value was computed previously and
                // now, the exchange rates might have changed. Let's recompute the
                // Kin value from the fiat value we had before but using a more
                // current exchange rate
                val newRate = exchange.rateFor(amount.rate.currency)
                    ?: throw PaymentError.ExchangeForCurrencyNotFound()
                paymentAmount = KinAmount.fromFiatAmount(
                    fiat = amount.fiat,
                    rate = newRate
                )

                analytics.recomputed(amount.rate.fx, newRate.fx)
                Timber.d("In: ${amount.rate.fx}, Out:${newRate.fx}")

                // 3. Fetch message metadata for this payload that
                // will tell us where to send the funds.
                val messages = messagingRepository
                    .fetchMessages(rendezvousKey)
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() && it.first().receiveRequest != null }
                    ?: throw PaymentError.MessageForRendezvousNotFound()

                val message = messages.first()
                val receiveRequest = message.receiveRequest!!

                // 4. Establish a relationship if a domain is provided and is verified
                val domain = receiveRequest.domain
                val organizer =
                    SessionManager.getOrganizer() ?: throw PaymentError.OrganizerNotFound()
                if (domain != null && organizer.relationshipFor(domain) != null) {
                    if (receiveRequest.verifier != null) {
                        client.establishRelationship(organizer, domain)
                    }
                }

                // 5. Complete the transfer.
                val transferResult = client.transferWithResult(
                    context = context,
                    amount = paymentAmount.copy(kin = paymentAmount.kin.toKinTruncating()),
                    fee = fee.kin,
                    organizer = organizer,
                    rendezvousKey = rendezvousKey.publicKeyBytes.toPublicKey(),
                    destination = receiveRequest.account,
                    isWithdrawal = true
                ).blockingGet()

                if (transferResult.isSuccess) {
                    Completable.concatArray(
                        balanceController.fetchBalance(),
                        client.fetchLimits(isForce = true)
                    ).doOnComplete {
                        analytics.transfer(
                            amount = paymentAmount,
                            successful = true,
                        )
                        cont.resume(Unit)
                    }.subscribe()
                } else {
                    // pass exception down to onFailure for isolated handling
                    throw transferResult.exceptionOrNull() ?: Throwable("Unable to complete payment")
                }
            }.onFailure { error ->
                analytics.transfer(
                    amount = paymentAmount,
                    successful = false
                )
                cont.resumeWithException(error)
            }
        }
    }

    fun rejectPayment(payload: CodePayload) {
        messagingRepository.rejectPayment(payload.rendezvous)
    }
}

sealed interface PaymentError {
    val message: String?

    data class NoExchangeData(override val message: String? = "No exchange data") : PaymentError,
        Throwable(message)

    data class InvalidPayload(override val message: String? = "invalid payload") : PaymentError,
        Throwable(message)

    data class OrganizerNotFound(override val message: String? = "Organizer not found") :
        PaymentError, Throwable(message)

    data class ExchangeForCurrencyNotFound(override val message: String? = "exchange for currency not found") :
        PaymentError, Throwable(message)

    data class MessageForRendezvousNotFound(override val message: String? = "message for rendezvous not found") :
        PaymentError, Throwable(message)
}