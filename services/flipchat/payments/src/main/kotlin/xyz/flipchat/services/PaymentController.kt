package xyz.flipchat.services

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.domain.BillController
import com.getcode.manager.TopBarManager
import com.getcode.model.Currency
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.models.BillState
import com.getcode.models.ConfirmationState
import com.getcode.models.MessageTipPaymentConfirmation
import com.getcode.models.PublicPaymentConfirmation
import com.getcode.network.BalanceController
import com.getcode.network.repository.PaymentError
import com.getcode.network.repository.PaymentRepository
import com.getcode.services.model.ExtendedMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.Kin
import com.getcode.utils.flagResId
import com.getcode.utils.formatAmountString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.flipchat.services.payments.R
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

data class PaymentState(
    val billState: BillState = BillState.Default,
)

sealed interface PaymentEvent {
    data class OnPaymentSuccess(
        val intentId: ID,
        val destination: PublicKey,
        val metadata: ExtendedMetadata?,
        val amount: KinAmount,
        val acknowledge: (Boolean, () -> Unit) -> Unit // Caller returns true if they want to proceed as success, false as error
    ) : PaymentEvent
    data object OnPaymentCancelled : PaymentEvent
    data class OnPaymentError(val error: Throwable): PaymentEvent
}

val LocalPaymentController = staticCompositionLocalOf<PaymentController?> { null }

@Singleton
open class PaymentController @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val resources: ResourceHelper,
    private val billController: BillController,
    private val balanceController: BalanceController,
    private val currencyUtils: CurrencyUtils,
) {
    protected val scope = CoroutineScope(Dispatchers.IO)

    val state = billController.state.map {
        PaymentState(it)
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = PaymentState())

    protected val _eventFlow: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    val eventFlow: SharedFlow<PaymentEvent> = _eventFlow.asSharedFlow()

    fun presentPublicPaymentConfirmation(
        destination: PublicKey,
        amount: KinAmount,
        metadata: ExtendedMetadata,
    ) {
        billController.update {
            it.copy(
                publicPaymentConfirmation = PublicPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    amount = amount,
                    destination = destination,
                    metadata = metadata,
                )
            )
        }
    }

    fun completePublicPayment() =
        scope.launch {
            val confirmation = billController.state.value.publicPaymentConfirmation ?: return@launch
            val destination = confirmation.destination
            val amount = confirmation.amount
            val metadata = confirmation.metadata

            billController.update {
                it.copy(
                    publicPaymentConfirmation = it.publicPaymentConfirmation?.copy(state = ConfirmationState.Sending),
                )
            }

            runCatching {
                paymentRepository.payPublicly(amount, destination, metadata)
            }.onSuccess {
                _eventFlow.emit(PaymentEvent.OnPaymentSuccess(
                    intentId = it,
                    destination = destination,
                    metadata = metadata,
                    amount = amount,
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
                                delay(1.33.seconds)
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
                            is PaymentError.OrganizerNotFound -> presentPaymentFailedError()
                        }
                    }
                    else -> presentPaymentFailedError()
                }

                _eventFlow.emit(PaymentEvent.OnPaymentError(it))

                billController.reset()
            }
        }

    fun cancelPayment(fromUser: Boolean = true) {
        scope.launch {
            billController.reset()
            if (fromUser) {
                _eventFlow.emit(PaymentEvent.OnPaymentCancelled)
            }
        }
    }

    fun presentMessageTipConfirmation(metadata: ExtendedMetadata, destination: PublicKey) {
        val rawBalance = balanceController.rawBalance
        val balance = formatAmountString(
            resources,
            Currency.Kin,
            rawBalance,
            suffix = resources.getKinSuffix()
        )

        billController.update {
            it.copy(
                messageTipPaymentConfirmation = MessageTipPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    metadata = metadata,
                    destination = destination,
                    balance = balance,
                )
            )
        }
    }

    fun completeMessageTip(amount: KinAmount) =
        scope.launch {
            val confirmation = billController.state.value.messageTipPaymentConfirmation ?: return@launch
            val destination = confirmation.destination
            val metadata = confirmation.metadata

            billController.update {
                it.copy(
                    messageTipPaymentConfirmation = it.messageTipPaymentConfirmation?.copy(state = ConfirmationState.Sending),
                )
            }

            runCatching {
                paymentRepository.payPublicly(amount, destination, metadata)
            }.onSuccess {
                _eventFlow.emit(PaymentEvent.OnPaymentSuccess(
                    intentId = it,
                    destination = destination,
                    metadata = metadata,
                    amount = amount,
                    acknowledge = { isSuccess, after ->
                        if (isSuccess) {
                            scope.launch {
                                billController.update { billState ->
                                    val messageTipPaymentConfirmation =
                                        billState.messageTipPaymentConfirmation ?: return@update billState
                                    billState.copy(
                                        messageTipPaymentConfirmation = messageTipPaymentConfirmation.copy(state = ConfirmationState.Sent),
                                    )
                                }
                                delay(1.33.seconds)
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
                            is PaymentError.OrganizerNotFound -> presentPaymentFailedError()
                        }
                    }
                    else -> presentPaymentFailedError()
                }

                _eventFlow.emit(PaymentEvent.OnPaymentError(it))

                billController.reset()
            }
        }

    private fun presentInsufficientFundsError() {
        TopBarManager.showMessage(
            resources.getString(R.string.error_title_paymentFailedDueToInsufficientFunds),
            resources.getString(R.string.error_description_paymentFailedDueToInsufficientFunds),
        )
    }

    private fun presentPaymentFailedError() {
        TopBarManager.showMessage(
            resources.getString(R.string.error_title_paymentFailed),
            resources.getString(R.string.error_description_paymentFailed),
        )
    }
}