package com.flipcash.app.payments.internal

import com.flipcash.app.payments.ConfirmationEvent
import com.flipcash.app.payments.ConfirmationState
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.PaymentState
import com.flipcash.app.payments.PoolResolutionConfirmation
import com.flipcash.app.payments.PublicPaymentConfirmation
import com.flipcash.app.payments.delegates.PoolBidDelegate
import com.flipcash.app.payments.delegates.DelegateEvent
import com.flipcash.app.payments.delegates.PoolResolveDelegate
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.getPublicKeyBase58
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

internal class InternalPaymentController(
    private val resources: ResourceHelper,
    private val userManager: UserManager,
    private val poolBidDelegate: PoolBidDelegate,
    private val poolResolveDelegate: PoolResolveDelegate,
) : PaymentController, PoolBidDelegate by poolBidDelegate,
    PoolResolveDelegate by poolResolveDelegate {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state: MutableStateFlow<PaymentState> = MutableStateFlow(PaymentState.Default)
    override val state: StateFlow<PaymentState>
        get() = _state.asStateFlow()


    private val _paymentEvents: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    override val paymentEvents: SharedFlow<PaymentEvent> = _paymentEvents.asSharedFlow()

    private val _confirmationEvents: MutableSharedFlow<ConfirmationEvent> = MutableSharedFlow()
    override val confirmationEvents: SharedFlow<ConfirmationEvent> =
        _confirmationEvents.asSharedFlow()

    override fun requestPaymentConfirmation(request: PaymentRequest<*>) {
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
        val request = _state.value.request ?: return
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

        request.rpcCall!!.invoke()
            .map { it as ID }
            .onSuccess { bidId ->
                poolBidDelegate.payForBid(
                    pool = pool,
                    bidId = bidId,
                    amount = amount,
                    payoutDestination = destination,
                    rendezvous = metadata.rendezvous,
                    onSuccess = { intentId ->
                        handlePaymentSuccess(
                            intentId = intentId,
                            metadata = metadata,
                            stateUpdate = {
                                it.copy(
                                    poolBidConfirmation = it.poolBidConfirmation?.copy(
                                        state = ConfirmationState.Sent
                                    ),
                                )
                            }
                        )
                    },
                    onError = ::handlePaymentError,
                )
            }.onFailure {
                _state.update { PaymentState.Default }
                _paymentEvents.emit(PaymentEvent.OnRpcFailure(it))
            }
    }

    private suspend fun completePoolDisbursement() {
        val request = _state.value.request ?: return
        val confirmation = _state.value.poolResolutionConfirmation ?: return
        val metadata = confirmation.metadata
        val (pool, bets, rendezvous, resolution) = metadata

        request.rpcCall!!.invoke()
            .onSuccess {
                poolResolveDelegate.resolvePool(
                    pool = pool,
                    bets = bets,
                    rendezvous = rendezvous,
                    resolution = resolution,
                    onSuccess = { id ->
                        handlePaymentSuccess(
                            intentId = id,
                            metadata = metadata,
                            stateUpdate = {
                                it.copy(
                                    poolResolutionConfirmation = it.poolResolutionConfirmation?.copy(
                                        state = ConfirmationState.Sent
                                    ),
                                )
                            }
                        )
                    },
                    onError = ::handlePaymentError,
                )
            }
            .onFailure {
                _state.update { PaymentState.Default }
                _paymentEvents.emit(PaymentEvent.OnRpcFailure(it))
            }
    }

    private suspend fun handlePaymentSuccess(
        intentId: ID,
        metadata: PaymentMetadata,
        stateUpdate: (PaymentState) -> PaymentState
    ) {
        _paymentEvents.emit(
            PaymentEvent.OnPaymentSuccess(
                intentId = intentId,
                metadata = metadata,
                acknowledge = { isSuccess, after ->
                    if (isSuccess) {
                        _state.update { stateUpdate(it) }
                    }

                    cancelRequest(fromUser = false)
                    after()
                }
            )
        )
    }

    private suspend fun handlePaymentError(error: Throwable) {
        when {
            error is PaymentError -> {
                when (error) {
                    is PaymentError.InsufficientBalance -> presentInsufficientFundsError()
                }
            }

            else -> presentPaymentFailedError()
        }
        _paymentEvents.emit(PaymentEvent.OnPaymentError(error))
        _state.update { PaymentState.Default }
    }

    override fun cancelRequest(fromUser: Boolean) {
        scope.launch {
            _state.update { PaymentState.Default }
            if (fromUser) {
                _paymentEvents.emit(PaymentEvent.OnPaymentCancelled)
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