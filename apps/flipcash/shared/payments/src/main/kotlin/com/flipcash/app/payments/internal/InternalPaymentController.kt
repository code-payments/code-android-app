package com.flipcash.app.payments.internal

import com.flipcash.app.payments.ConfirmationState
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.PaymentState
import com.flipcash.app.payments.PoolResolutionConfirmation
import com.flipcash.app.payments.PublicPaymentConfirmation
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InternalPaymentController(
    private val resources: ResourceHelper,
    private val balanceController: BalanceController,
    private val transactionController: TransactionController,
    private val userManager: UserManager,
    private val exchange: Exchange,
) : PaymentController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state: MutableStateFlow<PaymentState> = MutableStateFlow(PaymentState.Default)
    override val state: StateFlow<PaymentState>
        get() = _state.asStateFlow()


    private val _eventFlow: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    override val eventFlow: SharedFlow<PaymentEvent> = _eventFlow.asSharedFlow()

    override fun requestPaymentConfirmation(request: PaymentRequest) {
        val vault = userManager.accountCluster?.vaultPublicKey ?: return
        _state.update {
            when (request) {
                is PaymentRequest.PoolBid -> it.copy(
                    request = request,
                    poolBidConfirmation = PublicPaymentConfirmation(
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
                        request = request,
                        poolResolutionConfirmation = PoolResolutionConfirmation(
                            state = ConfirmationState.AwaitingConfirmation,
                            poolId = request.pool.id,
                            metadata = PoolResolutionPaymentMetadata(
                                pool = request.pool,
                                bets = request.bets,
                                rendezvous = request.rendezvous,
                                resolution = request.resolution
                            )
                        )
                    )

                }
            }
        }
    }

    override fun completeRequest() {
        scope.launch {
            val request = _state.value.request ?: return@launch
            when (request) {
                is PaymentRequest.PoolBid -> {
                    completeBidPayment()
                }

                is PaymentRequest.ResolvePool -> {
                    completePoolDisbursement()
                }
            }
        }
    }

    private suspend fun completeBidPayment() {
        val confirmation = _state.value.poolBidConfirmation ?: return
        val destination = confirmation.destination
        val amount = confirmation.amount
        val metadata = confirmation.metadata as PoolBidPaymentMetadata

        val (pool, _, _) = metadata
        _state.update {
            it.copy(
                poolBidConfirmation = it.poolBidConfirmation?.copy(state = ConfirmationState.Sending),
            )
        }

        exchange.fetchRatesIfNeeded()

        val balance = balanceController.rawBalance.value


        if (balance < pool.buyIn) {
            _state.update {
                it.copy(
                    poolBidConfirmation = it.poolBidConfirmation?.copy(state = ConfirmationState.AwaitingConfirmation),
                )
            }
            _eventFlow.emit(PaymentEvent.OnPaymentError(PaymentError.InsufficientBalance()))
            return
        }

        val localizedAmount = LocalFiat(
            usdc = amount.convertingTo(exchange.rateForUsd()),
            converted = amount,
        )

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
                            _state.update {
                                val publicPaymentConfirmation =
                                    it.poolBidConfirmation ?: return@update it
                                it.copy(
                                    poolBidConfirmation = publicPaymentConfirmation.copy(
                                        state = ConfirmationState.Sent
                                    ),
                                )
                            }
                            cancelRequest(fromUser = false)
                            after()
                        } else {
                            _state.update { PaymentState.Default }
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

            _state.update { PaymentState.Default }
        }
    }

    private suspend fun completePoolDisbursement() {
        val confirmation = _state.value.poolResolutionConfirmation ?: return
        val metadata = confirmation.metadata
        val (pool, bets, rendezvous, resolution) = metadata

        _eventFlow.emit(PaymentEvent.OnPaymentError(Throwable("Not yet implemented")))

        _state.update { PaymentState.Default }
    }

    override fun cancelRequest(fromUser: Boolean) {
        scope.launch {
            _state.update { PaymentState.Default }
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