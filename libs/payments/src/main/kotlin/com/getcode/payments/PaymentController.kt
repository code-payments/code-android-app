package com.getcode.payments

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.domain.BillController
import com.getcode.libs.payments.R
import com.getcode.manager.TopBarManager
import com.getcode.model.CodePayload
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.model.Kind
import com.getcode.model.SocialUser
import com.getcode.model.Username
import com.getcode.models.BillState
import com.getcode.models.ConfirmationState
import com.getcode.models.SocialUserPaymentConfirmation
import com.getcode.network.ChatHistoryController
import com.getcode.network.repository.PaymentRepository
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
    data class OnChatPaidForSuccessfully(val intentId: ID, val user: SocialUser): PaymentEvent
}

val LocalPaymentController = staticCompositionLocalOf<PaymentController?> { null }

@Singleton
class PaymentController @Inject constructor(
    private val historyController: ChatHistoryController,
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

    fun presentPrivatePaymentConfirmation(socialUser: SocialUser, amount: KinAmount) {
        val payload = CodePayload(
            kind = Kind.Tip,
            value = Username(socialUser.username),
        )


        billController.update {
            it.copy(
                socialUserPaymentConfirmation = SocialUserPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    payload = payload,
                    amount = amount,
                    metadata = socialUser,
                    isPrivate = true,
                    showScrim = true
                )
            )
        }
    }

    fun completePrivatePayment() = scope.launch {
        val confirmation = billController.state.value.socialUserPaymentConfirmation ?: return@launch
        val user = confirmation.metadata
        val amount = confirmation.amount

        billController.update {
            it.copy(
                socialUserPaymentConfirmation = it.socialUserPaymentConfirmation?.copy(state = ConfirmationState.Sending),
            )
        }

        runCatching {
            paymentRepository.payForFriendship(user, amount)
        }.onSuccess {
            historyController.fetch()

            billController.update { billState ->
                val socialUserPaymentConfirmation = billState.socialUserPaymentConfirmation ?: return@update billState
                billState.copy(
                    socialUserPaymentConfirmation = socialUserPaymentConfirmation.copy(state = ConfirmationState.Sent),
                )
            }
            delay(1.seconds)
            cancelPayment()
            delay(400.milliseconds)
            _eventFlow.emit(PaymentEvent.OnChatPaidForSuccessfully(it, user))
        }.onFailure {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_payment_failed),
                resources.getString(R.string.error_description_payment_failed),
            )

            billController.reset()
        }
    }

    fun cancelPayment() {
        billController.reset()
    }
}