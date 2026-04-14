package com.flipcash.app.onramp

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.core.android.extensions.canNativelyHandle
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.onramp.internal.buildConnectDeeplink
import com.flipcash.app.onramp.internal.buildTransactionDeeplink
import com.flipcash.app.onramp.internal.curvePublicKey
import com.flipcash.app.onramp.internal.packageName
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.shared.onramp.deeplinks.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.util.permissions.rememberNotificationPermission
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("LocalContextGetResourceValueCall", "InlinedApi")
@Composable
fun ExternalWalletOnRampHandler(
    controller: ExternalWalletOnRampController,
    navigator: CodeNavigator,
    content: @Composable () -> Unit,
) {
    val composeScope = rememberCoroutineScope()
    val analytics = rememberAnalytics()
    val state by controller.state.collectAsStateWithLifecycle()
    val amount by controller.amount.collectAsStateWithLifecycle()

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    fun close(exit: Boolean) {
        val origin = (state as? ExternalWalletOnRampState.Transacted)?.origin
            ?: (state as? ExternalWalletOnRampState.Failed)?.origin

        if (origin is AppRoute.Token.Info) {
            return
        }

        if (exit) {
            composeScope.launch {
                delay(300)
                navigator.hide()
            }
            return
        }

        origin?.let { route ->
            composeScope.launch {
                delay(300)
                navigator.popUntil { it::class == route::class }
            }
        } ?: run { navigator.popAll() }
    }

    val notifications = rememberNotificationPermission {
        composeScope.launch { close(true) }
    }

    LaunchedEffect(state, amount) {
        when (val current = state) {
            is ExternalWalletOnRampState.Idle -> Unit

            is ExternalWalletOnRampState.Started -> {
                val uri = buildConnectDeeplink(
                    provider = current.provider,
                    curvePublicKey = controller.keyPair.curvePublicKey,
                    origin = current.origin,
                )
                trace(
                    tag = TAG,
                    message = "wallet connect uri: $uri",
                    type = TraceType.Process
                )
                if (uri?.canNativelyHandle(context) == true) {
                    if (current.origin is AppRoute.Token.Info) {
                        controller.emitPendingNavigation(
                            AppRoute.Token.Swap(
                                SwapPurpose.FundWithWallet(current.origin.mint),
                                isFundingShortfall = current.origin.isFundingShortfall
                            )
                        )
                    }

                    analytics.connectWallet(current.provider)
                    uriHandler.openUri(uri.toString())
                    controller.transitionTo(
                        ExternalWalletOnRampState.Connecting(
                            origin = current.origin,
                            provider = current.provider,
                        )
                    )
                } else {
                    context.startActivity(IntentUtils.appStoreListing(current.provider.packageName))
                    controller.reset()
                }
            }

            is ExternalWalletOnRampState.Connecting -> Unit

            is ExternalWalletOnRampState.Connected -> {
                if (amount != null) {
                    when (current.origin) {
                        is AppRoute.Token.Info -> controller.createAndValidateSwapTransaction()
                        else -> controller.createAndValidateDepositTransaction()
                    }
                } else {
                    trace(
                        tag = TAG,
                        message = "wallet connected",
                        type = TraceType.Process
                    )
                    when (current.origin) {
                        is AppRoute.Token.Info -> {
                            // Swap already navigated via pendingNavigation at Started
                        }
                        else -> {
                            navigator.push(AppRoute.Token.OnRamp(controller.tokenToPurchase.value!!.address))
                        }
                    }
                }
            }

            is ExternalWalletOnRampState.Signing -> {
                val uri = buildTransactionDeeplink(
                    provider = current.provider,
                    curvePublicKey = controller.keyPair.curvePublicKey,
                    encryptionPublicKey = current.encryptionPublicKey,
                    unsignedTransaction = current.unsignedTransaction,
                    session = current.connection.session,
                    secretKey = controller.keyPair.secretKey.map { it.toByte() },
                    origin = current.origin,
                )
                if (uri == null) {
                    controller.transitionTo(
                        ExternalWalletOnRampState.Failed(
                            error = DeeplinkOnRampError.FailedToGenerateDeeplink(),
                            origin = current.origin,
                            provider = current.provider,
                        )
                    )
                    return@LaunchedEffect
                }

                trace(
                    tag = TAG,
                    message = "wallet transact uri: $uri",
                    type = TraceType.Process
                )

                val swapId = current.swapId
                if (current.origin is AppRoute.Token.Info && swapId != null) {
                    controller.emitPendingNavigation(
                        AppRoute.Token.TxProcessing(
                            swapId, awaitExternalWallet = true
                        )
                    )
                }

                analytics.amountSelectedForWalletTransfer(current.provider, current.amount.underlyingTokenAmount)
                uriHandler.openUri(uri.toString())
            }

            is ExternalWalletOnRampState.Signed -> {
                trace(
                    tag = TAG,
                    message = "wallet transaction signed!",
                    type = TraceType.Process
                )
                controller.sendTransaction()
            }

            is ExternalWalletOnRampState.Transacting -> {
                trace(
                    tag = TAG,
                    message = "transaction in progress",
                    type = TraceType.Process
                )
            }

            is ExternalWalletOnRampState.Transacted -> {
                trace(
                    tag = TAG,
                    message = "transaction complete",
                    type = TraceType.Process
                )
                analytics.transactionSubmittedToWallet(current.provider)

                if (current.origin is AppRoute.Token.Info) {
                    // TxProcessingScreen observes Transacted, calls reset() and dispatches OnSwapIdChanged
                    return@LaunchedEffect
                }

                val swapId = current.swapId
                val token = current.token
                controller.reset()

                if (swapId != null) {
                    navigator.push(AppRoute.Token.TxProcessing(swapId))
                } else {
                    val title = token?.let {
                        context.getString(R.string.prompt_title_tokenPurchaseOnTheWay, it.name)
                    } ?: context.getString(R.string.prompt_title_cashOnTheWay)
                    BottomBarManager.showSuccess(
                        title = title,
                        message = context.getString(R.string.prompt_description_cashOnTheWay),
                        showScrim = true,
                        actions = buildList {
                            if (notifications.isGranted) {
                                add(
                                    BottomBarAction(
                                        text = context.getString(R.string.action_ok),
                                    ) {
                                        close(true)
                                    }
                                )
                            } else {
                                add(
                                    BottomBarAction(
                                        text = context.getString(R.string.action_notifyMe)
                                    ) {
                                        notifications.launch()
                                    }
                                )

                                add(
                                    BottomBarAction(
                                        text = context.getString(R.string.action_dismiss),
                                        style = BottomBarManager.BottomBarButtonStyle.Text
                                    ) {
                                        close(true)
                                    }
                                )
                            }
                        },
                    )
                }
            }

            is ExternalWalletOnRampState.Failed -> {
                val error = current.error
                val (title, message) = error.messaging(
                    context = context,
                    provider = when (current.provider) {
                        OnRampProvider.Backpack -> context.getString(R.string.label_backpack)
                        OnRampProvider.Phantom -> context.getString(R.string.label_phantom)
                        OnRampProvider.Solflare -> context.getString(R.string.label_solflare)
                        null -> ""
                    }
                )

                if (error is DeeplinkOnRampError.WalletProvidedError && error.code == DeeplinkError.UserRejectedRequest.code) {
                    analytics.walletTransactionCancelled(current.provider!!)
                } else if (error is DeeplinkOnRampError.FailedToSendTransaction) {
                    analytics.walletTransactionFailed(current.provider!!)
                }

                trace(
                    tag = TAG,
                    message = "Something went wrong during deeplink onramp",
                    type = TraceType.Error,
                    metadata = {
                        "errorMessage" to error.message
                        "userMessage" to message
                        "code" to error.code
                    },
                    error = error
                )

                BottomBarManager.showError(
                    title = title,
                    message = message,
                ) {
                    close(false)
                    controller.reset()
                }
            }
        }
    }

    content()
}


