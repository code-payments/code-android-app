package com.getcode.network.repository

import android.annotation.SuppressLint
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.services.model.CodePayload
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.LoginRequest
import com.getcode.model.SocialUser
import com.getcode.model.fromFiatAmount
import com.getcode.model.generate
import com.getcode.model.intents.PrivateTransferMetadata
import com.getcode.model.toPublicKey
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.establishRelationshipSingle
import com.getcode.network.client.fetchLimits
import com.getcode.network.client.transferWithResult
import com.getcode.network.exchange.Exchange
import com.getcode.services.analytics.AnalyticsService
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class PaymentRepository @Inject constructor(
    private val exchange: Exchange,
    private val messagingRepository: MessagingRepository,
    private val client: Client,
    private val analytics: AnalyticsService,
    private val balanceController: BalanceController,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    suspend fun payPublicly(destination: PublicKey, amount: KinAmount): ID {
        return suspendCancellableCoroutine { cont ->
            val organizer = client.organizerLookup() ?: throw PaymentError.OrganizerNotFound()

            // Generally, we would use the rendezvous key that
            // was generated from the scan code payload, however,
            // tip codes are inherently deterministic and won't
            // change so we need a unique rendezvous for every tx.
            val rendezvous = PublicKey.generate()

            runCatching {
                val transferResult = client.transferWithResult(
                    amount = amount,
                    organizer = organizer,
                    fee = Kin.fromKin(0),
                    additionalFees = emptyList(),
                    rendezvousKey = rendezvous,
                    destination = destination,
                    isWithdrawal = true,
                )

                if (transferResult.isSuccess) {
                    Completable.concatArray(
                        balanceController.getBalance(),
                        client.fetchLimits(isForce = true)
                    ).observeOn(Schedulers.io()).doOnComplete {
//                        analytics.transferForTip(amount = amount, successful = true)
                        cont.resume(transferResult.getOrNull().orEmpty())
                    }.subscribe()
                } else {
                    // pass exception down to onFailure for isolated handling
                    throw transferResult.exceptionOrNull()
                        ?: Throwable("Unable to complete payment")
                }
            }.onFailure { error ->
                ErrorUtils.handleError(error)
                cont.resumeWithException(error)
            }
        }
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