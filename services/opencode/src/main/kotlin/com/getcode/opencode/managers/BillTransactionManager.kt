package com.getcode.opencode.managers

import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.transactors.ReceiveBillTransactor
import com.getcode.opencode.internal.transactors.GiveBillTransactor
import com.getcode.opencode.internal.transactors.SendBillTransactor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.utils.onSuccessWithDelay
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class BillTransactionManager @Inject constructor(
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val balanceController: BalanceController,
) {
    private var billDismissTimer: TimerTask? = null

    private var giveTransactor: GiveBillTransactor? = null
    private var receiveTransactor: ReceiveBillTransactor? = null
    private var sendTransactor: SendBillTransactor? = null

    fun awaitGrabFromRecipient(
        amount: LocalFiat,
        owner: AccountCluster,
        present: (List<Byte>) -> Unit,
        onGrabbed: () -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        giveTransactor?.dispose()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val payload = GiveBillTransactor(messagingController, transactionController, scope).also {
            giveTransactor = it
        }.with(amount, owner)

        scope.launch {
            giveTransactor?.start()
                ?.onSuccess {
                    onGrabbed()
                    balanceController.subtract(amount)
                    transactionController.updateLimits(owner, force = true)
                    giveTransactor?.dispose()
                    giveTransactor = null
                }
                ?.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    giveTransactor?.dispose()
                    giveTransactor = null
                }
        }

        presentBillForGive(onTimeout)
        present(payload)
    }

    fun attemptGrabFromSender(
        owner: AccountCluster,
        payload: OpenCodePayload,
        onGrabbed: (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        receiveTransactor?.dispose()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        receiveTransactor = ReceiveBillTransactor(messagingController, transactionController, scope).apply {
            with(owner, payload)
        }

        scope.launch {
            receiveTransactor?.start()
                ?.onSuccess { metadata ->
                    trace(
                        tag = "Bill",
                        message = "attemptGrabFromSender: ReceivePublicPayment => ${metadata.exchangeData}"
                    )

                    val amount = LocalFiat(metadata.exchangeData)

                    trace(
                        tag = "Bill",
                        message = "Grabbed ${amount.converted.formatted()} from sender"
                    )
                    onGrabbed(amount)
                    balanceController.add(amount)
                    transactionController.updateLimits(owner, force = true)
                    receiveTransactor?.dispose()
                    receiveTransactor = null
                }
                ?.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    receiveTransactor?.dispose()
                    receiveTransactor = null
                }
        }
    }

    fun createGiftCard(
        amount: LocalFiat,
        owner: AccountCluster,
        onCreated: (GiftCardAccount) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        sendTransactor?.dispose()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val sendTransactor = SendBillTransactor(transactionController, scope).apply {
            with(amount, owner)
        }

        scope.launch {
            sendTransactor.start()
                .onSuccess { onCreated(it) }
                .onFailure { onError(it) }
        }
    }

    private fun presentBillForGive(onTimeout: () -> Unit) {
        cancelBillTimeout()
        billDismissTimer = Timer().schedule((1000 * 50).toLong()) {
            onTimeout()
        }
    }

    fun reset() {
        cancelBillTimeout()
        giveTransactor?.dispose()
        receiveTransactor?.dispose()
        sendTransactor?.dispose()
    }

    private fun cancelBillTimeout() {
        billDismissTimer?.cancel()
    }
}