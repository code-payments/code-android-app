package com.flipcash.app.session

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.session.BillDeterminationResult.ActedUpon
import com.getcode.opencode.model.financial.Token
import com.flipcash.app.core.AppRoute
import com.getcode.opencode.model.core.ID
import com.getcode.ui.core.RestrictionType
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface BillDeterminationResult {
    data object None : BillDeterminationResult
    sealed interface ActedUpon
}

data object Grabbed : BillDeterminationResult, ActedUpon
data object PutInWallet : BillDeterminationResult, ActedUpon

interface BillOperations {
    val billState: StateFlow<BillState>
    fun showBill(bill: Scannable.Payable)
    fun dismissBill(action: BillDeterminationResult)

    /**
     * The user accepted funds they just received ("Put in Wallet"). Dismisses the bill and arms the
     * wallet reveal, so the tab they land on can tick the balance up from where it stood before the
     * claim rather than opening on a number that already moved.
     *
     * Separate from `dismissBill(PutInWallet)` because that same result also covers a grab timeout,
     * a cancel, and a swipe-away — none of which are the user asking to be shown their wallet.
     *
     * Returns whether the caller should take the user to their wallet. Only a scanned bill is
     * snapshotted, so a cash link, claimed from a link rather than from the scanner, dismisses
     * without routing.
     */
    fun claimReceivedFunds(): Boolean
}

/** One-shot signals from a scanned code that only the UI can act on. */
sealed interface CodeScanEvent {
    /**
     * A decoded cash code had no give request behind it — whoever showed it is no longer showing
     * it.
     *
     * The camera path ignores this, and should: a stale frame is one of sixty a second, and the
     * next one may be live, so a banner there would fire on a code the user is still pointing at.
     * A picked photo is read once and cannot be re-read, so the gallery path is the only caller
     * that has to say why nothing happened.
     */
    data object CashCodeNotLive : CodeScanEvent
}

interface CodeScanOperations {
    /**
     * Hot and replay-less, like [TipCardOperations.tipCardEvents]: an event with no scanner on
     * screen is dropped.
     */
    val codeScanEvents: Flow<CodeScanEvent>

    fun onCameraScanning(scanning: Boolean)
    fun onCodeScan(code: ScannableKikCode)
}

interface CashLinkOperations {
    fun openCashLink(cashLink: String?)
}

/**
 * A claim attempt that has finished, and whether the link was collected.
 *
 * [collected] is false for every way a claim can fail, which folds two cases a listener would
 * otherwise have to name itself: collecting back a link you sent yourself, and a link someone else
 * already took. Both arrive as errors, so neither reads as a collection.
 */
data class SettledClaim(
    val entropy: String,
    val collected: Boolean,
)

/**
 * Cash links whose claim attempt has settled, named by entropy.
 *
 * Deliberately not part of [CashLinkOperations]. A surface that *draws* a link's claim state needs
 * to know when that state has moved, and must not be able to move it — chat renders cash links as
 * cards, and the card path is barred from the claim path. Handing chat the operations interface
 * would put `openCashLink` back within its reach.
 *
 * Emitted on every settled attempt, not only a successful one: "already claimed" and "expired" are
 * the server correcting what the caller believed about the link, which is exactly the case a stale
 * card is in. A failure that moved nothing costs the listener one repeated query, and
 * [SettledClaim.collected] separates the two for a listener that has to do more than re-query.
 *
 * Hot and replay-less, like [TipCardOperations.tipCardEvents]: a claim with nothing listening is a
 * claim no rendered card was stale for.
 *
 * Only claims made *here*, which is the limit worth naming. A link collected on someone else's
 * device is invisible to this one — no push category names a claim, and the feed refresh a claim
 * triggers is the wallet's, not a chat's — so a surface that must stay honest about a link it did
 * not claim has to ask on a timer instead (`ChatViewModel.refreshLinkCards`).
 *
 * Chat closes the near half of that without the server: the reader taps a voucher *in a transcript*,
 * so that transcript knows which message carried the entropy, and on a claim that landed it replies
 * to the voucher with a thank-you (`ChatViewModel.initClaimReplies`). The other participants are
 * told by the reply rather than by a notification nobody wrote, and the sender reads that their cash
 * was collected. What stays open is a link claimed with no chat in front of it — pasted, scanned,
 * forwarded out of the chat it came from — because [CashLinkOperations.openCashLink] takes an entropy
 * and nothing else, so the client has no chat to write to. The server does, from the message that
 * carried the entropy, and that is what would retire the timer.
 *
 * Either way this flow is the seam, and it is needed even for the reply: an arriving message re-maps
 * the transcript, but the resolver would hand the same memoized `Claimable` back, so the card only
 * moves if something names the entropy. This flow names it, and a pushed claim would name it too,
 * emitted from the push handler rather than from `openCashLink`.
 */
interface CashLinkClaims {
    val settledClaims: Flow<SettledClaim>
}

/** One-shot signals from tip card resolution that only the UI can act on. */
sealed interface TipCardEvent {
    /**
     * The resolved card is the viewer's own. Tipping yourself is a payment no-op, so rather than
     * present a card that can't be acted on, the UI sends them to the You tab — the surface that
     * owns their tip card. Reached by scanning your own code (QR link or OpenCode payload); the
     * `flipcash.com/{self}` deeplink is diverted earlier, by the router.
     */
    data object OwnCardScanned : TipCardEvent
}

interface TipCardOperations {
    /**
     * Hot and replay-less: an event emitted with no collector is dropped, which is correct here —
     * every producer runs while the scanner is on screen.
     */
    val tipCardEvents: Flow<TipCardEvent>

    /**
     * Resolves [owner]'s tip card and presents it. Both ways of naming them arrive here — a scan or
     * a `flipcash.com/{id}` link by id, a `flipcash.com/{username}` link by handle — because
     * everything after resolution is the same card.
     */
    fun resolveTipCard(owner: TipCardOwner)
}

interface DepositOperations {
    /**
     * Presents the appropriate "you can't give yet" prompt based on the user's balance:
     * an add-money prompt when the wallet is empty, or a discover-currencies prompt when
     * the user has funds (e.g. reserves) but nothing giveable.
     */
    fun presentDepositOptions(onDismiss: (() -> Unit)? = null, onRoute: ((AppRoute) -> Unit)? = null)
}

interface SessionController : BillOperations, CodeScanOperations, CashLinkOperations, DepositOperations, TipCardOperations {
    val state: StateFlow<SessionState>
    fun onAppInForeground()
    fun onAppInBackground()
}

data class SessionState(
    val vibrateOnScan: Boolean = false,
    val hasGiveableBalance: Boolean = false,
    val hasBalance: Boolean = false,
    val logScanTimes: Boolean = false,
    val showNetworkOffline: Boolean = false,
    val isCameraUp: Boolean? = null,
    val billResult: BillDeterminationResult = BillDeterminationResult.None,
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val contactDmUnreadCount: Int = 0,
    val tipsUnreadCount: Int = 0,
    val tokens: List<Token> = emptyList(),
)

val LocalSessionController = staticCompositionLocalOf<SessionController?> { null }