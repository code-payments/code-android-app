package com.getcode.network.repository

import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.network.BalanceController
import com.getcode.network.client.Client
import com.getcode.network.client.fetchLimits
import com.getcode.network.client.publicPayment
import com.getcode.services.model.ExtendedMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject


class PaymentRepository @Inject constructor(
    private val userManager: UserManager,
    private val client: Client,
    private val balanceController: BalanceController,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    suspend fun payPublicly(
        amount: KinAmount,
        destination: PublicKey,
        extendedMetadata: ExtendedMetadata
    ): ID {
        val organizer = userManager.organizer ?: throw PaymentError.OrganizerNotFound()
        return client.publicPayment(
            amount = amount,
            organizer = organizer,
            destination = destination,
            extendedMetadata = extendedMetadata
        ).onSuccess {
            balanceController.fetchBalance()
            client.fetchLimits(isForce = true).observeOn(Schedulers.io()).subscribe()
        }.onFailure {
            ErrorUtils.handleError(it)
        }.getOrThrow()
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