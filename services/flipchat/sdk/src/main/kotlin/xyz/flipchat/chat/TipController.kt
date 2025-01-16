package xyz.flipchat.chat

import androidx.compose.runtime.staticCompositionLocalOf
import com.getcode.domain.BillController
import com.getcode.model.Currency
import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.models.ConfirmationState
import com.getcode.models.MessageTipPaymentConfirmation
import com.getcode.network.BalanceController
import com.getcode.network.repository.PaymentRepository
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.CurrencyUtils
import com.getcode.utils.Kin
import com.getcode.utils.flagResId
import com.getcode.utils.formatAmountString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.flipchat.services.PaymentController
import xyz.flipchat.services.PaymentEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class TipController @Inject constructor(
    paymentRepository: PaymentRepository,
    currencyUtils: CurrencyUtils,
    private val resources: ResourceHelper,
    private val billController: BillController,
    private val balanceController: BalanceController,
    private val roomController: RoomController,
) : PaymentController(
    paymentRepository, resources, billController, balanceController, currencyUtils
) {
    fun presentMessageTipConfirmation(conversationId: ID, messageId: ID) {
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
                    conversationId = conversationId,
                    messageId = messageId,
                    balance = balance,
                    currencyFlag = Currency.Kin.flagResId(resources),
                )
            )
        }
    }

    fun completeMessageTip(amount: KinAmount) =
        scope.launch {
            val confirmation = billController.state.value.messageTipPaymentConfirmation ?: return@launch
            val messageId = confirmation.messageId
            val conversationId = confirmation.conversationId

            billController.update {
                it.copy(
                    messageTipPaymentConfirmation = it.messageTipPaymentConfirmation?.copy(state = ConfirmationState.Sending),
                )
            }

            roomController.sendTip(conversationId, messageId, amount)
                .onFailure {
                    _eventFlow.emit(PaymentEvent.OnPaymentError(it))
                    billController.reset()
                }
                .onSuccess {
                    billController.update { billState ->
                        val publicPaymentConfirmation =
                            billState.publicPaymentConfirmation ?: return@update billState
                        billState.copy(
                            publicPaymentConfirmation = publicPaymentConfirmation.copy(state = ConfirmationState.Sent),
                        )
                    }
                    delay(1.33.seconds)
                    cancelPayment(fromUser = false)
                }
        }
}

val LocalTipController = staticCompositionLocalOf<TipController?> { null }