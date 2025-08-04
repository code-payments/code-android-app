package com.flipcash.app.onramp.internal.phantom

import android.net.Uri.Builder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.stack.StackEvent
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.phantom.PhantomDeeplinkOrigin
import com.flipcash.app.onramp.internal.data.PhantomDeeplinkState
import com.flipcash.app.onramp.internal.data.PhantomDepositState
import com.flipcash.app.router.Router
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.CombinedNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.utils.base64
import com.getcode.utils.base58
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay

@Composable
fun PhantomOnRampHandler(
    state: PhantomDepositState,
    navigator: CodeNavigator?,
    router: Router,
    deepLink: DeepLink?,
    content: @Composable () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(deepLink) {
        val type = router.processType(deepLink)
        if (type is DeeplinkType.PhantomConnection) {
            state.decrypt(connectionResult = type.result)
        }
    }

    LaunchedEffect(state.deeplinkState) {
        println("deeplink state: ${state.deeplinkState}")
        when (state.deeplinkState) {
            PhantomDeeplinkState.IDLE -> Unit
            PhantomDeeplinkState.STARTING -> Unit
            PhantomDeeplinkState.STARTED -> {
                val originEncoded = when (val origin = state.origin) {
                    is PhantomDeeplinkOrigin.PoolWithId -> "pool-id_${origin.id.base58}"
                    is PhantomDeeplinkOrigin.PoolWithRendezvous -> "pool-seed_${origin.keyPair.seed.base64}"
                    PhantomDeeplinkOrigin.Menu -> "menu"
                    null -> "unknown"
                }
                val redirectUrl = "https://app.flipcash.com/phantom/connected?origin=$originEncoded"
                val uri = Builder()
                    .scheme("https")
                    .authority("phantom.app")
                    .path("ul/v1/connect")
                    .appendQueryParameter("app_url", "https://flipcash.com")
                    .appendQueryParameter("dapp_encryption_public_key", state.curvePublicKey)
                    .appendQueryParameter("cluster", "mainnet-beta")
                    .appendQueryParameter("redirect_link", redirectUrl)
                    .build()

                println("Phantom connect uri: $uri")
                uriHandler.openUri(uri.toString())
                state.deeplinkState = PhantomDeeplinkState.CONNECTING
            }

            PhantomDeeplinkState.CONNECTING -> {
                state.encryptedData?.let {
                    state.deeplinkState = PhantomDeeplinkState.CONNECTED
                }
            }

            PhantomDeeplinkState.CONNECTED -> {
                println("connected to phantom!")
                // this will always be present in a modal so we can confidently push it into the stack
                // without worrying about the need to show vs. push
                delay(300)
                navigator?.push(ScreenRegistry.get(NavScreenProvider.HomeScreen.OnRamp.Amount))
            }

            PhantomDeeplinkState.TRANSACTING -> Unit
            PhantomDeeplinkState.TRANSACTED -> Unit
            PhantomDeeplinkState.CONNECTION_ERROR -> {
                println("connection error")
            }

            PhantomDeeplinkState.TRANSACTION_ERROR -> {
                println("transaction error")
            }
        }
    }

    content()
}

