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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.android.R
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.resolveRoutes
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.router.Router
import com.flipcash.services.user.AuthState
import com.getcode.navigation.core.CodeNavigator
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
internal fun MainRoot(
    isNewUi: Boolean,
    deepLink: () -> DeepLink?,
    onPendingAction: (DeeplinkAction) -> Unit = {},
) {
    val navigator = LocalCodeNavigator.current
    val userManager = LocalUserManager.current!!
    var showLoading by remember { mutableStateOf(false) }
    val router = LocalRouter.current!!
    var showLogo by remember { mutableStateOf(true) }
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
                modifier = Modifier.graphicsLayer { alpha = loadingAlpha }
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
                    message = "Handling auth state change during app launch => $state",
                    metadata = {
                        "state" to state
                    }
                )
                val launch = buildNavGraphForLaunch(
                    state = state,
                    isNewUi = isNewUi,
                    router = router,
                    deepLink = deepLink
                )

                when (state) {
                    AuthState.Authenticating -> {
                        delay(0.5.seconds)
                        showLoading = true
                    }

                    AuthState.Ready -> {
                        showLogo = false
                    }

                    else -> Unit
                }

                if (launch != null) {
                    val current = navigator.backStack.toList()
                    val target = launch.resolvedBackStack()

                    // Skip if the current stack already matches or extends the target.
                    // This prevents blowing away screens the user has navigated into
                    // (e.g. a verification code screen) when auth/flags re-emit.
                    if (!current.startsWith(target)) {
                        navigator.replaceAll(launch.baseRoutes)
                        if (launch.deeplinkRoutes.isNotEmpty()) {
                            navigator.navigateAll(launch.deeplinkRoutes)
                        }
                    }

                    // Fire eagerly so the claim/login starts in parallel
                    // with the nav transition instead of waiting for
                    // App.kt's LaunchedEffect to see a non-Loading route.
                    if (launch.pendingAction != null) {
                        onPendingAction(launch.pendingAction)
                    }
                }
            }.launchIn(this)
    }
}

/**
 * Result of building the initial nav graph.
 *
 * [baseRoutes] are set via replaceAll (root backstack).
 * [deeplinkRoutes] are applied via navigateTo, which handles sheet wrapping
 * so deeplinks targeting screens inside sheets render correctly.
 */
internal data class LaunchNavGraph(
    val baseRoutes: List<NavKey>,
    val deeplinkRoutes: List<AppRoute> = emptyList(),
    val pendingAction: DeeplinkAction? = null,
) {
    /**
     * Predict the final backstack that [baseRoutes] + [navigateTo(deeplinkRoutes)] will produce.
     * Uses the shared [resolveRoutes] to apply the same sheet-wrapping as [navigateTo]
     * so we can compare against the current backstack and skip redundant navigation.
     */
    fun resolvedBackStack(): List<NavKey> {
        if (deeplinkRoutes.isEmpty()) return baseRoutes
        return baseRoutes + resolveRoutes(deeplinkRoutes)
    }
}

/**
 * Returns true if [this] list starts with [prefix] (element-wise structural equality).
 * An exact match also returns true (prefix == full list).
 *
 * This lets us detect when the user has navigated *deeper* than the launch target
 * (e.g. opened a sheet, pushed a verification screen) so we don't blow away their
 * backstack on a harmless auth/flags re-emission.
 */
private fun List<NavKey>.startsWith(prefix: List<NavKey>): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { i -> this[i]::class == prefix[i]::class }
}

internal fun buildNavGraphForLaunch(
    state: AuthState,
    router: Router,
    isNewUi: Boolean,
    deepLink: () -> DeepLink?,
): LaunchNavGraph? {
    return when (state) {
        is AuthState.Onboarding -> when (state.resumePoint) {
            // Access key already seen; resume into display-name entry. On success it replaces
            // the stack with the permissions phase (see UpdateUserProfileFlowScreen target).
            AuthState.ResumePoint.DisplayName -> LaunchNavGraph(
                listOf(
                    AppRoute.UpdateUserProfile(
                        origin = AppRoute.OnboardingFlow(),
                        includeName = true,
                        includePhoto = false,
                        target = AppRoute.OnboardingFlow(
                            phase = AppRoute.OnboardingFlow.Phase.Permissions,
                            skipContacts = true,
                        ),
                        allowBack = false,
                    )
                )
            )

            AuthState.ResumePoint.AccessKey ->
                LaunchNavGraph(listOf(AppRoute.OnboardingFlow(resumeAt = AppRoute.OnboardingFlow.ResumePoint.AccessKey)))
            AuthState.ResumePoint.AccessKeyThenPurchase ->
                LaunchNavGraph(listOf(AppRoute.OnboardingFlow(resumeAt = AppRoute.OnboardingFlow.ResumePoint.AccessKeyThenPurchase)))
            AuthState.ResumePoint.PostAccessKey ->
                LaunchNavGraph(listOf(AppRoute.OnboardingFlow(resumeAt = AppRoute.OnboardingFlow.ResumePoint.PostAccessKey)))
        }

        AuthState.Ready -> {
            // New UI opens on the Wallet tab; v1 opens on the Scanner.
            val home = if (isNewUi) AppRoute.Sheets.Wallet else AppRoute.Main.Scanner
            val link = deepLink()
            if (link != null) {
                when (val action = router.dispatch(link)) {
                    is DeeplinkAction.Navigate -> LaunchNavGraph(
                        baseRoutes = listOf(home),
                        deeplinkRoutes = action.routes,
                    )

                    is DeeplinkAction.OpenCashLink,
                    is DeeplinkAction.PresentTipCard,
                    is DeeplinkAction.Login -> LaunchNavGraph(
                        baseRoutes = listOf(home),
                        pendingAction = action,
                    )

                    else -> LaunchNavGraph(listOf(home))
                }
            } else {
                LaunchNavGraph(listOf(home))
            }
        }

        AuthState.LoggedOut -> {
            val link = deepLink()
            if (link != null) {
                when (val action = router.dispatch(link)) {
                    is DeeplinkAction.Navigate -> LaunchNavGraph(action.routes)
                    else -> LaunchNavGraph(listOf(AppRoute.OnboardingFlow()))
                }
            } else {
                LaunchNavGraph(listOf(AppRoute.OnboardingFlow()))
            }
        }

        // Transient pre-resolution states — wait on the Loading screen. Navigating to login here
        // (ClearAll) would tear down MainRoot's auth observer before Ready arrives, stranding an
        // authenticated user on login. A genuine no-account resolves to LoggedOut (handled above).
        AuthState.Unknown,
        AuthState.Authenticating -> null
    }
}
