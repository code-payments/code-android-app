package com.flipcash.app.onramp

import android.Manifest
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.onramp.internal.PhantomDeeplinkState
import com.flipcash.app.onramp.internal.PhantomDepositState
import com.flipcash.app.onramp.internal.buildConnectDeeplink
import com.flipcash.app.onramp.internal.buildTransactionDeeplink
import com.flipcash.app.router.Router
import com.flipcash.services.analytics.Action
import com.flipcash.shared.onramp.phantom.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.RepeatOnLifecycle
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.notificationPermissionCheck
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.to

@Composable
fun PhantomOnRampHandler(
    state: PhantomDepositState,
    router: Router,
    deepLink: DeepLink?,
    content: @Composable () -> Unit
) {
    val navigator = LocalCodeNavigator.current
    val permissions = LocalPermissionChecker.current

    suspend fun close(exit: Boolean) {
        if (exit) {
            delay(300)
            navigator.hide()
            return
        }

        state.origin?.let { screenProvider ->
            val screen = ScreenRegistry.get(screenProvider)
            delay(300)
            val popped = navigator.popUntil { it::class == screen::class }
            if (!popped) navigator.popAll()
        } ?: run { navigator.popAll() }
    }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val composeScope = rememberCoroutineScope()
    val onNotificationResult: (Boolean) -> Unit = { isGranted ->
        composeScope.launch { close(true) }
    }

    val notificationPermissionCheck =
        notificationPermissionCheck { onNotificationResult(it) }

    RepeatOnLifecycle(
        targetState = Lifecycle.State.STARTED
    ) {
        state.errors
            .onEach { error ->
                val (title, message) = error.messaging(context)
                trace(
                    tag = TAG,
                    message = "Something went wrong during phantom onramp",
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
                    launch { close(false) }
                    state.reset()
                }
            }.launchIn(this)
    }

    LaunchedEffect(deepLink) {
        val type = router.processType(deepLink)
        if (type is DeeplinkType.PhantomConnection) {
            val result = type.result
            val error = type.error
            if (result != null) {
                state.decrypt(connectionResult = result)
            } else {
                val resolvedError = PhantomError.fromCode(error?.errorCode)
                val message = error?.errorMessage ?: "Something went wrong"
                state.errors.emit(PhantomOnRampError.PhantomProvidedError(resolvedError, message = message))
            }
        } else if (type is DeeplinkType.PhantomSignedTransaction) {
            val result = type.result
            val error = type.error
            if (result != null) {
                state.decrypt(signingResult = result)
            } else {
                val resolvedError = PhantomError.fromCode(error?.errorCode)
                val message = error?.errorMessage ?: "Something went wrong"
                state.errors.emit(PhantomOnRampError.PhantomProvidedError(resolvedError, message = message))
            }
        }
    }

    LaunchedEffect(state.deeplinkState, state.amount) {
        when (state.deeplinkState) {
            PhantomDeeplinkState.IDLE -> Unit
            PhantomDeeplinkState.STARTING -> Unit
            PhantomDeeplinkState.STARTED -> {
                val uri = buildConnectDeeplink(state)
                trace(
                    tag = TAG,
                    message = "Phantom connect uri: $uri",
                    type = TraceType.Process
                )
                uriHandler.openUri(uri.toString())
                state.deeplinkState = PhantomDeeplinkState.CONNECTING
            }

            PhantomDeeplinkState.CONNECTING -> {
                state.walletConnection?.let {
                    state.deeplinkState = PhantomDeeplinkState.CONNECTED
                }
            }

            PhantomDeeplinkState.CONNECTED -> {
                trace(
                    tag = TAG,
                    message = "phantom connected",
                    type = TraceType.Process
                )
                // if amount was provided, send the transaction
                if (state.amount != null) {
                    state.createAndSendTransaction()
                } else {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Amount))
                }
            }

            PhantomDeeplinkState.SIGNING -> {
                val uri = buildTransactionDeeplink(state)
                if (uri == null) {
                    state.errors.tryEmit(PhantomOnRampError.FailedToGenerateDeeplink())
                    return@LaunchedEffect
                }

                trace(
                    tag = TAG,
                    message = "Phantom transact uri: $uri",
                    type = TraceType.Process
                )
                uriHandler.openUri(uri.toString())
            }

            PhantomDeeplinkState.SIGNED -> {
                trace(
                    tag = TAG,
                    message = "phantom transaction signed!",
                    type = TraceType.Process
                )
                state.sendTransaction()
            }

            PhantomDeeplinkState.TRANSACTING -> {
                trace(
                    tag = TAG,
                    message = "transaction in progress",
                    type = TraceType.Process
                )
            }

            PhantomDeeplinkState.TRANSACTED -> {
                trace(
                    tag = TAG,
                    message = "transaction complete",
                    type = TraceType.Process
                )
                state.reset()
                val hasPushPerms = permissions.isGranted(Manifest.permission.POST_NOTIFICATIONS)
                BottomBarManager.showMessage(
                    title = context.getString(R.string.prompt_title_cashOnTheWay),
                    subtitle = context.getString(R.string.prompt_description_cashOnTheWay),
                    actions = buildList {
                        if (hasPushPerms) {
                            add(
                                BottomBarAction(
                                    text = context.getString(R.string.action_ok),
                                ) {
                                    launch { close(true) }
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
                                    launch { close(true) }
                                }
                            )
                        }
                    },
                    type = BottomBarManager.BottomBarMessageType.SUCCESS,
                )
            }
        }
    }

    content()
}


