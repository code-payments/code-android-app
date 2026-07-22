package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.BillDeterminationResult
import com.flipcash.app.session.Grabbed
import com.flipcash.app.session.PutInWallet
import com.flipcash.app.session.internal.toast.SessionToastController
import com.flipcash.app.shareable.ShareConfirmationResult
import com.flipcash.app.shareable.ShareResult
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.app.shareable.ShareableConfirmationController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.core.R
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Internal delegate that manages the "Send as Link" gift-card flow.
 *
 * Unlike the three `by`-delegated interfaces, this class is **not** exposed on the
 * public [SessionController] API — it is an internal worker invoked by the shell when
 * [BillPresentationDelegate] emits [BillPresentationDelegate.Event.SendAsLinkRequested].
 *
 * The flow:
 * 1. Creates a [GiftCardAccount] and presents the share sheet.
 * 2. If the user shares: funds the gift card on-chain, waits for confirmation, then
 *    emits [Event.DismissBill] and [Event.RefreshFeed].
 * 3. If the user cancels the share sheet without sharing: emits
 *    [Event.RestartBillGrab] so the shell can re-enter the "await grab" flow.
 * 4. If the user cancels the confirmation: prompts "Are you sure?", and on
 *    confirmation cancels the remote send and returns funds.
 *
 * Funding is guarded by [giftCardFundingInProgress] (`AtomicBoolean`) to prevent
 * double-funding from rapid taps.
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class GiftCardSharingDelegate @Inject constructor(
    private val billController: BillController,
    private val shareSheetController: ShareSheetController,
    private val shareConfirmationController: ShareableConfirmationController,
    private val toastController: SessionToastController,
    private val tokenCoordinator: TokenCoordinator,
    private val transactionController: TransactionController,
    private val analytics: FlipcashAnalyticsService,
    private val vibrator: Vibrator,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) {

    sealed interface Event {
        data class DismissBill(val action: BillDeterminationResult) : Event
        data class RestartBillGrab(val bill: Scannable.Payable, val owner: AccountCluster) : Event
        data object RefreshFeed : Event
    }

    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    private val giftCardFundingInProgress = AtomicBoolean(false)

    fun shareGiftCard(bill: Scannable.Payable, owner: AccountCluster) {
        val amount = bill.amount
        val token = bill.token
        val verifiedState = bill.verifiedState!!

        val giftCard = GiftCardAccount.create(token)
        val shareable = Shareable.CashLink(
            giftCardAccount = giftCard,
            amount = amount,
            autoConfirmationAfter = 60.seconds
        )

        scope.launch {
            shareSheetController.onShared = onShared@{ result ->
                when (result) {
                    is ShareResult.ActionTaken -> {
                        if (!giftCardFundingInProgress.compareAndSet(false, true)) return@onShared
                        scope.launch action@{
                            val fundingResult = initiateGiftCardFunding(
                                giftCard = giftCard,
                                owner = owner,
                                amount = amount,
                                token = token,
                                verifiedState = verifiedState
                            )
                            if (fundingResult.isFailure) {
                                return@action
                            }

                            shareSheetController.reset(setChecked = true)

                            delay(CASH_LINK_CONFIRMATION_DELAY)

                            confirmGiftCardSent(
                                owner = owner,
                                giftCard = giftCard,
                                amount = amount,
                                shareable = shareable,
                                result = result
                            )
                        }.invokeOnCompletion { giftCardFundingInProgress.set(false) }
                    }

                    ShareResult.NotShared -> {
                        trace(
                            tag = "Session",
                            message = "Cash link not sent. Restarting awaiting grab",
                            type = TraceType.User,
                        )
                        val currentBill = billController.state.value.bill as? Scannable.Payable ?: bill
                        _events.trySend(Event.RestartBillGrab(currentBill, owner))
                    }
                }
            }
            shareSheetController.present(shareable)
        }
    }

    private suspend fun confirmGiftCardSent(
        owner: AccountCluster,
        giftCard: GiftCardAccount,
        amount: LocalFiat,
        shareable: Shareable,
        result: ShareResult.ActionTaken,
    ) {
        val confirmResult =
            shareConfirmationController.confirm(shareable, result)

        shareSheetController.reset(setChecked = false)

        when (confirmResult) {
            ShareConfirmationResult.Cancelled -> {
                BottomBarManager.showAlert(
                    title = resources.getString(R.string.prompt_title_cancelCashLinkPostShare),
                    message = resources.getString(R.string.prompt_description_cancelCashLinkPostShare),
                    actions = listOf(
                        BottomBarAction(
                            text = resources.getString(R.string.action_yes),
                            onClick = {
                                scope.launch {
                                    cancelGiftCard(owner, giftCard)
                                }
                            }
                        ),
                        BottomBarAction(
                            text = resources.getString(R.string.action_nevermind),
                            style = BottomBarManager.BottomBarButtonStyle.Text,
                            onClick = {
                                scope.launch {
                                    confirmGiftCardSent(
                                        owner,
                                        giftCard,
                                        amount,
                                        shareable,
                                        result
                                    )
                                }
                            }
                        )
                    ),
                    isDismissible = false,
                    showCancel = false,
                    showScrim = false,
                )
            }

            is ShareConfirmationResult.Confirmed -> {
                when (result) {
                    ShareResult.CopiedToClipboard -> {
                        toastController.enqueue(amount, isDeposit = false)
                        _events.trySend(Event.DismissBill(Grabbed))
                        vibrator.vibrate()
                        _events.trySend(Event.RefreshFeed)
                        analytics.transfer(Analytics.Transfer.SentCashLink.Clipboard, amount)
                        trace(
                            tag = "Session",
                            message = "Cash link copied",
                            metadata = {
                                "underlying quarks" to amount.underlyingTokenAmount.quarks
                                "native amount" to amount.nativeAmount.formatted()
                                "fx" to amount.rate.fx
                                "currency" to amount.rate.currency.name
                                "token mint" to amount.mint
                            },
                            type = TraceType.User,
                        )
                    }

                    is ShareResult.SharedToApp -> {
                        toastController.enqueue(amount, isDeposit = false)
                        _events.trySend(Event.DismissBill(Grabbed))
                        vibrator.vibrate()
                        _events.trySend(Event.RefreshFeed)

                        analytics.transfer(
                            event = Analytics.Transfer.SentCashLink.App(name = result.to),
                            amount = amount
                        )

                        trace(
                            tag = "Session",
                            message = "Cash link shared",
                            metadata = {
                                "target" to result.to
                                "underlying quarks" to amount.underlyingTokenAmount.quarks
                                "native amount" to amount.nativeAmount.formatted()
                                "fx" to amount.rate.fx
                                "currency" to amount.rate.currency.name
                                "token mint" to amount.mint
                            },
                            type = TraceType.User,
                        )
                    }
                }
            }
        }
    }

    private suspend fun initiateGiftCardFunding(
        giftCard: GiftCardAccount,
        owner: AccountCluster,
        amount: LocalFiat,
        token: Token,
        verifiedState: VerifiedState,
    ): Result<LocalFiat> = suspendCancellableCoroutine { cont ->
        billController.fundGiftCard(
            giftCard = giftCard,
            amount = amount,
            token = token,
            owner = owner,
            verifiedState = verifiedState,
            onFunded = {
                tokenCoordinator.subtract(token, amount)
                shareSheetController.reset()
                cont.resume(Result.success(it))
            },
            onError = {
                _events.trySend(Event.DismissBill(PutInWallet))
                BottomBarManager.showError(
                    title = resources.getString(R.string.error_title_failedToCreateGiftCard),
                    message = resources.getString(R.string.error_description_failedToCreateGiftCard)
                )
                cont.resume(Result.failure(it))
            }
        )
    }

    private suspend fun cancelGiftCard(
        owner: AccountCluster,
        giftCard: GiftCardAccount
    ) {
        transactionController.cancelRemoteSend(
            vault = giftCard.cluster.vaultPublicKey,
            owner = owner,
        ).onFailure {
            _events.trySend(Event.DismissBill(PutInWallet))
        }.onSuccess {
            tokenCoordinator.update()
            _events.trySend(Event.DismissBill(PutInWallet))
        }
    }
}

private val CASH_LINK_CONFIRMATION_DELAY = 500.milliseconds
