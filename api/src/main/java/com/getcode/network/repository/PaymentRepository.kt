package com.getcode.network.repository

import android.annotation.SuppressLint
import android.content.Context
import com.getcode.analytics.AnalyticsService
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.CodePayload
import com.getcode.model.KinAmount
import com.getcode.model.LoginRequest
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.establishRelationshipSingle
import com.getcode.network.client.fetchLimits
import com.getcode.network.client.transferWithResult
import com.getcode.network.exchange.Exchange
import com.getcode.utils.ErrorUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class PaymentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exchange: Exchange,
    private val messagingRepository: MessagingRepository,
    private val client: Client,
    private val analytics: AnalyticsService,
    private val balanceController: BalanceController,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    fun attemptLogin(payload: CodePayload): Pair<CodePayload, LoginRequest>? {
        return runCatching {
            // 1. Fetch message metadata for this payload to get the
            // domain for which we'll need to establish a relationship
            val loginAttempt = messagingRepository
                .fetchMessages(payload.rendezvous)
                .getOrNull()
                ?.takeIf { it.isNotEmpty() && it.first().loginRequest != null }
                ?.firstOrNull()?.loginRequest
                ?: throw PaymentError.MessageForRendezvousNotFound()

            codeScanned(payload.rendezvous)
            return payload to loginAttempt
        }.onFailure { ErrorUtils.handleError(it) }.getOrNull()
    }

//    suspend fun rejectLogin(rendezvousKey: KeyPair) {
//        messagingRepository.rejectLogin(rendezvousKey)
//    }

    suspend fun attemptRequest(payload: CodePayload): Pair<KinAmount, CodePayload>? {
        val fiat = payload.fiat
        if (fiat == null) {
            Timber.d("payload does not contain Fiat value")
            return null
        }

        exchange.fetchRatesIfNeeded()
        val rate = exchange.rateFor(fiat.currency)
        if (rate == null) {
            Timber.d("Unable to determine rate")
            return null
        }

        Timber.d("Rate for ${rate.currency.name}: ${rate.fx}")
        Timber.d("fiat value = ${fiat.amount}")

        val amount = KinAmount.fromFiatAmount(fiat.amount, rate.fx, fiat.currency)

        Timber.d("amount=${amount.fiat}, ${amount.kin}, ${amount.rate}")

        codeScanned(payload.rendezvous)

        return amount to payload
    }

    private fun codeScanned(rendezvousKey: KeyPair) = launch {
        messagingRepository.codeScanned(rendezvousKey)
            .onSuccess {
                Timber.d("code scanned message sent successfully")
            }.onFailure {
                Timber.e(t = it, message = "code scanned message sent unsuccessfully")
            }
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

                val organizer =
                    SessionManager.getOrganizer() ?: throw PaymentError.OrganizerNotFound()

                // 4. Establish a relationship if a domain is provided. If a verifier
                // is present that means the domain has been verified by the server.
                val domain = receiveRequest.domain
                if (domain != null) {
                    if (
                        receiveRequest.verifier != null &&
                        organizer.relationshipFor(domain) == null
                    ) {
                        client.establishRelationshipSingle(organizer, domain).blockingGet()
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
                    throw transferResult.exceptionOrNull()
                        ?: Throwable("Unable to complete payment")
                }
            }.onFailure { error ->
                analytics.transfer(
                    amount = paymentAmount,
                    successful = false
                )
                ErrorUtils.handleError(error)
                cont.resumeWithException(error)
            }
        }
    }

    suspend fun rejectPayment(payload: CodePayload) {
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