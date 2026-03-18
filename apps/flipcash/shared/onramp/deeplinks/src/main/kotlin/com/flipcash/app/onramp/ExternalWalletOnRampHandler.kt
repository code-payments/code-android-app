package com.flipcash.app.onramp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.core.android.extensions.canNativelyHandle
import com.flipcash.app.core.tokens.TokenSwapPurpose
import com.flipcash.app.onramp.internal.ExternalWalletDeeplinkState
import com.flipcash.app.onramp.internal.ExternalWalletState
import com.flipcash.app.onramp.internal.buildConnectDeeplink
import com.flipcash.app.onramp.internal.buildTransactionDeeplink
import com.flipcash.app.onramp.internal.packageName
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.shared.onramp.deeplinks.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.utils.lifecycle.RepeatOnLifecycle
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.notificationPermissionCheck
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.to

@SuppressLint("LocalContextGetResourceValueCall", "InlinedApi")
@Composable
fun ExternalWalletOnRampHandler(
    state: ExternalWalletDeeplinkState,
    navigator: CodeNavigator,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    content: @Composable () -> Unit
) {
    val permissions = LocalPermissionChecker.current
    val composeScope = rememberCoroutineScope()
    val analytics = rememberAnalytics()

    fun close(exit: Boolean) {
        if (state.origin is AppRoute.Token.Info) {
            // Token flow screens handle their own navigation via inner navigator
            return
        }

        if (exit) {
            composeScope.launch {
                delay(300)
                navigator.hide()
            }
            return
        }

        state.origin?.let { route ->
            composeScope.launch {
                delay(300)
                navigator.popUntil { it::class == route::class }
            }
        } ?: run { navigator.popAll() }
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current


    val onNotificationResult: (Boolean) -> Unit = {
        composeScope.launch { close(true) }
    }

    val notificationPermissionCheck =
        notificationPermissionCheck { onNotificationResult(it) }

    RepeatOnLifecycle(
        targetState = Lifecycle.State.RESUMED,
        lifecycleOwner = lifecycleOwner,
    ) {
        state.errors
            .onEach { error ->
                val (title, message) = error.messaging(
                    context = context,
                    provider = when (state.provider) {
                        OnRampProvider.Backpack -> context.getString(R.string.label_backpack)
                        OnRampProvider.Phantom -> context.getString(R.string.label_phantom)
                        OnRampProvider.Solflare -> context.getString(R.string.label_solflare)
                        null -> ""
                    }
                )

                if (error is DeeplinkOnRampError.WalletProvidedError && error.code == DeeplinkError.UserRejectedRequest.code) {
                    analytics.walletTransactionCancelled(state.provider!!)
                } else if (error is DeeplinkOnRampError.FailedToSendTransaction) {
                    analytics.walletTransactionFailed(state.provider!!)
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
                    state.reset()
                }
            }.launchIn(this)
    }

    LaunchedEffect(state.deeplinkState, state.amount) {
        when (state.deeplinkState) {
            ExternalWalletState.IDLE -> Unit
            ExternalWalletState.STARTING -> Unit
            ExternalWalletState.STARTED -> {
                val uri = buildConnectDeeplink(state)
                trace(
                    tag = TAG,
                    message = "wallet connect uri: $uri",
                    type = TraceType.Process
                )
                if (uri?.canNativelyHandle(context) == true) {
                    val origin = state.origin
                    if (origin is AppRoute.Token.Info) {
                        state.pendingNavigation = AppRoute.Token.SwapTransact(
                            TokenSwapPurpose.FundWithWallet(origin.mint),
                            forNeededFunds = origin.forNeededFunds
                        )
                    }

                    analytics.connectWallet(state.provider!!)
                    uriHandler.openUri(uri.toString())
                    state.deeplinkState = ExternalWalletState.CONNECTING
                } else {
                    val provider = state.provider ?: return@LaunchedEffect
                    context.startActivity(IntentUtils.appStoreListing(provider.packageName))
                    state.reset()
                }
            }

            ExternalWalletState.CONNECTING -> {
                state.walletConnection?.let {
                    state.deeplinkState = ExternalWalletState.CONNECTED
                }
            }

            ExternalWalletState.CONNECTED -> {
                // if amount was provided, send the transaction
                if (state.amount != null) {
                    when (state.origin) {
                        is AppRoute.Token.Info -> state.createAndValidateSwapTransaction()
                        else -> state.createAndValidateDepositTransaction()
                    }
                } else {
                    trace(
                        tag = TAG,
                        message = "wallet connected",
                        type = TraceType.Process
                    )
                    when (state.origin) {
                        is AppRoute.Token.Info -> {
                            // SwapTransact already navigated via pendingNavigation at STARTED
                        }
                        else -> {
                            navigator.push(AppRoute.OnRamp.AmountEntry)
                        }
                    }
                }
            }

            ExternalWalletState.SIGNING -> {
                val uri = buildTransactionDeeplink(state)
                if (uri == null) {
                    state.errors.tryEmit(DeeplinkOnRampError.FailedToGenerateDeeplink())
                    return@LaunchedEffect
                }

                trace(
                    tag = TAG,
                    message = "wallet transact uri: $uri",
                    type = TraceType.Process
                )

                val swapId = state.swapId
                if (state.origin is AppRoute.Token.Info && swapId != null) {
                    state.pendingNavigation = AppRoute.Token.TxProcessing(
                        swapId, awaitExternalWallet = true
                    )
                }

                analytics.amountSelectedForWalletTransfer(state.provider!!, state.amount!!.underlyingTokenAmount)
                uriHandler.openUri(uri.toString())
            }

            ExternalWalletState.SIGNED -> {
                trace(
                    tag = TAG,
                    message = "wallet transaction signed!",
                    type = TraceType.Process
                )
                state.sendTransaction()
            }

            ExternalWalletState.TRANSACTING -> {
                trace(
                    tag = TAG,
                    message = "transaction in progress",
                    type = TraceType.Process
                )
            }

            ExternalWalletState.TRANSACTED -> {
                trace(
                    tag = TAG,
                    message = "transaction complete",
                    type = TraceType.Process
                )
                analytics.transactionSubmittedToWallet(state.provider!!)

                if (state.origin is AppRoute.Token.Info) {
                    // TxProcessingScreen observes TRANSACTED, calls reset() and dispatches OnSwapIdChanged
                    return@LaunchedEffect
                }

                val swapId = state.swapId
                state.reset()

                if (swapId != null) {
                    // confirmation is shown in finalization screen
                    navigator.push(AppRoute.Token.TxProcessing(swapId))
                } else {
                    val hasPushPerms = permissions.isGranted(Manifest.permission.POST_NOTIFICATIONS)
                    val title = state.tokenToPurchase?.let { token ->
                        context.getString(R.string.prompt_title_tokenPurchaseOnTheWay, token.name)
                    } ?: context.getString(R.string.prompt_title_cashOnTheWay)
                    BottomBarManager.showMessage(
                        title = title,
                        subtitle = context.getString(R.string.prompt_description_cashOnTheWay),
                        showScrim = true,
                        actions = buildList {
                            if (hasPushPerms) {
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
                                        notificationPermissionCheck(true)
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
                        type = BottomBarManager.BottomBarMessageType.SUCCESS,
                    )
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
