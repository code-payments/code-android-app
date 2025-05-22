package com.flipcash.app.internal.ui.navigation

import android.os.Parcelable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.android.app.R
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.router.Router
import com.flipcash.services.user.AuthState
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.utils.trace
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.seconds

@Parcelize
internal class MainRoot(private val deepLink: () -> DeepLink?) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val userManager = LocalUserManager.currentOrThrow
        var showLoading by remember { mutableStateOf(false) }
        val router = LocalRouter.currentOrThrow
        var showLogo by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.65f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showLogo) {
                        Image(
                            painter = painterResource(R.drawable.ic_flipcash_logo_w_name),
                            contentDescription = null,
                        )
                    }
                }

                Spacer(modifier = Modifier.requiredHeight(CodeTheme.dimens.inset))
                val loadingAlpha by animateFloatAsState(
                    if (showLoading) 1f else 0f,
                    label = "loading visibility"
                )
                CodeCircularProgressIndicator(
                    modifier = Modifier.alpha(loadingAlpha)
                )
            }
        }


        LaunchedEffect(userManager) {
            userManager.state
                .map { it.authState }
                .distinctUntilChanged()
                .onEach { state ->

                    trace(
                        tag = "AuthStateRouter",
                        message = "Handling auth state change during app launch",
                        metadata = {
                            "state" to state
                        }
                    )
                    val screens = buildNavGraphForLaunch(state, router)

                    when (state) {
                        AuthState.LoggedInAwaitingUser -> {
                            delay(1.5.seconds)
                            showLoading = true
                            showLogo = true
                        }

                        AuthState.LoggedIn -> {
                            showLogo = false
                        }

                        else -> {
                            showLogo = true
                        }
                    }

                    if (screens != null) {
                        navigator.replaceAll(screens)
                    }
                }.launchIn(this)
        }
    }

    private suspend fun buildNavGraphForLaunch(
        state: AuthState,
        router: Router,
    ): List<Screen>? {
        return when (state) {
            is AuthState.Registered -> {
                if (state.seenAccessKey) {
                    listOf(
                        ScreenRegistry.get(NavScreenProvider.Login.Home()),
                        ScreenRegistry.get(NavScreenProvider.CreateAccount.AccessKey),
                        ScreenRegistry.get(NavScreenProvider.CreateAccount.Purchase)
                    )
                } else {
                    listOf(
                        ScreenRegistry.get(NavScreenProvider.Login.Home()),
                        ScreenRegistry.get(NavScreenProvider.CreateAccount.AccessKey)
                    )
                }
            }

            AuthState.LoggedIn -> {
                val screens = router.processDestination(deepLink())

                screens.ifEmpty {
                    listOf(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
                }
            }

            AuthState.LoggedOut,
            AuthState.Unknown -> {
                val screens = router.processDestination(deepLink())

                screens.ifEmpty {
                    listOf(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                }
            }

            AuthState.LoggedInAwaitingUser -> null
        }
    }
}

