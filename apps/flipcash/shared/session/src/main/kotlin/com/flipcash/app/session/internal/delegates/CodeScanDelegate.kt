package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.CodeScanOperations
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.util.vibration.Vibrator
import com.getcode.libs.code.detection.NativeScanSample
import com.getcode.utils.base58
import com.getcode.utils.hexEncodedString
import com.getcode.utils.payment.PaymentFlow
import com.getcode.utils.payment.PaymentTraceRegistry
import com.getcode.utils.trace
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
 * The rendezvous dedup map is cleared per-key on grab error so the user can retry.
 *
 * @see com.flipcash.app.session.internal.RealSessionController
 */
@Singleton
class CodeScanDelegate @Inject constructor(
    private val stateHolder: SessionStateHolder,
    private val billController: BillController,
    private val tokenCoordinator: TokenCoordinator,
    private val analytics: FlipcashAnalyticsService,
    private val vibrator: Vibrator,
    private val userManager: UserManager,
    private val dispatchers: DispatcherProvider,
) : CodeScanOperations {

    sealed interface Event {
        data class BillReady(val bill: Bill) : Event
        data object RefreshFeed : Event
        data object CheckPendingFeed : Event
    }

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.consumeAsFlow()

    private val scannedRendezvous = mutableMapOf<String, Long>()

    override fun onCameraScanning(scanning: Boolean) {
        stateHolder.update { it.copy(isCameraUp = scanning) }
    }

    override fun onCodeScan(code: ScannableKikCode, nativeScan: NativeScanSample?) {
        if (billController.state.value.bill != null) {
            return
        }

        val payload = (code as? ScannableKikCode.RemoteKikCode)?.payloadId?.toList() ?: return
        val codePayload = OpenCodePayload.fromList(payload)
        if (scannedRendezvous.contains(codePayload.rendezvous.publicKey)) {
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
            PayloadKind.Cash -> onCashScanned(codePayload, nativeScan)
            PayloadKind.MultiMintCash -> onCashScanned(codePayload, nativeScan)
            PayloadKind.Unknown -> Unit
        }
    }

    private fun onCashScanned(payload: OpenCodePayload, nativeScan: NativeScanSample?) {
        scannedRendezvous[payload.rendezvous.publicKey] = Clock.System.now().toEpochMilliseconds()
        val paymentTrace = PaymentTraceRegistry.open(payload.rendezvous.publicKey, PaymentFlow.Grab)
        nativeScan?.let { sample ->
            paymentTrace.mark(
                name = "nativeScan",
                durationMs = sample.durationMs,
                metadata = mapOf(
                    "width" to sample.width,
                    "height" to sample.height,
                    "quality" to sample.quality,
                ),
            )
        }

        trace(
            tag = "Session",
            message = "Scanned: ${payload.fiat!!.quarks} ${payload.fiat!!.currencyCode}"
        )
        val owner = userManager.accountCluster ?: return

        analytics.transferStart(Analytics.Transfer.Initiate.GrabBillStart)
        billController.attemptGrab(
            owner = owner,
            payload = payload,
            onGrabbed = { token, amount, verifiedState ->
                tokenCoordinator.add(token, amount)
                val grabStart = scannedRendezvous[payload.rendezvous.publicKey]
                val grabTime = grabStart?.let {
                    Clock.System.now().toEpochMilliseconds() - it
                }

                val bill = Bill.Cash(
                    amount = amount,
                    token = token,
                    didReceive = true,
                    verifiedState = verifiedState
                )
                _events.trySend(Event.BillReady(bill))

                val stages = PaymentTraceRegistry.finish(payload.rendezvous.publicKey, success = true)
                    ?.durations()
                    ?: emptyMap()
                analytics.transfer(Analytics.Transfer.GrabBill(time = grabTime, stages = stages), amount)
                BottomBarManager.clear()
                _events.trySend(Event.CheckPendingFeed)
                _events.trySend(Event.RefreshFeed)
            },
            onError = {
                val stages = PaymentTraceRegistry.finish(payload.rendezvous.publicKey, success = false, error = it)
                    ?.durations()
                    ?: emptyMap()
                analytics.transfer(
                    event = Analytics.Transfer.GrabBill(stages = stages),
                    fiat = payload.fiat,
                    successful = false,
                    error = it
                )
                scannedRendezvous.remove(payload.rendezvous.publicKey)
            }
        )
    }
}
