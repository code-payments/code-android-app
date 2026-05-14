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
        is CoinbaseOnRampWebError.Unknown,
        is CoinbaseOnRampWebError.MissingTransactionUuid -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampUnknownFailure),
                message = resources.getString(R.string.error_description_onrampUnknownFailure),
            )
        }

        is CoinbaseOnRampWebError.GuestCardNotDebit -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampInvalidCard),
                message = resources.getString(R.string.error_description_onrampInvalidCard),
            )
        }

        is CoinbaseOnRampWebError.GuestRegionMismatch -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampRegionMismatch),
                message = resources.getString(R.string.error_description_onrampRegionMismatch),
            )
        }

        is CoinbaseOnRampWebError.GuestGooglePayError -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampTransactionFailed),
                message = resources.getString(R.string.error_description_onrampTransactionFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestGooglePayNotReady -> {
            BottomBarManager.showAlert(
                title = resources.getString(R.string.error_title_onrampGooglePayNotReady),
                message = resources.getString(R.string.error_description_onrampGooglePayNotReady),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionBuyFailed -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampTransactionBuyFailed),
                message = resources.getString(R.string.error_description_onrampTransactionBuyFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionSendFailed -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampTransactionSendFailed),
                message = resources.getString(R.string.error_description_onrampTransactionSendFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionAvsValidationFailed -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampTransactionAvsValidationFailed),
                message = resources.getString(R.string.error_description_onrampTransactionAvsValidationFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionTransactionFailed -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampTransactionFailed),
                message = resources.getString(R.string.error_description_onrampTransactionFailed),
            )
        }

        is CoinbaseOnRampWebError.Internal,
        is CoinbaseOnRampWebError.GooglePayButtonNotFound,
        is CoinbaseOnRampWebError.WebViewTimeout -> {
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_onrampInternal),
                message = resources.getString(R.string.error_description_onrampInternal),
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
