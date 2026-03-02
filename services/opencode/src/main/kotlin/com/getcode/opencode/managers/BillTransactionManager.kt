package com.getcode.opencode.managers

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.transactors.GiveBillTransactor
import com.getcode.opencode.internal.transactors.GrabBillTransactor
import com.getcode.opencode.internal.transactors.ReceiveGiftCardTransactor
import com.getcode.opencode.internal.transactors.SendGiftCardTransactor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.utils.ErrorUtils
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration

@Singleton
class BillTransactionManager @Inject constructor(
    private val accountController: AccountController,
    private val currencyController: CurrencyController,
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val tokenProvider: TokenMetadataProvider,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
    private val verifiedProtoManager: VerifiedProtoManager,
) {
    private var billDismissTimer: TimerTask? = null

    // bills
    private var giveTransactor: GiveBillTransactor? = null
    private var grabTransactor: GrabBillTransactor? = null

    // gifts
    private var giftTransactor: SendGiftCardTransactor? = null
    private var receiveTransactor: ReceiveGiftCardTransactor? = null

    val sharedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun awaitGrabFromRecipient(
        token: Token,
        amount: LocalFiat,
        owner: AccountCluster,
        verifiedState: VerifiedState?,
        billExchangeDataTimeout: Duration?,
        present:  (List<Byte>) -> Unit,
        onGrabbed: suspend (LocalFiat) -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        giveTransactor?.dispose()

        sharedScope.launch {
            val childScope = CoroutineScope(sharedScope.coroutineContext + Job())

            val transactor = GiveBillTransactor(
                currencyController = currencyController,
                messagingController = messagingController,
                transactionController = transactionController,
                scope = childScope,
                verifiedProtoManager = verifiedProtoManager,
            ).apply {
                with(token, amount, owner, billExchangeDataTimeout, verifiedState)
            }

            giveTransactor = transactor

            present(transactor.data)
            presentBillForGive(onTimeout)

            transactor.start()
                .onSuccess {
                    childScope.cancel()
                    onGrabbed(LocalFiat(it.exchangeData))
                    transactionController.updateLimits(owner, force = true)
                }.onFailure {
                    onError(it)
                    transactor.dispose()
                }
        }
    }

    fun attemptGrabFromSender(
        owner: AccountCluster,
        payload: OpenCodePayload,
        onGrabbed: suspend (Token, LocalFiat, VerifiedState?) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        grabTransactor?.dispose()

        sharedScope.launch {
            val childScope = CoroutineScope(sharedScope.coroutineContext + Job())
            val transactor =
                GrabBillTransactor(
                    accountController = accountController,
                    messagingController,
                    transactionController,
                    tokenProvider,
                    childScope
                ).apply {
                    with(owner, payload)
                }

            grabTransactor = transactor

            transactor.start()
                .onSuccess { metadata ->
                    childScope.cancel()
                    trace(
                        tag = "Bill",
                        message = "attemptGrabFromSender: ${metadata.javaClass.simpleName} => ${metadata.exchangeData}"
                    )

                    val mint = metadata.exchangeData.mint
                    val amount = LocalFiat(metadata.exchangeData)

                    val token = tokenProvider.getTokenMetadata(mint).getOrNull()?.token
                    if (token == null) {
                        onError(IllegalStateException("No metadata found for token $mint"))
                        return@onSuccess
                    }

                    trace(
                        tag = "Bill",
                        message = "Grabbed ${amount.nativeAmount.formatted()} of ${token.symbol} from sender"
                    )

                    val verifiedState = metadata.verifiedExchangeData?.verifiedState
                    onGrabbed(token, amount, verifiedState)
                    sharedScope.launch {
                        transactionController.updateLimits(owner, force = true)
                    }
                }.onFailure {
                    onError(it)
                    transactor.dispose()
                }
        }
    }

    fun fundGiftCard(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        owner: AccountCluster,
        token: Token,
        onFunded: suspend (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        giftTransactor?.dispose()

        sharedScope.launch {
            val transactor = SendGiftCardTransactor(transactionController).apply {
                with(giftCard, amount, token, owner)
            }
            giftTransactor = transactor

            transactor.start()
                .onSuccess {
                    onFunded(amount)
                    transactionController.updateLimits(owner, force = true)
                }.onFailure {
                    ErrorUtils.handleError(it)
                    onError(it)
                    transactor.dispose()
                }
        }
    }

    fun receiveGiftCard(
        owner: AccountCluster,
        entropy: String,
        claimIfOwned: Boolean,
        onReceived: suspend (Token, LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        receiveTransactor?.dispose()

        sharedScope.launch {
            val transactor = ReceiveGiftCardTransactor(
                accountController = accountController,
                transactionController = transactionController,
                tokenProvider = tokenProvider,
                mnemonicManager = mnemonicManager,
                giftCardManager = giftCardManager
            ).apply {
                with(owner, entropy)
            }

            receiveTransactor = transactor

            receiveTransactor?.start(claimIfOwned)
                ?.onSuccess { (token, amount) ->
                    onReceived(token, amount)
                }?.onFailure {
                    onError(it)
                    transactor.dispose()
                }
        }
    }

    private fun presentBillForGive(onTimeout: () -> Unit) {
        cancelBillTimeout()
        billDismissTimer = Timer().schedule((1000 * 50).toLong()) {
            onTimeout()
        }
    }

    fun cancelAwaitForGrab() {
        cancelBillTimeout()
        giveTransactor?.dispose()
        giveTransactor = null
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