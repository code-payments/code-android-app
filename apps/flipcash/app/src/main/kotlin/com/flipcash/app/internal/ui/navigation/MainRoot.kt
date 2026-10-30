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
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.homeRoute
import com.flipcash.app.core.navigation.launchRoute
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.resolveBackStack
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.router.Router
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.user.AuthState
import com.flipcash.shared.chat.share.LocalPendingChatShares
import com.flipcash.shared.chat.share.PendingChatShares
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.utils.trace
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun MainRoot(
    deepLink: () -> DeepLink?,
    onPendingAction: (DeeplinkAction) -> Unit = {},
) {
    val navigator = LocalCodeNavigator.current
    val userManager = LocalUserManager.current!!
    var showLoading by remember { mutableStateOf(false) }
    val router = LocalRouter.current!!
    var showLogo by remember { mutableStateOf(true) }
    val pendingShares = LocalPendingChatShares.current
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
                // A launch by a share opens on the picker, so the share has to be known before the
                // first real route is chosen; waiting here keeps the Loading screen up meanwhile.
                val shareRoute = if (state == AuthState.Ready) {
                    pendingShares?.let { claimShareRoute(it) }
                } else null
                val launch = buildNavGraphForLaunch(
                    state = state,
                    router = router,
                    deepLink = deepLink,
                    shareRoute = shareRoute,
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
     * Predict the final backstack that [baseRoutes] + `navigateAll(deeplinkRoutes)` will produce.
     * Uses the shared [resolveBackStack] so it applies the same tab-switch resolution as
     * `navigateAll`, letting us compare against the current backstack and skip redundant
     * navigation.
     */
    fun resolvedBackStack(): List<NavKey> =
        resolveBackStack(baseRoutes, deeplinkRoutes)
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

/**
 * The screen a share from another app opens on, once it has finished arriving, or null when this
 * launch carries no share. Claims the share, so the root's own share handling doesn't push the
 * same screen a second time.
 */
private suspend fun claimShareRoute(shares: PendingChatShares): AppRoute? {
    // Bounded: a copy that never finishes must not leave the app on its splash screen.
    withTimeoutOrNull(ShareArrivalTimeout) { shares.incoming.first { !it } }
    shares.beginPresenting() ?: return null
    return AppRoute.Messaging.SharePicker
}

private val ShareArrivalTimeout = 3.seconds

/**
 * @param shareRoute the screen a share from another app opens on, which goes straight on top of the
 * start screen so it is the first one shown. Wins over a deeplink: the launch is the share.
 */
internal fun buildNavGraphForLaunch(
    state: AuthState,
    router: Router,
    deepLink: () -> DeepLink?,
    shareRoute: AppRoute? = null,
): LaunchNavGraph? {
    return when (state) {
        is AuthState.Onboarding -> when (state.resumePoint) {
            // Access key already seen; resume into display-name entry. On success it replaces
            // the stack with the permissions phase (see UpdateUserProfileFlowScreen target).
            AuthState.ResumePoint.DisplayName -> LaunchNavGraph(
                listOf(
                    AppRoute.UpdateUserProfile(
                        origin = AppRoute.OnboardingFlow(),
                        steps = listOf(UpdateProfileStep.Name(DisplayNameSource.Onboarding)),
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
            val home = homeRoute
            val link = deepLink()
            if (shareRoute != null) {
                LaunchNavGraph(listOf(launchRoute, shareRoute))
            } else if (link != null) {
                when (val action = router.dispatch(link)) {
                    is DeeplinkAction.Navigate -> LaunchNavGraph(
                        baseRoutes = listOf(home),
                        deeplinkRoutes = action.routes,
                    )

                    // A token link opens as the wallet's expanded card — a pending action applied on
                    // top of the wallet home, which is already the launch base.
                    is DeeplinkAction.OpenToken,
                    is DeeplinkAction.OpenCashLink,
                    is DeeplinkAction.PresentTipCard,
                    is DeeplinkAction.OpenExternally,
                    is DeeplinkAction.Login -> LaunchNavGraph(
                        baseRoutes = listOf(home),
                        pendingAction = action,
                    )

                    // A link that resolves to nothing is a plain launch.
                    else -> LaunchNavGraph(listOf(launchRoute))
                }
            } else {
                LaunchNavGraph(listOf(launchRoute))
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
