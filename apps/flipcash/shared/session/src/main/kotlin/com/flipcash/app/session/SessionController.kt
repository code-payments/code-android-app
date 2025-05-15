package com.flipcash.app.session

import androidx.compose.runtime.staticCompositionLocalOf
import com.flipcash.app.core.bill.Bill
import com.flipcash.app.core.bill.BillState
import com.getcode.opencode.model.financial.Fiat
import com.getcode.ui.core.RestrictionType
import com.getcode.util.permissions.PermissionResult
import com.kik.kikx.models.ScannableKikCode
import kotlinx.coroutines.flow.StateFlow

sealed interface PresentationStyle {
    data object Hidden : PresentationStyle
    sealed interface Visible

    data object Pop : PresentationStyle, Visible
    data object Slide : PresentationStyle, Visible
}

interface SessionController {
    val state: StateFlow<SessionState>
    val billState: StateFlow<BillState>
    fun onAppInForeground()
    fun onAppInBackground()
    fun onCameraScanning(scanning: Boolean)
    fun onCameraPermissionResult(result: PermissionResult)
    fun showBill(bill: Bill, vibrate: Boolean = false)
    fun cancelSend(style: PresentationStyle = PresentationStyle.Slide, overrideToast: Boolean = false)
    fun onCodeScan(code: ScannableKikCode)
    fun openCashLink(cashLink: String?)
}

data class SessionState(
    val isCameraPermissionGranted: Boolean? = null,
    val vibrateOnScan: Boolean = false,
    val balance: Fiat? = null,
    val logScanTimes: Boolean = false,
    val showNetworkOffline: Boolean = false,
    val autoStartCamera: Boolean? = true,
    val isCameraScanEnabled: Boolean = true,
    val presentationStyle: PresentationStyle = PresentationStyle.Hidden,
    val restrictionType: RestrictionType? = null,
    val isRemoteSendLoading: Boolean = false,
    val notificationUnreadCount: Int = 0,
)

val LocalSessionController = staticCompositionLocalOf<SessionController?> { null }