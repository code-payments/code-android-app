package com.flipcash.app.messenger.internal.sendback

import com.flipcash.analytics.SendContext
import com.flipcash.analytics.State as AnalyticsState
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.messenger.internal.payment.StartChattingPayer
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.CashMessageReturnStatus
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.paymentrequest.PaymentIntentId
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.SendBackRow
import com.flipcash.shared.payments.sendback.SendBackPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.LiveSecrets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Send Back for one open DM: returning its opening payment to the person who paid it.
 *
 * The transcript [note]s each cash message as it maps it. From that, [rows] says what each bubble
 * draws under its lines: a return in the transcript marks the message it names Sent Back, for both
 * people; an offered message the viewer received gets one status check per DM open, and its row
 * follows that answer and the viewer's taps.
 *
 * The server's `payment_intent_id` lives in [intentIds] only, for as long as this DM is open. It is
 * never put in [rows] or [sheet], so it can't reach a log through a state's toString, and it is
 * held in [LiveSecrets] from the moment it arrives.
 */
internal class SendBackFlow(
    private val scope: CoroutineScope,
    private val payments: SendBackPaymentDelegate,
    private val returnStatus: suspend (ChatId, Long) -> Result<CashMessageReturnStatus>,
    private val payer: StartChattingPayer,
    private val tokenCoordinator: TokenCoordinator,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    private val analytics: FlipcashAnalytics,
    /** The open DM, or null before it resolves. */
    private val chatId: () -> ChatId?,
    /** The DM peer's name as the chat title shows it. */
    private val peerName: () -> String?,
    /** Where "Add Money" goes. */
    private val openScreen: (AppRoute) -> Unit,
) {
    /** What the confirm sheet draws. No intent id: see the class comment. */
    data class Sheet(
        val messageId: Long,
        val amount: Fiat,
        val token: Token,
        val recipientName: String?,
    )

    /** An offered payment the viewer received, as much of it as a return needs. */
    private data class Offer(val messageId: Long, val amount: Fiat, val mint: Mint, val payee: ID)

    private val lock = Any()
    private val offers = mutableMapOf<Long, Offer>()
    private val checked = mutableSetOf<Long>()
    private val intentIds = mutableMapOf<Long, PaymentIntentId>()

    /** Ids of messages a return in the transcript names. */
    private val returned = MutableStateFlow<Set<Long>>(emptySet())

    /** The row each offered message's status and the viewer's taps have put it in. */
    private val local = MutableStateFlow<Map<Long, SendBackRow>>(emptyMap())

    val rows: StateFlow<Map<Long, SendBackRow>> =
        combine(local, returned) { byState, byTranscript ->
            byState + byTranscript.associateWith { SendBackRow.SentBack }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    /** A tap or swipe is between its balance check and its outcome. */
    private var busy = false

    /**
     * Called for each cash message as the transcript maps it. Pages load newest first, and a return
     * is always later in the DM than what it returns, so a loaded return is noted before its
     * original: the status check below is skipped for an original whose return is already here.
     */
    fun note(messageId: Long, cash: MessageContent.Cash, senderId: ID?, isFromSelf: Boolean) {
        cash.paymentReturn?.returnOf?.let { of ->
            if (of.messageId !in returned.value) returned.update { it + of.messageId }
        }
        if (cash.paymentReturn?.offer == null || isFromSelf || senderId == null) return
        val self = userManager.accountId ?: return
        // The offer is on the sender's copy too; only its single receiver may send it back.
        if (cash.receiver != self) return
        val chat = chatId() ?: return
        synchronized(lock) {
            if (!checked.add(messageId)) return
            offers[messageId] = Offer(messageId, cash.amount, cash.mint, senderId)
        }
        scope.launch { checkStatus(chat, messageId) }
    }

    private suspend fun checkStatus(chat: ChatId, messageId: Long) {
        if (messageId in returned.value) return
        // A failure leaves the row hidden, with no error; the next DM open asks again.
        val status = returnStatus(chat, messageId).getOrNull() ?: return
        when (status.status) {
            CashMessageReturnStatus.Status.AVAILABLE -> {
                status.paymentIntentId?.let { keep(messageId, it) }
                setRowIfUnset(messageId, SendBackRow.Available)
            }
            CashMessageReturnStatus.Status.RETURNED -> setRow(messageId, SendBackRow.SentBack)
            CashMessageReturnStatus.Status.UNKNOWN -> Unit
        }
    }

    /** The receiver tapped Send Back under [messageId]. */
    fun tap(messageId: Long) {
        if (rows.value[messageId] != SendBackRow.Available || _sheet.value != null) return
        val offer = synchronized(lock) { offers[messageId] } ?: return
        if (!claim()) return
        scope.launch {
            try {
                val token = tokenFor(offer.mint)
                val hasBalance = token?.let { checkBalance(offer, it) }?.hasBalance
                if (token == null || hasBalance == null) {
                    // Not a tap the funnel counts: it reached neither the sheet nor the modal.
                    presentRateUnavailable()
                    return@launch
                }
                analytics.track(TransferEvents.sendBackTapped(hasBalance = hasBalance))
                if (hasBalance) {
                    _sheet.value = Sheet(messageId, offer.amount, token, peerName())
                } else {
                    presentInsufficientBalance()
                }
            } finally {
                busy = false
            }
        }
    }

    /**
     * The swipe. The balance can change while the sheet is up, so it is checked again, and the
     * amount that check priced is the one paid.
     */
    fun confirm() {
        val open = _sheet.value ?: return
        val offer = synchronized(lock) { offers[open.messageId] } ?: return
        if (!claim()) return
        scope.launch {
            val check = checkBalance(offer, open.token)
            _sheet.value = null
            if (check?.hasBalance != true) {
                busy = false
                if (check != null) presentInsufficientBalance() else presentRateUnavailable()
                return@launch
            }
            setRow(offer.messageId, SendBackRow.Sending)
            try {
                submit(offer, open.token, check.priced)
            } finally {
                busy = false
            }
        }
    }

    fun dismiss() {
        _sheet.value = null
    }

    private suspend fun submit(offer: Offer, token: Token, priced: VerifiedFiat) {
        val id = offer.messageId
        val chat = chatId()
        val owner = userManager.accountCluster
        if (chat == null || owner == null) {
            failed(offer, IllegalStateException("no chat or account"))
            return
        }
        val intentId = synchronized(lock) { intentIds[id] } ?: run {
            val status = returnStatus(chat, id).getOrNull()
            if (status?.status == CashMessageReturnStatus.Status.RETURNED) {
                alreadyReturned(offer)
                return
            }
            status?.paymentIntentId?.takeIf { status.status == CashMessageReturnStatus.Status.AVAILABLE }
                ?.also { keep(id, it) }
        }
        if (intentId == null) {
            failed(offer, IllegalStateException("no payment intent id"))
            return
        }

        payments.pay(
            chatId = chat,
            messageId = id,
            payee = offer.payee,
            priced = priced,
            token = token,
            source = owner.withTimelockForToken(token),
            intentId = intentId,
        ).onSuccess {
            setRow(id, SendBackRow.SentBack)
            analytics.track(sentCash(AnalyticsState.SUCCESS, offer, null))
        }.onFailure { cause ->
            // The outcome is unknown: a second tap or another device may have got there first.
            val after = returnStatus(chat, id).getOrNull()
            if (after?.status == CashMessageReturnStatus.Status.RETURNED) {
                alreadyReturned(offer)
            } else {
                failed(offer, cause)
            }
        }
    }

    private fun alreadyReturned(offer: Offer) {
        setRow(offer.messageId, SendBackRow.SentBack)
        analytics.track(sentCash(AnalyticsState.FAILURE, offer, ALREADY_RETURNED))
    }

    private fun failed(offer: Offer, cause: Throwable) {
        setRow(offer.messageId, SendBackRow.Available)
        analytics.track(sentCash(AnalyticsState.FAILURE, offer, cause.analytics))
        presentFailed()
    }

    private fun sentCash(state: AnalyticsState, offer: Offer, error: String?) =
        TransferEvents.sentCash(state, offer.amount.analytics, error, SendContext.SEND_BACK)

    /** [offer] priced once at today's rate, and whether the viewer holds that much. */
    private class BalanceCheck(val priced: VerifiedFiat, val hasBalance: Boolean)

    /**
     * Prices [offer] and compares the token quarks it needs with the quarks the viewer holds,
     * exactly. Null when that can't be decided: no fresh rate to price it, or no quark balance
     * loaded yet. A fiat comparison is no stand-in, since rounding passes a balance just short.
     */
    private suspend fun checkBalance(offer: Offer, token: Token): BalanceCheck? {
        val priced = payments.price(offer.amount, token).getOrNull() ?: return null
        val needed = priced.localFiat.underlyingTokenAmount.quarks
        val held = tokenCoordinator.heldQuarksForToken(offer.mint).first() ?: return null
        return BalanceCheck(priced, held >= needed)
    }

    private suspend fun tokenFor(mint: Mint): Token? =
        tokenCoordinator.cachedToken(mint) ?: tokenCoordinator.getTokenMetadata(mint).getOrNull()?.token

    private fun keep(messageId: Long, intentId: PaymentIntentId) {
        LiveSecrets.hold(PublicKey(intentId.bytes.toList()).base58())
        synchronized(lock) { intentIds[messageId] = intentId }
    }

    private fun claim(): Boolean = synchronized(lock) {
        if (busy) false else true.also { busy = true }
    }

    private fun setRow(messageId: Long, row: SendBackRow) {
        local.update { it + (messageId to row) }
    }

    private fun setRowIfUnset(messageId: Long, row: SendBackRow) {
        local.update { if (messageId in it) it else it + (messageId to row) }
    }

    private fun presentInsufficientBalance() {
        payer.presentInsufficientBalance(onAddMoney = ::presentDeposit)
    }

    private fun presentDeposit() {
        scope.launch { payer.depositRoute()?.let(openScreen) }
    }

    private fun presentRateUnavailable() {
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_staleRates),
            message = resources.getString(R.string.error_description_staleRates),
        )
    }

    /** A submit that failed, the return still not made (test case 15). */
    private fun presentFailed() {
        BottomBarManager.showError(
            title = resources.getString(R.string.error_title_couldntSendBack),
            message = resources.getString(R.string.error_description_couldntSendBack),
        )
    }

    private companion object {
        /** `Sent Cash`'s error when the payment had already been returned. */
        const val ALREADY_RETURNED = "Already Returned"
    }
}
