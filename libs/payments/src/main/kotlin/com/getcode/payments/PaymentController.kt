package com.getcode.payments

import androidx.compose.runtime.staticCompositionLocalOf
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
import com.getcode.network.repository.PaymentRepository
import com.getcode.util.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
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
    private val paymentRepository: PaymentRepository,
    private val resources: ResourceHelper,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val state = MutableStateFlow(PaymentState())

    private val _eventFlow: MutableSharedFlow<PaymentEvent> = MutableSharedFlow()
    val eventFlow: SharedFlow<PaymentEvent> = _eventFlow.asSharedFlow()

    fun presentPrivatePaymentConfirmation(socialUser: SocialUser, amount: KinAmount) {
        val payload = CodePayload(
            kind = Kind.Tip,
            value = Username(socialUser.username),
        )


        state.update {
            val billState = it.billState.copy(
                socialUserPaymentConfirmation = SocialUserPaymentConfirmation(
                    state = ConfirmationState.AwaitingConfirmation,
                    payload = payload,
                    amount = amount,
                    metadata = socialUser,
                    isPrivate = true,
                    showScrim = true
                )
            )

            it.copy(billState = billState)
        }
    }

    fun completePrivatePayment() = scope.launch {
        val confirmation = state.value.billState.socialUserPaymentConfirmation ?: return@launch
        val user = confirmation.metadata
        val amount = confirmation.amount

        state.update {
            val billState = it.billState
            it.copy(
                billState = billState.copy(
                    socialUserPaymentConfirmation = billState.socialUserPaymentConfirmation?.copy(state = ConfirmationState.Sending)
                ),
            )
        }

        runCatching {
            paymentRepository.payForFriendship(user, amount)
        }.onSuccess {
//            historyController.fetch()

            state.update { s ->
                val billState = s.billState
                val socialUserPaymentConfirmation = s.billState.socialUserPaymentConfirmation ?: return@update s

                s.copy(
                    billState = billState.copy(
                        socialUserPaymentConfirmation = socialUserPaymentConfirmation.copy(state = ConfirmationState.Sent),
                    ),
                )
            }
            delay(1.seconds)
            cancelTip()
            delay(400.milliseconds)
            _eventFlow.emit(PaymentEvent.OnChatPaidForSuccessfully(it, user))
        }.onFailure {
            TopBarManager.showMessage(
                resources.getString(R.string.error_title_payment_failed),
                resources.getString(R.string.error_description_payment_failed),
            )

            state.update { uiModel ->
                uiModel.copy(
                    billState = uiModel.billState.copy(
                        bill = null,
                        showToast = false,
                        socialUserPaymentConfirmation = null,
                        toast = null,
                        valuation = null,
                        primaryAction = null,
                        secondaryAction = null,
                    )
                )
            }
        }
    }

    fun cancelTipEntry() {
        // Cancelling from amount entry is triggered by a UI event.
        // To distinguish between a valid "Next" action that will
        // also dismiss the entry screen, we need to check explicitly
        if (state.value.billState.socialUserPaymentConfirmation == null) {
            cancelTip()
        }
    }

    fun cancelTip() {
//        tipController.reset()
        state.update {
            val billState = it.billState.copy(
                bill = null,
                socialUserPaymentConfirmation = null,
                valuation = null,
                primaryAction = null,
                secondaryAction = null,
            )

            it.copy(
                billState = billState
            )
        }
    }
}