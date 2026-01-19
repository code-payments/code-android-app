package com.flipcash.app.payments.internal

import com.flipcash.app.payments.ConfirmationEvent
import com.flipcash.app.payments.PaymentController
import com.flipcash.app.payments.PaymentEvent
import com.flipcash.app.payments.PaymentRequest
import com.flipcash.app.payments.PaymentState
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.R
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
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

internal class InternalPaymentController(
    private val resources: ResourceHelper,
    private val userManager: UserManager,
) : PaymentController {

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
        // update the request immediately for usage in early error handling
        _state.update { it.copy(request = request) }
    }

    override fun completeRequest() {
        scope.launch {
            val request = _state.value.request ?: return@launch
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
                        _state.update(stateUpdate)
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
                    is PaymentError.InsufficientBalance -> Unit
                    is PaymentError.NoOwnerForDistribution -> presentPaymentFailedError()
                    is PaymentError.NoPoolBalance -> presentPaymentFailedError()
                    is PaymentError.PoolDistributionFailed -> Unit
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
    data class NoOwnerForDistribution(override val message: String? = "No owner for distribution") : PaymentError, Throwable(message)
    data class NoPoolBalance(override val message: String? = "No pool balance") : PaymentError, Throwable(message)

    data class PoolDistributionFailed(
        val state: Map<String, Any?> = emptyMap(),
        override val message: String = "Failed to distribute funds",
        override val cause: Throwable,
    ): PaymentError, Throwable(
        message = message,
        cause = cause,
    )
}