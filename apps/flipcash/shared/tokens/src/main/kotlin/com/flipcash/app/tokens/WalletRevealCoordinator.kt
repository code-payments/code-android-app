package com.flipcash.app.tokens

import androidx.compose.runtime.Immutable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The wallet as it stood immediately before a claim landed.
 *
 * @param mint the token that was claimed.
 * @param isNewToken whether the wallet held no displayable balance in [mint] before the claim —
 *   i.e. whether the card deck is about to gain a card rather than update one.
 * @param totalBefore the summed balance across every token, USD-denominated like
 *   [TokenCoordinator.observeTotalBalance].
 * @param mintBalanceBefore [mint]'s own balance, so its card rolls in step with the total rather
 *   than showing the post-claim number beside a pre-claim one.
 */
@Immutable
data class WalletReveal(
    val mint: Mint,
    val isNewToken: Boolean,
    val totalBefore: Fiat,
    val mintBalanceBefore: Fiat,
)

/**
 * Carries the pre-claim wallet across the "Put in Wallet" hand-off, so the tab the user lands on can
 * show the money arriving instead of a total that has already moved.
 *
 * Both claim paths — a scanned bill ([com.flipcash.app.session] `CodeScanDelegate`) and a cash link
 * — credit the balance the moment the funds are grabbed, which is well before the user taps through
 * the confirmation. By then the wallet's own flows already carry the new number, so there is nothing
 * left to animate. [capture] takes the "before" picture at the point of credit; [arm] publishes it
 * only when the user actually asks to be taken to the wallet.
 *
 * The reveal releases itself, so a stale one can never colour a later visit: [arm] starts a
 * [UnclaimedTimeout] fuse for the case where the wallet never appears, and [onDisplayed] — reported
 * by the wallet once it is drawn — replaces that with the short [HoldDuration] the animation runs
 * off. Timing the hold from the screen rather than from the tap keeps a slow entry from eating it.
 */
@Singleton
class WalletRevealCoordinator @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(dispatchers.Default + SupervisorJob())

    private var captured: WalletReveal? = null
    private var held = false
    private var release: Job? = null

    private val _pending = MutableStateFlow<WalletReveal?>(null)

    /** The reveal the wallet should currently be rendering, or null to render live values. */
    val pending: StateFlow<WalletReveal?> = _pending.asStateFlow()

    /**
     * Snapshots the wallet before [mint]'s incoming balance is applied. Call this *immediately*
     * before crediting — a snapshot taken afterwards is just the post-claim state.
     */
    fun capture(mint: Mint) {
        captured = WalletReveal(
            mint = mint,
            isNewToken = !tokenCoordinator.holdsDisplayableBalance(mint),
            totalBefore = tokenCoordinator.currentTotalBalance(),
            mintBalanceBefore = tokenCoordinator.currentBalance(mint),
        )
    }

    /**
     * Publishes the captured snapshot, and reports whether there was one. Nothing is captured
     * unless the funds came from a scanned bill, so a `false` return is also the answer to
     * "should the caller take the user to their wallet?".
     */
    fun arm(): Boolean {
        val snapshot = captured ?: return false
        captured = null
        held = false
        _pending.value = snapshot
        releaseAfter(UnclaimedTimeout)
        return true
    }

    /** Reported by the wallet the first time it draws a pending reveal; starts the hold. */
    fun onDisplayed() {
        if (_pending.value == null || held) return
        held = true
        releaseAfter(HoldDuration)
    }

    private fun releaseAfter(duration: Duration) {
        release?.cancel()
        release = scope.launch {
            delay(duration)
            _pending.value = null
        }
    }

    companion object {
        /**
         * How long the pre-claim picture stays up once the wallet is on screen. Long enough to read
         * as a starting value rather than a flicker, short enough that the tab doesn't feel stalled.
         */
        val HoldDuration = 450.milliseconds

        /** Fuse for a reveal nobody came to collect (the user backed out before the wallet drew). */
        val UnclaimedTimeout = 3.seconds
    }
}
