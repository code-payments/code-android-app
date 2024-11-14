package xyz.flipchat.services

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.domain.BillController
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.models.BillState
import com.getcode.models.ConfirmationState
import com.getcode.models.PublicPaymentConfirmation
import com.getcode.network.repository.PaymentRepository
import com.getcode.oct24.services.payments.R
import com.getcode.services.model.ExtendedMetadata
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class PaymentState(
    val billState: BillState = BillState.Default,
)

sealed interface PaymentEvent {
    data object PresentPaymentEntry : PaymentEvent
    data class OnPaymentSuccess(val intentId: ID, val destination: PublicKey) : PaymentEvent
    data object OnPaymentCancelled : PaymentEvent
    data class OnPaymentError(val error: Throwable): PaymentEvent
}

val LocalPaymentController = staticCompositionLocalOf<PaymentController?> { null }

@Singleton
class PaymentController @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val resources: ResourceHelper,
    private val billController: BillController,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val state = billController.state.map {
        PaymentState(it)
    }.stateIn(scope, started = SharingStarted.Eagerly, initialValue = PaymentState())

    private val _eventFlow: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    val eventFlow: SharedFlow<PaymentEvent> = _eventFlow.asSharedFlow()

    fun presentPublicPaymentConfirmation(
        destination: PublicKey,
        amount: KinAmount,
        metadata: ExtendedMetadata
    ) {
        billController.update {
            it.copy(
                publicPaymentConfirmation = PublicPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    amount = amount,
                    destination = destination,
                    metadata = metadata
                )
            )
        }
    }

    fun completePublicPayment(onSuccess: () -> Unit = { }, onError: (Throwable) -> Unit = { }) =
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
                onSuccess()

                billController.update { billState ->
                    val publicPaymentConfirmation =
                        billState.publicPaymentConfirmation ?: return@update billState
                    billState.copy(
                        publicPaymentConfirmation = publicPaymentConfirmation.copy(state = ConfirmationState.Sent),
                    )
                }
                delay(1.seconds)
                cancelPayment(fromUser = false)
                delay(400.milliseconds)
                _eventFlow.emit(PaymentEvent.OnPaymentSuccess(it, destination))
            }.onFailure {
                onError(it)
                TopBarManager.showMessage(
                    resources.getString(R.string.error_title_payment_failed),
                    resources.getString(R.string.error_description_payment_failed),
                )
                _eventFlow.emit(PaymentEvent.OnPaymentError(it))

                billController.reset()
            }
        }

    fun cancelPayment(fromUser: Boolean = true) {
        billController.reset()
        if (fromUser) {
            _eventFlow.tryEmit(PaymentEvent.OnPaymentCancelled)
        }
    }
}