private const val TAG = "onramp::phantom"

private typealias Title = String
private typealias Message = String

private fun PhantomOnRampError.messaging(context: Context): Pair<Title, Message> = when (this) {
    is PhantomOnRampError.DecryptionError -> context.getString(R.string.error_title_phantomDecryption) to context.getString(R.string.error_description_phantomDecryption)
    is PhantomOnRampError.DeserializationError -> context.getString(R.string.error_title_phantomDeserialization) to context.getString(R.string.error_description_phantomDeserialization)
    is PhantomOnRampError.FailedToCreateTransaction -> context.getString(R.string.error_title_phantomFailedToCreateTransaction) to context.getString(R.string.error_description_phantomFailedToCreateTransaction)
    is PhantomOnRampError.FailedToGenerateDeeplink -> context.getString(R.string.error_title_phantomFailedToCreateDeeplink) to context.getString(R.string.error_description_phantomFailedToCreateDeeplink)
    is PhantomOnRampError.FailedToSendTransaction -> context.getString(R.string.error_title_phantomFailedToSendTransaction) to context.getString(R.string.error_description_phantomFailedToSendTransaction)
    is PhantomOnRampError.PhantomProvidedError -> when (this.error) {
        PhantomError.Disconnected -> context.getString(R.string.error_title_phantomDisconnected) to context.getString(R.string.error_description_phantomDisconnected)
        PhantomError.Unauthorized -> context.getString(R.string.error_title_phantomUnauthorized) to context.getString(R.string.error_description_phantomUnauthorized)
        PhantomError.UserRejectedRequest -> context.getString(R.string.error_title_phantomUserRejected) to context.getString(R.string.error_description_phantomUserRejected)
        PhantomError.InvalidInput -> context.getString(R.string.error_title_phantomInvalidInput) to context.getString(R.string.error_description_phantomInvalidInput)
        PhantomError.RequestedResourceNotAvailable -> context.getString(R.string.error_title_phantomRequestedResourceNotAvailable) to context.getString(R.string.error_description_phantomRequestedResourceNotAvailable)
        PhantomError.TransactionRejected -> context.getString(R.string.error_title_phantomTransactionRejected) to context.getString(R.string.error_description_phantomTransactionRejected)
        PhantomError.MethodNotFound -> context.getString(R.string.error_title_phantomMethodNotFound) to context.getString(R.string.error_description_phantomMethodNotFound)
        PhantomError.InternalError -> context.getString(R.string.error_title_phantomInternalError) to context.getString(R.string.error_description_phantomInternalError)
        PhantomError.Unknown -> context.getString(R.string.error_title_phantomUnknown) to context.getString(R.string.error_description_phantomUnknown)
    }
}