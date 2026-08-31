package com.flipcash.app.tokens

import androidx.compose.runtime.Immutable
import com.flipcash.libs.coroutines.DispatcherProvider
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
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
 * The reveal ends on the later of two clocks, both started by [arm]: [BillClearDelay] from the tap,
 * so the balance does not move while the bill is still sliding off over it, and [MinimumHold] from
 * the wallet reporting itself drawn ([onDisplayed]), so a tab that took a while to appear still
 * shows the pre-claim figures for a beat rather than rolling them on its first frame. It releases
 * itself either way — an [UnclaimedTimeout] bounds the wait, so a reveal nobody came to collect can
 * never colour a later visit.
 */
@Singleton
class WalletRevealCoordinator @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
    dispatchers: DispatcherProvider,
) {
    private val scope = CoroutineScope(dispatchers.Default + SupervisorJob())

    private var captured: WalletReveal? = null
    private var displayed = CompletableDeferred<Unit>()
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
        displayed = CompletableDeferred()
        _pending.value = snapshot

        release?.cancel()
        release = scope.launch {
            // Both clocks run from here and the reveal ends on the slower of them, so neither the
            // bill still coming down nor a tab that has not drawn yet can eat the animation. The
            // timeout covers the wallet never arriving at all, in which case `displayed` never
            // completes and this would otherwise wait forever.
            withTimeoutOrNull(UnclaimedTimeout) {
                coroutineScope {
                    launch { delay(BillClearDelay) }
                    launch {
                        displayed.await()
                        delay(MinimumHold)
                    }
                }
            }
            _pending.value = null
        }
        return true
    }

    /** Reported by the wallet the first time it draws a pending reveal; starts the [MinimumHold]. */
    fun onDisplayed() {
        if (_pending.value == null) return
        displayed.complete(Unit)
    }

    companion object {
        /**
         * How long the pre-claim picture stays up after the tap, before the balance is allowed to
         * move. The bill returns on a 600 ms slide and the tab crossfades under it over 300 ms;
         * rolling the total while that is still happening spends the animation where it cannot be
         * seen. Matches iOS's own `depositRevealDelay`.
         */
        val BillClearDelay = 450.milliseconds

        /**
         * The least time the wallet holds the pre-claim figures once it has actually drawn them,
         * for the case where it took longer than [BillClearDelay] to appear. Without it a slow tab
         * would arrive on a number that rolls on the same frame, which reads as a glitch rather
         * than as money landing.
         */
        val MinimumHold = 150.milliseconds

        /** Fuse for a reveal nobody came to collect (the user backed out before the wallet drew). */
        val UnclaimedTimeout = 3.seconds
    }
}
