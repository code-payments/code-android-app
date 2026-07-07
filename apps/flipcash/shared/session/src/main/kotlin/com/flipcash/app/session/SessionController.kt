package com.flipcash.app.session

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.session.BillDeterminationResult.ActedUpon
import com.getcode.opencode.model.financial.Token
import com.flipcash.app.core.AppRoute
import com.getcode.ui.core.RestrictionType
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.flow.StateFlow

sealed interface BillDeterminationResult {
    data object None : BillDeterminationResult
    sealed interface ActedUpon
}

data object Grabbed : BillDeterminationResult, ActedUpon
data object PutInWallet : BillDeterminationResult, ActedUpon

interface BillOperations {
    val billState: StateFlow<BillState>
    fun showBill(bill: Bill)
    fun dismissBill(action: BillDeterminationResult)
}

interface CodeScanOperations {
    fun onCameraScanning(scanning: Boolean)
    fun onCodeScan(code: ScannableKikCode)
}

interface CashLinkOperations {
    fun openCashLink(cashLink: String?)
}

interface DepositOperations {
    fun presentDepositOptions(onRoute: ((AppRoute) -> Unit)? = null)
}

interface SessionController : BillOperations, CodeScanOperations, CashLinkOperations, DepositOperations {
    val state: StateFlow<SessionState>
    fun onAppInForeground()
    fun onAppInBackground()
}

data class SessionState(
    val vibrateOnScan: Boolean = false,
    val hasGiveableBalance: Boolean = false,
    val logScanTimes: Boolean = false,
    val showNetworkOffline: Boolean = false,
    val autoStartCamera: Boolean? = true,
    val isCameraUp: Boolean? = null,
    val billResult: BillDeterminationResult = BillDeterminationResult.None,
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val notificationUnreadCount: Int = 0,
    val tokens: List<Token> = emptyList(),
    val isPhoneNumberSendEnabled: Boolean = false,
    val addMoneyUx: Boolean = false,
)

val LocalSessionController = staticCompositionLocalOf<SessionController?> { null }