package com.flipcash.app.onramp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.utils.RepeatOnLifecycle
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
    val close = suspend {
        state.origin?.let { screenProvider ->
            val screen = ScreenRegistry.get(screenProvider)
            delay(300)
            val popped = navigator.popUntil { it::class == screen::class }
            if (!popped) navigator.popAll()
        } ?: run { navigator.popAll() }
    }

    val uriHandler = LocalUriHandler.current
    RepeatOnLifecycle(
        targetState = Lifecycle.State.STARTED
    ) {
        state.errors
            .onEach { error ->
//                val (title, message) = error.messaging()
                trace(
                    tag = TAG,
                    message = "Something went wrong during phantom onramp",
                    type = TraceType.Error,
                    metadata = {
                        "errorMessage" to error.message
                        "userMessage" to ""
                        "code" to error.code
                    }
                )

                BottomBarManager.showError(
                    "Something went wrong",
                    "Please try again"
                ) {
                    launch { close() }
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

    LaunchedEffect(state.deeplinkState) {
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
                // this will always be present in a modal so we can confidently push it into the stack
                // without worrying about the need to show vs. push
                delay(300)
                navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Amount))
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
                navigator.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Success))
            }
        }
    }

    content()
}


private const val TAG = "onramp::phantom"

private fun PhantomOnRampError.messaging(): Pair<String, String> = when (this) {
    is PhantomOnRampError.DecryptionError -> TODO()
    is PhantomOnRampError.DeserializationError -> TODO()
    is PhantomOnRampError.FailedToCreateTransaction -> TODO()
    is PhantomOnRampError.FailedToGenerateDeeplink -> TODO()
    is PhantomOnRampError.FailedToSendTransaction -> TODO()
    is PhantomOnRampError.PhantomProvidedError -> when (this.error) {
        PhantomError.Disconnected -> TODO()
        PhantomError.Unauthorized -> TODO()
        PhantomError.UserRejectedRequest -> TODO()
        PhantomError.InvalidInput -> TODO()
        PhantomError.RequestedResourceNotAvailable -> TODO()
        PhantomError.TransactionRejected -> TODO()
        PhantomError.MethodNotFound -> TODO()
        PhantomError.InternalError -> TODO()
        PhantomError.Unknown -> "Something went wrong" to "Please try again"
    }
}