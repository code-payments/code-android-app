package com.flipcash.app.internal.ui.navigation

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
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.android.R
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.router.Router
import com.flipcash.services.internal.model.account.UserFlags
import com.flipcash.services.user.AuthState
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.utils.trace
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun MainRoot(deepLink: () -> DeepLink?) {
    val navigator = LocalCodeNavigator.current
    val userManager = LocalUserManager.current!!
    var showLoading by remember { mutableStateOf(false) }
    val router = LocalRouter.current!!
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
            .map { it.authState to it.flags }
            .distinctUntilChanged()
            .onEach { (state, flags) ->
                trace(
                    tag = "AuthStateRouter",
                    message = "Handling auth state change during app launch => $state",
                    metadata = {
                        "state" to state
                    }
                )
                val routes = buildNavGraphForLaunch(
                    state = state,
                    userFlags = flags,
                    router = router,
                    deepLink = deepLink
                )

                when (state) {
                    AuthState.LoggedInAwaitingUser -> {
                        delay(1.5.seconds)
                        showLoading = true
                        showLogo = true
                    }

                    AuthState.LoggedInWithUser -> {
                        showLogo = false
                    }

                    else -> {
                        showLogo = true
                    }
                }

                if (routes != null) {
                    navigator.replaceAll(routes)
                }
            }.launchIn(this)
    }
}

private suspend fun buildNavGraphForLaunch(
    state: AuthState,
    userFlags: UserFlags?,
    router: Router,
    deepLink: () -> DeepLink?,
): List<NavKey>? {
    return when (state) {
        is AuthState.Registered -> {
            if (state.seenAccessKey) {
                buildList {
                    if (userFlags?.requiresIapForRegistration == true) {
                        addAll(
                            listOf(
                                AppRoute.Onboarding.Login(),
                                AppRoute.Onboarding.AccessKey,
                                AppRoute.Onboarding.Purchase()
                            )
                        )
                    } else {
                        addAll(listOf(AppRoute.Main.Scanner()))
                    }
                }
            } else {
                listOf(
                    AppRoute.Onboarding.Login(),
                    AppRoute.Onboarding.AccessKey
                )
            }
        }

        AuthState.LoggedInWithUser -> {
            val routes = router.processDestination(deepLink())

            routes.ifEmpty {
                listOf(AppRoute.Main.Scanner())
            }
        }

        AuthState.LoggedOut,
        AuthState.Unknown -> {
            val routes = router.processDestination(deepLink())
            routes.ifEmpty {
                listOf(AppRoute.Onboarding.Login())
            }
        }

        AuthState.LoggedInAwaitingUser -> null
    }
}
