package com.flipcash.app.payments.internal

import com.flipcash.app.core.bill.ConfirmationState
import com.flipcash.app.core.bill.PoolResolutionConfirmation
import com.flipcash.app.core.bill.PublicPaymentConfirmation
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.PaymentState
import com.flipcash.app.payments.PoolBidPaymentMetadata
import com.flipcash.app.payments.PoolResolutionPaymentMetadata
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InternalPaymentController(
    private val resources: ResourceHelper,
    private val billController: BillController,
    private val balanceController: BalanceController,
    private val transactionController: TransactionController,
    private val userManager: UserManager,
    private val exchange: Exchange,
) : PaymentController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val state: StateFlow<PaymentState> = billController.state.map {
        PaymentState(it)
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = PaymentState())


    private val _eventFlow: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    override val eventFlow: SharedFlow<PaymentEvent> = _eventFlow.asSharedFlow()

    override fun presentPublicPaymentConfirmation(request: PaymentRequest) {
        val vault = userManager.accountCluster?.vaultPublicKey ?: return
        billController.update {
            when (request) {
                is PaymentRequest.PoolBid -> it.copy(
                    publicPaymentConfirmation = PublicPaymentConfirmation(
                        state = ConfirmationState.AwaitingConfirmation,
                        amount = request.pool.buyIn,
                        destination = vault,
                        metadata = PoolBidPaymentMetadata(
                            pool = request.pool,
                            selectedOutcome = request.outcome,
                            rendezvous = request.rendezvous
                        )
                    )
                )

                is PaymentRequest.ResolvePool -> {
                    it.copy(
                        poolResolutionConfirmation = PoolResolutionConfirmation(
                            state = ConfirmationState.AwaitingConfirmation,
                            poolId = request.poolWithBets.pool.id,
                            metadata = PoolResolutionPaymentMetadata(
                                poolWithBets = request.poolWithBets,
                                resolution = request.resolution
                            )
                        )
                    )

                }
            }
        }
    }

    override fun completePublicPayment() {
        scope.launch {
            val confirmation = billController.state.value.publicPaymentConfirmation ?: return@launch
            val destination = confirmation.destination
            val amount = confirmation.amount
            val metadata = confirmation.metadata

            when (metadata) {
                is PoolBidPaymentMetadata -> {
                    completeBidPayment(
                        metadata = metadata,
                        amount = amount,
                        destination = destination
                    )
                }

                is PoolResolutionPaymentMetadata -> {
                    completePoolDisbursement(metadata)
                }
            }
        }
    }

    private suspend fun completeBidPayment(
        metadata: PoolBidPaymentMetadata,
        amount: Fiat,
        destination: PublicKey

    ) {
        val (pool, _, _) = metadata
        billController.update {
            it.copy(
                publicPaymentConfirmation = it.publicPaymentConfirmation?.copy(state = ConfirmationState.Sending),
            )
        }

        exchange.fetchRatesIfNeeded()

        val balance = balanceController.rawBalance.value

        val localizedAmount = LocalFiat(
            usdc = amount.convertingTo(exchange.rateForUsd()),
            converted = amount,
        )


        if (balance < pool.buyIn) {
            billController.update {
                it.copy(
                    publicPaymentConfirmation = it.publicPaymentConfirmation?.copy(state = ConfirmationState.AwaitingConfirmation),
                )
            }
            _eventFlow.emit(PaymentEvent.OnPaymentError(PaymentError.InsufficientBalance()))
            return
        }

//        val request = transactionController.transfer(
//            destination = destination,
//            amount = localizedAmount,
//            rendezvous = PublicKey.fromBase58(metadata.rendezvous.getPublicKeyBase58()),
//            owner = userManager.accountCluster!!,
//        ).map { it.id.bytes }

        Result.success(RandomId).onSuccess {
                _eventFlow.emit(
                    PaymentEvent.OnPaymentSuccess(
                        intentId = it,
                        metadata = metadata,
                        acknowledge = { isSuccess, after ->
                            if (isSuccess) {
                                scope.launch {
                                    billController.update { billState ->
                                        val publicPaymentConfirmation =
                                            billState.publicPaymentConfirmation
                                                ?: return@update billState
                                        billState.copy(
                                            publicPaymentConfirmation = publicPaymentConfirmation.copy(
                                                state = ConfirmationState.Sent
                                            ),
                                        )
                                    }
                                    cancelPayment(fromUser = false)
                                    after()
                                }
                            } else {
                                billController.reset()
                                after()
                            }
                        }
                    )
                )
            }.onFailure {
                when {
                    it is PaymentError -> {
                        when (it) {
                            is PaymentError.InsufficientBalance -> presentInsufficientFundsError()
                        }
                    }

                    else -> presentPaymentFailedError()
                }

                _eventFlow.emit(PaymentEvent.OnPaymentError(it))

                billController.reset()
            }
    }

    private suspend fun completePoolDisbursement(
        metadata: PoolResolutionPaymentMetadata
    ) {
        val (poolWithBets, resolution) = metadata

        _eventFlow.emit(PaymentEvent.OnPaymentError(Throwable("Not yet implemented")))

        billController.reset()
    }

    override fun cancelPayment(fromUser: Boolean) {
        scope.launch {
            billController.reset()
            if (fromUser) {
                _eventFlow.emit(PaymentEvent.OnPaymentCancelled)
            }
        }
    }

    private fun presentInsufficientFundsError() {
        BottomBarManager.showError(
            resources.getString(R.string.error_title_paymentFailedDueToInsufficientFunds),
            resources.getString(R.string.error_description_paymentFailedDueToInsufficientFunds),
        )
    }

    private fun presentPaymentFailedError() {
        BottomBarManager.showError(
            resources.getString(R.string.error_title_paymentFailed),
            resources.getString(R.string.error_description_paymentFailed),
        )
    }
}

sealed interface PaymentError {
    val message: String?

    data class InsufficientBalance(override val message: String? = "Insufficient balance for payment") :
        PaymentError, Throwable(message)
}