private const val TAG = "onramp::deeplinks"

private typealias Title = String
private typealias Message = String

private fun DeeplinkOnRampError.messaging(context: Context, provider: String): Pair<Title, Message> = when (this) {
    is DeeplinkOnRampError.DecryptionError -> context.getString(R.string.error_title_deeplinkOnRampDecryption) to context.getString(R.string.error_description_deeplinkOnRampDecryption).format(provider)
    is DeeplinkOnRampError.DeserializationError -> context.getString(R.string.error_title_deeplinkOnRampDeserialization) to context.getString(R.string.error_description_deeplinkOnRampDeserialization).format(provider)
    is DeeplinkOnRampError.FailedToCreateTransaction -> context.getString(R.string.error_title_deeplinkOnRampFailedToCreateTransaction) to context.getString(R.string.error_description_deeplinkOnRampFailedToCreateTransaction)
    is DeeplinkOnRampError.FailedToSimulateTransaction -> context.getString(R.string.error_title_deeplinkOnRampFailedToSimulateTransaction) to context.getString(R.string.error_description_deeplinkOnRampFailedToSimulateTransaction)
    is DeeplinkOnRampError.FailedToGenerateDeeplink -> context.getString(R.string.error_title_deeplinkOnRampFailedToCreateDeeplink) to context.getString(R.string.error_description_deeplinkOnRampFailedToCreateDeeplink)
    is DeeplinkOnRampError.FailedToSendTransaction -> context.getString(R.string.error_title_deeplinkOnRampFailedToSendTransaction) to context.getString(R.string.error_description_deeplinkOnRampFailedToSendTransaction).format(provider)
    is DeeplinkOnRampError.FailedToSubmitBuyToServer -> context.getString(R.string.error_title_deeplinkOnRampExternalFundBuy) to context.getString(R.string.error_description_deeplinkOnRampExternalFundBuy).format(provider)
    is DeeplinkOnRampError.WalletProvidedError -> when (this.error) {
        DeeplinkError.Disconnected -> context.getString(R.string.error_title_deeplinkOnRampDisconnected) to context.getString(R.string.error_description_deeplinkOnRampDisconnected).format(provider)
        DeeplinkError.Unauthorized -> context.getString(R.string.error_title_deeplinkOnRampUnauthorized) to context.getString(R.string.error_description_deeplinkOnRampUnauthorized)
        DeeplinkError.UserRejectedRequest -> context.getString(R.string.error_title_deeplinkOnRampUserRejected).format(provider) to context.getString(R.string.error_description_deeplinkOnRampUserRejected).format(provider)
        DeeplinkError.InvalidInput -> context.getString(R.string.error_title_deeplinkOnRampInvalidInput) to context.getString(R.string.error_description_deeplinkOnRampInvalidInput)
        DeeplinkError.RequestedResourceNotAvailable -> context.getString(R.string.error_title_deeplinkOnRampRequestedResourceNotAvailable) to context.getString(R.string.error_description_deeplinkOnRampRequestedResourceNotAvailable).format(provider)
        DeeplinkError.TransactionRejected -> context.getString(R.string.error_title_deeplinkOnRampTransactionRejected) to context.getString(R.string.error_description_deeplinkOnRampTransactionRejected).format(provider)
        DeeplinkError.MethodNotFound -> context.getString(R.string.error_title_deeplinkOnRampMethodNotFound) to context.getString(R.string.error_description_deeplinkOnRampMethodNotFound).format(provider)
        DeeplinkError.InternalError -> context.getString(R.string.error_title_deeplinkOnRampInternalError) to context.getString(R.string.error_description_deeplinkOnRampInternalError).format(provider)
        DeeplinkError.Unknown -> context.getString(R.string.error_title_deeplinkOnRampUnknown) to context.getString(R.string.error_description_deeplinkOnRampUnknown)
    }
}
