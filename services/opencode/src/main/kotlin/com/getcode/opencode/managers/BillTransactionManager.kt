package com.getcode.opencode.managers

import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.MessagingController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.VerifiedFiatCalculator
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.transactors.AccountClusterFactory
import com.getcode.opencode.internal.transactors.BillPresentationData
import com.getcode.opencode.internal.transactors.GiveBillTransactor
import com.getcode.opencode.internal.transactors.GrabBillTransactor
import com.getcode.opencode.internal.transactors.PayloadFactory
import com.getcode.opencode.internal.transactors.ReceiveGiftCardTransactor
import com.getcode.opencode.internal.transactors.SendGiftCardTransactor
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.utils.payment.PaymentTraceRegistry
import com.getcode.utils.timedTraceSuspend
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.schedule
import kotlin.time.Duration

/**
 * Coordinates the four bill transaction flows by owning transactor lifecycles
 * and bridging between the UI layer ([BillController]) and the lower-level
 * transactors.
 *
 * Flows managed:
 * - **Give bill** ([awaitGrabFromRecipient]) — present a cash bill and wait for a scan.
 * - **Grab bill** ([attemptGrabFromSender]) — claim a scanned bill from a sender.
 * - **Send cash link** ([fundGiftCard]) — fund a gift card for remote sending.
 * - **Receive cash link** ([receiveGiftCard]) — claim a gift card via entropy.
 */
@Singleton
class BillTransactionManager @Inject constructor(
    private val accountController: AccountController,
    private val messagingController: MessagingController,
    private val transactionController: TransactionController,
    private val tokenProvider: TokenMetadataProvider,
    private val mnemonicManager: MnemonicManager,
    private val giftCardManager: GiftCardManager,
    private val payloadFactory: PayloadFactory,
    private val accountClusterFactory: AccountClusterFactory,
    private val verifiedFiatCalculator: VerifiedFiatCalculator,
) {
    private var billDismissTimer: TimerTask? = null

    // bills
    private var giveTransactor: GiveBillTransactor? = null
    private var grabTransactor: GrabBillTransactor? = null

    // gifts
    private var giftTransactor: SendGiftCardTransactor? = null
    private var receiveTransactor: ReceiveGiftCardTransactor? = null

    val sharedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Starts the **give** flow: creates a [GiveBillTransactor], generates a
     * rendezvous payload, calls [present] with the [BillPresentationData] so the
     * UI can display the scannable code, then blocks until a recipient grabs it.
     *
     * @param nonce optional nonce to reuse from a prior presentation of the same
     *   bill (e.g. after a cancelled share-sheet).
     * @param present callback invoked with the payload data + nonce for UI display.
     * @param onGrabbed called with the grabbed amount on successful transfer.
     * @param onTimeout called if the bill expires before being grabbed.
     * @param onError called on any transactor failure.
     */
    fun awaitGrabFromRecipient(
        token: Token,
        amount: LocalFiat,
        owner: AccountCluster,
        verifiedState: VerifiedState?,
        billExchangeDataTimeout: Duration?,
        nonce: List<Byte>? = null,
        present: (BillPresentationData) -> Unit,
        onGrabbed: suspend (LocalFiat, Map<String, Long>) -> Unit,
        onTimeout: () -> Unit,
        onError: (Throwable, Map<String, Long>) -> Unit,
    ) {
        giveTransactor?.dispose()

        sharedScope.launch {
            val childScope = CoroutineScope(sharedScope.coroutineContext + Job())

            val transactor = GiveBillTransactor(
                messagingController = messagingController,
                transactionController = transactionController,
                scope = childScope,
                payloadFactory = payloadFactory,
                verifiedFiatCalculator = verifiedFiatCalculator,
            ).apply {
                with(token, amount, owner, billExchangeDataTimeout, verifiedState, nonce)
            }

            giveTransactor = transactor

            present(transactor.presentationData)
            presentBillForGive(onTimeout)

            // If cancelAwaitForGrab() fired between present() and here,
            // bail out before start() reads fields that dispose() may have nulled.
            ensureActive()

            transactor.start()
                .onSuccess {
                    childScope.cancel()
                    val stages = transactor.correlationId
                        ?.let { PaymentTraceRegistry.finish(it, success = true)?.durations() }
                        .orEmpty()
                    onGrabbed(LocalFiat(it.exchangeData), stages)
                    transactionController.updateLimits(owner, force = true)
                }.onFailure { error ->
                    val stages = transactor.correlationId
                        ?.let { PaymentTraceRegistry.finish(it, success = false, error = error)?.durations() }
                        .orEmpty()
                    onError(error, stages)
                    transactor.dispose()
                }
        }
    }

    /**
     * Starts the **grab** flow: creates a [GrabBillTransactor] for the scanned
     * [payload] and attempts to claim the bill from the sender.
     *
     * @param onGrabbed called with the token, amount, and optional verified state
     *   once the grab completes.
     * @param onError called on any transactor failure.
     */
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

                    val token = timedTraceSuspend(
                        message = "post-grab tokenMetadata fetch",
                        tag = "Bill",
                    ) { tokenProvider.getTokenMetadata(mint).getOrNull()?.token }
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

    /**
     * Starts the **send cash link** flow: creates a [SendGiftCardTransactor] and
     * submits a remote-send intent to fund the [giftCard] on-chain.
     *
     * @param onFunded called with the funded amount on success.
     * @param onError called on any transactor failure.
     */
    fun fundGiftCard(
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        owner: AccountCluster,
        token: Token,
        verifiedState: VerifiedState,
        onFunded: suspend (LocalFiat) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        giftTransactor?.dispose()

        sharedScope.launch {
            val transactor = SendGiftCardTransactor(transactionController, payloadFactory).apply {
                with(giftCard, amount, token, owner, verifiedState)
            }
            giftTransactor = transactor

            transactor.start()
                .onSuccess {
                    onFunded(amount)
                    transactionController.updateLimits(owner, force = true)
                }.onFailure {
                    onError(it)
                    transactor.dispose()
                }
        }
    }

    /**
     * Starts the **receive cash link** flow: creates a [ReceiveGiftCardTransactor]
     * and attempts to claim the gift card identified by [entropy].
     *
     * @param claimIfOwned when `true`, allows the issuer to reclaim their own
     *   gift card.
     * @param onReceived called with the token and amount on success.
     * @param onError called on any transactor failure.
     */
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
                giftCardManager = giftCardManager,
                accountClusterFactory = accountClusterFactory,
            )

            runCatching { transactor.with(owner, entropy) }
                .onFailure {
                    onError(it)
                    transactor.dispose()
                    return@launch
                }

            receiveTransactor = transactor

            receiveTransactor?.start(claimIfOwned)
                ?.onSuccess { (token, amount) ->
                    onReceived(token, amount)
                    transactionController.updateLimits(owner, force = true)
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