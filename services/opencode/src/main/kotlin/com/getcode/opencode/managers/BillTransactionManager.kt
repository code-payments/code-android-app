package com.getcode.opencode.managers

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.BalanceController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.transactors.GrabBillTransactor
import com.getcode.opencode.internal.transactors.GiveBillTransactor
import com.getcode.opencode.internal.transactors.ReceiveGiftCardTransactor
import com.getcode.opencode.internal.transactors.SendGiftCardTransactor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.LocalFiat
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

@Singleton
class BillTransactionManager @Inject constructor(
    private val accountController: AccountController,
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val balanceController: BalanceController,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
    private val exchange: Exchange,
) {
    private var billDismissTimer: TimerTask? = null

    // bills
    private var giveTransactor: GiveBillTransactor? = null
    private var grabTransactor: GrabBillTransactor? = null

    // gifts
    private var giftTransactor: SendGiftCardTransactor? = null
    private var receiveTransactor: ReceiveGiftCardTransactor? = null

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

        scope.launch {
            val transactor = GiveBillTransactor(
                messagingController,
                transactionController,
                scope,
                exchange
            ).apply {
                with(amount, owner)
            }

            giveTransactor = transactor

            presentBillForGive(onTimeout)
            present(transactor.data)

            transactor.start()
                .onSuccess {
                    onGrabbed()
                    balanceController.subtract(LocalFiat(it.exchangeData))
//                    scope.launch {
//                        transactionController.updateLimits(owner, force = true)
//                    }
                }.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    transactor.dispose()
                    if (giveTransactor == transactor) {
                        giveTransactor = null
                    }
                }
        }
    }

    fun attemptGrabFromSender(
        owner: AccountCluster,
        payload: OpenCodePayload,
        onGrabbed: (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        grabTransactor?.dispose()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            val transactor =
                GrabBillTransactor(messagingController, transactionController, scope).apply {
                    with(owner, payload)
                }

            grabTransactor = transactor

            transactor.start()
                .onSuccess { metadata ->
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
//                    scope.launch {
//                        transactionController.updateLimits(owner, force = true)
//                    }
                }.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    transactor.dispose()
                    if (grabTransactor == transactor) {
                        grabTransactor = null
                    }
                }
        }
    }

    fun fundGiftCard(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        owner: AccountCluster,
        onFunded: (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        giftTransactor?.dispose()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            val transactor = SendGiftCardTransactor(transactionController).apply {
                with(giftCard, amount, owner)
            }
            giftTransactor = transactor

            transactor.start()
                .onSuccess {
                    onFunded(amount)
                    balanceController.subtract(amount)
//                    scope.launch {
//                        transactionController.updateLimits(owner, force = true)
//                    }
                    if (giftTransactor == transactor) {
                        giftTransactor = null
                    }
                }.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    transactor.dispose()
                    if (giftTransactor == transactor) {
                        giftTransactor = null
                    }
                }
        }
    }

    fun receiveGiftCard(
        owner: AccountCluster,
        entropy: String,
        onReceived: (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        receiveTransactor?.dispose()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            val transactor = ReceiveGiftCardTransactor(
                accountController = accountController,
                transactionController = transactionController,
                mnemonicManager = mnemonicManager,
                giftCardManager = giftCardManager
            ).apply {
                with(owner, entropy)
            }

            receiveTransactor = transactor

            receiveTransactor?.start()
                ?.onSuccess { amount ->
                    onReceived(amount)
                    balanceController.add(amount)
                    transactor.dispose()
                    if (receiveTransactor == transactor) {
                        receiveTransactor = null
                    }
                }?.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    transactor.dispose()
                    if (receiveTransactor == transactor) {
                        receiveTransactor = null
                    }
                }
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
        grabTransactor?.dispose()
        giftTransactor?.dispose()
        receiveTransactor?.dispose()
    }

    private fun cancelBillTimeout() {
        billDismissTimer?.cancel()
    }
}