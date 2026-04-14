package com.flipcash.app.onramp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.flipcash.app.core.AppRoute
import com.flipcash.app.onramp.internal.CoinbaseOnRampWebError
import com.flipcash.shared.onramp.coinbase.R
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import kotlinx.coroutines.delay

@Composable
fun CoinbaseOnRampHandler(
    manager: CoinbaseOnRampManager,
    controller: OnRampController,
    navigator: CodeNavigator,
    content: @Composable () -> Unit,
) {
    val state by manager.state.collectAsState()
    val context = LocalContext.current

    when (val current = state) {
        is CoinbaseOnRampState.Paying -> {
            CoinbaseOnRampWebview(
                orderId = current.order.orderId,
                paymentLinkUrl = current.order.paymentLink,
                onPaymentSuccess = { orderId ->
                    manager.onPaymentSuccess(orderId)
                },
                onPaymentFailure = { error ->
                    manager.onPaymentFailure(error)
                },
                onCancel = {
                    manager.onPaymentCancel()
                },
            )
        }

        is CoinbaseOnRampState.Processing -> {
            LaunchedEffect(current) {
                delay(400) // let the system payment sheet finish its dismiss animation
                controller.processPayment()
                    .onFailure {
                        BottomBarManager.showError(
                            title = "Something Went Wrong",
                            message = "Failed to complete purchase. Please try again",
                        )
                    }
            }
        }

        is CoinbaseOnRampState.Completed -> {
            LaunchedEffect(current) {
                navigator.push(AppRoute.Token.TxProcessing(current.swapId))
                manager.reset()
            }
        }

        is CoinbaseOnRampState.Failed -> {
            LaunchedEffect(current) {
                delay(400) // let the system payment sheet finish its dismiss animation
                showOnRampFailure(context, current.error)
                manager.reset()
            }
        }

        CoinbaseOnRampState.Idle -> Unit
    }

    content()
}

private fun showOnRampFailure(context: Context, error: CoinbaseOnRampWebError) {
    when (error) {
        is CoinbaseOnRampWebError.Unknown,
        is CoinbaseOnRampWebError.MissingTransactionUuid -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampUnknownFailure),
                message = context.getString(R.string.error_description_onrampUnknownFailure),
            )
        }

        is CoinbaseOnRampWebError.GuestCardNotDebit -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampInvalidCard),
                message = context.getString(R.string.error_description_onrampInvalidCard),
            )
        }

        is CoinbaseOnRampWebError.GuestGooglePayError -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampTransactionFailed),
                message = context.getString(R.string.error_description_onrampTransactionFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestGooglePayNotReady -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampGooglePayNotReady),
                message = context.getString(R.string.error_description_onrampGooglePayNotReady),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionBuyFailed -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampTransactionBuyFailed),
                message = context.getString(R.string.error_description_onrampTransactionBuyFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionSendFailed -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampTransactionSendFailed),
                message = context.getString(R.string.error_description_onrampTransactionSendFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionAvsValidationFailed -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampTransactionAvsValidationFailed),
                message = context.getString(R.string.error_description_onrampTransactionAvsValidationFailed),
            )
        }

        is CoinbaseOnRampWebError.GuestTransactionTransactionFailed -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampTransactionFailed),
                message = context.getString(R.string.error_description_onrampTransactionFailed),
            )
        }

        is CoinbaseOnRampWebError.Internal,
        is CoinbaseOnRampWebError.GooglePayButtonNotFound -> {
            BottomBarManager.showError(
                title = context.getString(R.string.error_title_onrampInternal),
                message = context.getString(R.string.error_description_onrampInternal),
            )
        }
    }
}
