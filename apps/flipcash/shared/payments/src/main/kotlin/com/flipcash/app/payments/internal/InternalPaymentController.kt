package com.flipcash.app.payments.internal

import com.flipcash.app.core.bill.ConfirmationState
import com.flipcash.app.core.bill.PublicPaymentConfirmation
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.PaymentState
import com.flipcash.app.payments.PoolPaymentMetadata
import com.flipcash.services.models.PoolMetadata
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.BalanceController
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
) : PaymentController {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val state: StateFlow<PaymentState> = billController.state.map {
        PaymentState(it)
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = PaymentState())


    private val _eventFlow: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    override val eventFlow: SharedFlow<PaymentEvent> = _eventFlow.asSharedFlow()

    override fun presentPublicPaymentConfirmation(request: PaymentRequest) {
        billController.update {
            when (request) {
                is PaymentRequest.Pool -> it.copy(
                    publicPaymentConfirmation = PublicPaymentConfirmation(
                        state = ConfirmationState.AwaitingConfirmation,
                        amount = request.pool.buyIn,
                        destination = request.pool.fundingDestination,
                        metadata = PoolPaymentMetadata(
                            pool = PoolMetadata(
                                id = request.pool.id,
                                creator = request.pool.creator,
                                name = request.pool.name,
                                buyIn = request.pool.buyIn,
                                fundingDestination = request.pool.fundingDestination,
                                createdAt = request.pool.createdAt,
                                isOpen = request.pool.isOpen,
                            ),
                            selectedOutcome = request.outcome,
                            rendezvous = request.pool.rendezvous!!
                        )
                    )
                )
            }
        }
    }

    override fun completePublicPayment() {
        scope.launch {
            val confirmation = billController.state.value.publicPaymentConfirmation ?: return@launch
            val metadata = confirmation.metadata as PoolPaymentMetadata

            val (pool, rendezvous, outcome) = metadata
            billController.update {
                it.copy(
                    publicPaymentConfirmation = it.publicPaymentConfirmation?.copy(state = ConfirmationState.Sending),
                )
            }

            val balance = balanceController.rawBalance.value

            runCatching {
                if (balance < pool.buyIn) throw PaymentError.InsufficientBalance()
                Unit
            }.onSuccess {
                _eventFlow.emit(
                    PaymentEvent.OnPaymentSuccess(
                    metadata = metadata,
                    acknowledge = { isSuccess, after ->
                        if (isSuccess) {
                            scope.launch {
                                billController.update { billState ->
                                    val publicPaymentConfirmation =
                                        billState.publicPaymentConfirmation ?: return@update billState
                                    billState.copy(
                                        publicPaymentConfirmation = publicPaymentConfirmation.copy(state = ConfirmationState.Sent),
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
                ))
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