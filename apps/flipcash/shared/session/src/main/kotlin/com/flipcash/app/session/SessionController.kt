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
}

interface CodeScanOperations {
    fun onCameraScanning(scanning: Boolean)
    fun onCodeScan(code: ScannableKikCode)
}

interface CashLinkOperations {
    fun openCashLink(cashLink: String?)
}

/** One-shot signals from tip card resolution that only the UI can act on. */
sealed interface TipCardEvent {
    /**
     * The resolved card is the viewer's own. Tipping yourself is a payment no-op, so rather than
     * present a card that can't be acted on, the UI sends them to the You tab — the surface that
     * owns their tip card. Reached by scanning your own code (QR link or OpenCode payload); the
     * `/tip/{self}` deeplink is diverted earlier, by the router.
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
     * a `/tip/{id}` link by id, a `flipcash.com/{username}` link by handle — because everything
     * after resolution is the same card.
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