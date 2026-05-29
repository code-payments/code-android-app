package com.flipcash.app.onramp

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.flipcash.shared.onramp.coinbase.R
import com.getcode.manager.BottomBarManager
import kotlinx.coroutines.delay

@Composable
fun CoinbaseOnRampHandler(
    controller: CoinbaseOnRampController = LocalCoinbaseOnRampController.current,
    content: @Composable () -> Unit,
) {
    val state by controller.state.collectAsState()
    val resources = LocalResources.current
    when (val current = state) {
        is CoinbaseOnRampState.Paying -> {
            CoinbaseOnRampWebview(
                orderId = current.order.orderId,
                paymentLinkUrl = current.order.paymentLink,
                onPaymentSuccess = { orderId ->
                    controller.onPaymentSuccess(orderId)
                },
                onPaymentFailure = { error ->
                    controller.onPaymentFailure(error)
                },
                onCancel = {
                    controller.onPaymentCancel()
                },
            )
        }

        is CoinbaseOnRampState.Completed -> {
            LaunchedEffect(current) {
                controller.emitPendingNavigation(
                    AppRoute.Token.TxProcessing(current.swapId, SwapPurpose.Buy(current.token.address), current.amount)
                )
                controller.reset()
            }
        }

        is CoinbaseOnRampState.Failed -> {
            LaunchedEffect(current) {
                delay(400) // let the system payment sheet finish its dismiss animation
                showOnRampFailure(resources, current.error)
                controller.reset()
            }
        }

        CoinbaseOnRampState.Idle -> Unit
    }

    content()
}

private fun showOnRampFailure(resources: Resources, error: CoinbaseOnRampWebError) {
    when (error) {
        // --- Grouped errors ---

        is CoinbaseOnRampWebError.UnknownFailure -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampUnknownFailure),
                message = resources.getString(R.string.error_description_onrampUnknownFailure),
            )
        }

        is CoinbaseOnRampWebError.CardDeclined -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampCardSoftDeclined),
                message = resources.getString(R.string.error_description_onrampCardSoftDeclined),
            )
        }

        is CoinbaseOnRampWebError.BillingAddressInvalid -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampTransactionAvsValidationFailed),
                message = resources.getString(R.string.error_description_onrampTransactionAvsValidationFailed),
            )
        }

        is CoinbaseOnRampWebError.InternalFailure -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampInternal),
                message = resources.getString(R.string.error_description_onrampInternal),
            )
        }

        is CoinbaseOnRampWebError.TransactionFailed -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampTransactionFailed),
                message = resources.getString(R.string.error_description_onrampTransactionFailed),
            )
        }

        // --- Single-variant errors ---

        is CoinbaseOnRampWebError.GuestCardNotDebit -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampInvalidCard),
                message = resources.getString(R.string.error_description_onrampInvalidCard),
            )
        }

        is CoinbaseOnRampWebError.GuestCardRiskDeclined -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampCardRiskDeclined),
                message = resources.getString(R.string.error_description_onrampCardRiskDeclined),
            )
        }

        is CoinbaseOnRampWebError.GuestPermissionDenied -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampCardPermissionDenied),
                message = resources.getString(R.string.error_description_onrampCardPermissionDenied),
            )
        }

        is CoinbaseOnRampWebError.RegionNotSupported -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampRegionMismatch),
                message = resources.getString(R.string.error_description_onrampRegionMismatch),
            )
        }

        is CoinbaseOnRampWebError.GuestWeeklyTransactionLimitReached -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampTransactionLimit),
                message = resources.getString(R.string.error_description_onrampTransactionLimit),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionMaxLimitReached -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampTransactionCount),
                message = resources.getString(R.string.error_description_onrampTransactionCount),
            )
        }

        is CoinbaseOnRampWebError.GuestGooglePayNotReady -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampGooglePayNotReady),
                message = resources.getString(R.string.error_description_onrampGooglePayNotReady),
            )
        }

        is CoinbaseOnRampWebError.GuestGooglePayNotSupported -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampGooglePayNotSupported),
                message = resources.getString(R.string.error_description_onrampGooglePayNotSupported),
            )
        }

        is CoinbaseOnRampWebError.GuestCardInsufficientBalance -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampCardInsufficientBalance),
                message = resources.getString(R.string.error_description_onrampCardInsufficientBalance),
            )
        }

        is CoinbaseOnRampWebError.GuestCardPrepaidDeclined -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampCardPrepaidDeclined),
                message = resources.getString(R.string.error_description_onrampCardPrepaidDeclined),
            )
        }

        is CoinbaseOnRampWebError.InvalidBillingName -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampInvalidBillingName),
                message = resources.getString(R.string.error_description_onrampInvalidBillingName),
            )
        }

        is CoinbaseOnRampWebError.PaymentSheetTimeout -> {
            BottomBarManager.showInfo(
                title = resources.getString(R.string.error_title_onrampPaymentSheetTimeout),
                message = resources.getString(R.string.error_description_onrampPaymentSheetTimeout),
            )
        }
    }
}
