package com.flipcash.app.ui.navigation

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
import com.flipcash.app.R
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.ui.LocalRouter
import com.flipcash.services.user.AuthState
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

internal class MainRoot(private val deepLink: () -> DeepLink?) : Screen {

    override val key: ScreenKey = uniqueScreenKey

    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val userManager = LocalUserManager.currentOrThrow
        var showLoading by remember { mutableStateOf(false) }
        val router = LocalRouter.currentOrThrow

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.secondary),
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
                    Image(
                        painter = painterResource(R.drawable.flipchat_logo),
                        contentDescription = null,
                    )
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
                    Timber.d("sessionState=$state")
                    when (state) {
                        AuthState.LoggedInAwaitingUser -> {
                            delay(500)
                            showLoading = true
                        }
                        AuthState.Unregistered,
                        AuthState.LoggedIn -> {
                            val screens = router.processDestination(deepLink())

                            if (screens.isNotEmpty()) {
                                navigator.replaceAll(screens)
                            } else {
                                navigator.replace(ScreenRegistry.get(NavScreenProvider.AppHomeScreen()))
                            }
                        }
                        AuthState.LoggedOut -> {
                            navigator.replace(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                        }
                        AuthState.Unknown -> {
                            navigator.replace(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                        }
                    }
                }.launchIn(this)
        }
    }
}

