package com.flipcash.app.session.internal.delegates

import com.flipcash.analytics.State
import com.flipcash.analytics.events.ScanEvents
import com.flipcash.analytics.events.TransferEvents
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.analytics
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.CodeScanEvent
import com.flipcash.app.session.CodeScanOperations
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.WalletRevealCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.internal.transactors.GrabTransactorError
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.util.vibration.Vibrator
import com.getcode.utils.base58
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Implements [CodeScanOperations] — handling QR (Kik Code) scans and grab attempts.
 *
 * When a user scans a cash code:
 * 1. Deduplicates by rendezvous key (prevents double-grabs of the same code).
 * 2. Parses the [OpenCodePayload] to determine the kind (Cash, MultiMintCash).
 * 3. Optionally vibrates on scan (controlled by the `vibrateOnScan` feature flag).
 * 4. Calls [BillController.attemptGrab] to claim the funds.
 * 5. On success: adds the token to the balance and emits [Event.BillReady],
 *    [Event.CheckPendingFeed], and [Event.RefreshFeed] for the shell to route.
 *
 * The rendezvous dedup map is cleared per-key on grab error so the user can retry. Tip
 * payloads carry no grab, so they are deduplicated on a cooldown instead — see
 * [isTipCardOnCooldown].
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class CodeScanDelegate @Inject constructor(
    private val stateHolder: SessionStateHolder,
    private val billController: BillController,
    private val tokenCoordinator: TokenCoordinator,
    private val walletReveal: WalletRevealCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val vibrator: Vibrator,
    private val userManager: UserManager,
    private val dispatchers: DispatcherProvider,
) : CodeScanOperations {

    sealed interface Event {
        data class BillReady(val bill: Scannable.Payable) : Event
        data object RefreshFeed : Event
        data object CheckPendingFeed : Event

        /** A tip [OpenCodePayload] was scanned; the shell resolves & presents the card for [userId]. */
        data class TipCardScanned(val userId: ID) : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    // Separate from [events], which is the shell's single-consumer channel. This one is the
    // scanner UI's, and is replay-less: an event raised with no scanner on screen is dropped.
    private val _codeScanEvents = MutableSharedFlow<CodeScanEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val codeScanEvents: Flow<CodeScanEvent> = _codeScanEvents.asSharedFlow()

    private val scannedRendezvous = mutableMapOf<String, Long>()
    private val scannedTipCards = mutableMapOf<ID, Long>()

    override fun onCameraScanning(scanning: Boolean) {
        stateHolder.update { it.copy(isCameraUp = scanning) }
    }

    override fun onCodeScan(code: ScannableKikCode, fromStillImage: Boolean) {
        if (billController.state.value.bill != null) {
            return
        }

        val payload = (code as? ScannableKikCode.RemoteKikCode)?.payloadId?.toList() ?: return
        val codePayload = OpenCodePayload.fromList(payload)
        if (scannedRendezvous.contains(codePayload.rendezvous.publicKey)) {
            return
        }

        // A tip card is a person rather than a one-shot grab, so it gets a cooldown where cash
        // gets the permanent rendezvous suppression above. Without one the only thing between the
        // camera and a second present is the "a bill is up" check at the top of this function, and
        // the hand-off into the chat drops the bill before it pushes (see TipCardDecorator) — for
        // the length of that transition the card is still in frame with every gate open, and it
        // re-presents over the chat it just opened.
        if (codePayload.kind == PayloadKind.Tip && isTipCardOnCooldown(codePayload.userId)) {
            return
        }

        if (stateHolder.current.vibrateOnScan) {
            vibrator.tick()
        }

        trace(
            tag = "Session",
            message = """
                Kind: ${codePayload.kind}
                Nonce: ${codePayload.nonce.hexEncodedString()}
                Rendezvous: ${codePayload.rendezvous.publicKeyBytes.base58}
            """.trimIndent()
        )

        when (codePayload.kind) {
            PayloadKind.Cash -> onCashScanned(codePayload, fromStillImage)
            PayloadKind.MultiMintCash -> onCashScanned(codePayload, fromStillImage)
            PayloadKind.Tip -> onTipCardScanned(codePayload)
            PayloadKind.Unknown -> Unit
        }
    }

    private fun onCashScanned(payload: OpenCodePayload, fromStillImage: Boolean) {
        scannedRendezvous[payload.rendezvous.publicKey] = Clock.System.now().toEpochMilliseconds()

        trace(
            tag = "Session",
            message = "Scanned: ${payload.fiat!!.quarks} ${payload.fiat!!.currencyCode}"
        )
        val owner = userManager.accountCluster ?: return

        analytics.track(TransferEvents.grabBillStart())
        billController.attemptGrab(
            owner = owner,
            payload = payload,
            onGrabbed = { token, amount, verifiedState ->
                // Take the wallet's "before" picture first: the credit below lands here, at grab
                // time, but the user doesn't reach the wallet until they tap "Put in Wallet". Snapshot
                // it afterwards and there is nothing left for the balance to tick up from.
                walletReveal.capture(token.address)
                tokenCoordinator.add(token, amount)
                val grabStart = scannedRendezvous[payload.rendezvous.publicKey]
                val grabTime = grabStart?.let {
                    Clock.System.now().toEpochMilliseconds() - it
                }

                val bill = Scannable.Payable.forToken(
                    amount = amount,
                    token = token,
                    didReceive = true,
                    verifiedState = verifiedState
                )
                _events.trySend(Event.BillReady(bill))

                analytics.track(TransferEvents.grabBill(State.SUCCESS, amount.analytics, grabTime, null))
                BottomBarManager.clear()
                _events.trySend(Event.CheckPendingFeed)
                _events.trySend(Event.RefreshFeed)
            },
            onError = {
                analytics.track(
                    TransferEvents.grabBill(State.FAILURE, payload.fiat?.analytics, null, it.analytics)
                )
                scannedRendezvous.remove(payload.rendezvous.publicKey)

                // Named rather than folded into the general failure: this is the one grab error
                // that is about the code itself rather than about the network or the account, so
                // it is the one a caller can explain. Gated on the origin of this scan, not on a
                // flag the scanner holds, so a camera frame can never surface a photo's error.
                if (fromStillImage && it is GrabTransactorError.GiveRequestNotFound) {
                    _codeScanEvents.tryEmit(CodeScanEvent.CashCodeNotLive)
                }
            }
        )
    }

    private fun onTipCardScanned(payload: OpenCodePayload) {
        // Tip payloads carry the recipient's user id (see OpenCodePayload layout 2), not a
        // rendezvous grab. Hand the id to the shell, which routes to TipCardOperations to
        // resolve and present the card.
        val userId = payload.userId ?: return
        analytics.track(ScanEvents.tipCardScanned())
        _events.trySend(Event.TipCardScanned(userId))
    }

    /**
     * Whether [userId]'s card was accepted within [TipCardRescanCooldownMillis]. Accepting refreshes
     * the window and a suppressed frame does not, so holding the camera on a card runs the cooldown
     * out rather than extending it indefinitely.
     *
     * Synchronized because the analyzer decodes frames concurrently (each frame gets its own
     * coroutine in MultiCodeAnalyzer), and an unguarded check-then-set lets two of them both pass.
     */
    @Synchronized
    private fun isTipCardOnCooldown(userId: ID?): Boolean {
        userId ?: return false
        val now = Clock.System.now().toEpochMilliseconds()
        val lastScanned = scannedTipCards[userId]
        if (lastScanned != null && now - lastScanned < TipCardRescanCooldownMillis) {
            return true
        }
        scannedTipCards[userId] = now
        return false
    }

    private companion object {
        /**
         * How long the same tip card is ignored after one has been accepted. Long enough to cover
         * the card's beat on screen and the push into the chat behind it, short enough that
         * deliberately scanning the same person again isn't blocked. The same 3s the QR analyzer
         * debounces by, which is what already covers the other scan route to this card.
         */
        const val TipCardRescanCooldownMillis = 3_000L
    }
}
