package com.flipcash.app.onramp

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.getcode.utils.isNetworkError
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val resources = LocalResources.current

    fun close(exit: Boolean) {
        val origin = (state as? ExternalWalletOnRampState.Transacted)?.origin
            ?: (state as? ExternalWalletOnRampState.Failed)?.origin

        if (origin is AppRoute.Token.Info || origin is AppRoute.Token.CurrencyCreator) {
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
                                shortfall = current.origin.shortfall
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
                trace(
                    tag = TAG,
                    message = "wallet connected",
                    type = TraceType.Process
                )
                if (amount != null) {
                    when (current.origin) {
                        is AppRoute.Token.Info,
                        is AppRoute.Token.CurrencyCreator -> controller.createAndValidateSwapTransaction()
                        else -> controller.createAndValidateDepositTransaction()
                    }
                } else {
                    when (current.origin) {
                        is AppRoute.Token.Info -> {
                            // Swap already navigated via pendingNavigation at Started
                        }
                        is AppRoute.Token.CurrencyCreator -> {
                            // Amount is always pre-set from CurrencyCreator; no-op for safety
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

                analytics.amountSelectedForWalletTransfer(current.provider, current.amount.localFiat.underlyingTokenAmount)
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

                if (current.origin is AppRoute.Token.CurrencyCreator) {
                    // CurrencyCreatorViewModel observes Transacted, handles reset and completion
                    return@LaunchedEffect
                }

                val swapId = current.swapId
                val token = current.token
                controller.reset()

                if (swapId != null) {
                    navigator.push(AppRoute.Token.TxProcessing(swapId))
                } else {
                    val title = token?.let {
                        resources.getString(R.string.prompt_title_tokenPurchaseOnTheWay, it.name)
                    } ?: resources.getString(R.string.prompt_title_cashOnTheWay)
                    BottomBarManager.showSuccess(
                        title = title,
                        message = resources.getString(R.string.prompt_description_cashOnTheWay),
                        showScrim = true,
                        actions = buildList {
                            if (notifications.isGranted) {
                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_ok),
                                    ) {
                                        close(true)
                                    }
                                )
                            } else {
                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_notifyMe)
                                    ) {
                                        notifications.launch()
                                    }
                                )

                                add(
                                    BottomBarAction(
                                        text = resources.getString(R.string.action_dismiss),
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
                    resources = resources,
                    provider = when (current.provider) {
                        OnRampProvider.Backpack -> resources.getString(R.string.label_backpack)
                        OnRampProvider.Phantom -> resources.getString(R.string.label_phantom)
                        OnRampProvider.Solflare -> resources.getString(R.string.label_solflare)
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
                    error = error.takeUnless { it.isAlert }
                )

                val onDismiss = {
                    close(false)
                    controller.reset()
                }

                when {
                    error.isNetworkCause -> {
                        BottomBarManager.showAlert(
                            title = resources.getString(R.string.error_title_noInternet),
                            message = resources.getString(R.string.error_description_noInternet),
                            onDismiss = { onDismiss() },
                        )
                    }
                    error.isAlert -> {
                        BottomBarManager.showAlert(
                            title = title,
                            message = message,
                            onDismiss = { onDismiss() },
                        )
                    }
                    else -> {
                        BottomBarManager.showError(
                            title = title,
                            message = message,
                            onDismiss = { onDismiss() },
                        )
                    }
                }
            }
        }
    }

    content()
}


private const val TAG = "onramp::deeplinks"

private val DeeplinkOnRampError.isAlert: Boolean
    get() = this is DeeplinkOnRampError.WalletProvidedError && error in listOf(
        DeeplinkError.UserRejectedRequest,
        DeeplinkError.Disconnected,
        DeeplinkError.TransactionRejected,
    ) || this is DeeplinkOnRampError.FailedToSendTransaction
        || (this is DeeplinkOnRampError.FailedToSimulateTransaction && cause?.isNetworkError() == true)
        || (this is DeeplinkOnRampError.FailedToCreateTransaction && cause?.isNetworkError() == true)

private val DeeplinkOnRampError.isNetworkCause: Boolean
    get() = (this is DeeplinkOnRampError.FailedToSimulateTransaction || this is DeeplinkOnRampError.FailedToCreateTransaction)
        && cause?.isNetworkError() == true

private typealias Title = String
private typealias Message = String

private fun DeeplinkOnRampError.messaging(resources: Resources, provider: String): Pair<Title, Message> = when (this) {
    is DeeplinkOnRampError.DecryptionError -> resources.getString(R.string.error_title_deeplinkOnRampDecryption) to resources.getString(R.string.error_description_deeplinkOnRampDecryption).format(provider)
    is DeeplinkOnRampError.DeserializationError -> resources.getString(R.string.error_title_deeplinkOnRampDeserialization) to resources.getString(R.string.error_description_deeplinkOnRampDeserialization).format(provider)
    is DeeplinkOnRampError.FailedToCreateTransaction -> resources.getString(R.string.error_title_deeplinkOnRampFailedToCreateTransaction) to resources.getString(R.string.error_description_deeplinkOnRampFailedToCreateTransaction)
    is DeeplinkOnRampError.FailedToSimulateTransaction -> resources.getString(R.string.error_title_deeplinkOnRampFailedToSimulateTransaction) to resources.getString(R.string.error_description_deeplinkOnRampFailedToSimulateTransaction)
    is DeeplinkOnRampError.FailedToGenerateDeeplink -> resources.getString(R.string.error_title_deeplinkOnRampFailedToCreateDeeplink) to resources.getString(R.string.error_description_deeplinkOnRampFailedToCreateDeeplink)
    is DeeplinkOnRampError.FailedToSendTransaction -> resources.getString(R.string.error_title_deeplinkOnRampFailedToSendTransaction) to resources.getString(R.string.error_description_deeplinkOnRampFailedToSendTransaction).format(provider)
    is DeeplinkOnRampError.FailedToSubmitBuyToServer -> resources.getString(R.string.error_title_deeplinkOnRampExternalFundBuy) to resources.getString(R.string.error_description_deeplinkOnRampExternalFundBuy).format(provider)
    is DeeplinkOnRampError.WalletProvidedError -> when (this.error) {
        DeeplinkError.Disconnected -> resources.getString(R.string.error_title_deeplinkOnRampDisconnected) to resources.getString(R.string.error_description_deeplinkOnRampDisconnected).format(provider)
        DeeplinkError.Unauthorized -> resources.getString(R.string.error_title_deeplinkOnRampUnauthorized) to resources.getString(R.string.error_description_deeplinkOnRampUnauthorized)
        DeeplinkError.UserRejectedRequest -> resources.getString(R.string.error_title_deeplinkOnRampUserRejected).format(provider) to resources.getString(R.string.error_description_deeplinkOnRampUserRejected).format(provider)
        DeeplinkError.InvalidInput -> resources.getString(R.string.error_title_deeplinkOnRampInvalidInput) to resources.getString(R.string.error_description_deeplinkOnRampInvalidInput)
        DeeplinkError.RequestedResourceNotAvailable -> resources.getString(R.string.error_title_deeplinkOnRampRequestedResourceNotAvailable) to resources.getString(R.string.error_description_deeplinkOnRampRequestedResourceNotAvailable).format(provider)
        DeeplinkError.TransactionRejected -> resources.getString(R.string.error_title_deeplinkOnRampTransactionRejected) to resources.getString(R.string.error_description_deeplinkOnRampTransactionRejected).format(provider)
        DeeplinkError.MethodNotFound -> resources.getString(R.string.error_title_deeplinkOnRampMethodNotFound) to resources.getString(R.string.error_description_deeplinkOnRampMethodNotFound).format(provider)
        DeeplinkError.InternalError -> resources.getString(R.string.error_title_deeplinkOnRampInternalError) to resources.getString(R.string.error_description_deeplinkOnRampInternalError).format(provider)
        DeeplinkError.Unknown -> resources.getString(R.string.error_title_deeplinkOnRampUnknown) to resources.getString(R.string.error_description_deeplinkOnRampUnknown)
    }
}
