package com.flipcash.app.internal.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.bill.customization.BillPlaygroundScaffold
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.verification.email.LocalEmailCodeChannel
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.featureflags.model.BackgroundResetTimeout
import com.flipcash.app.internal.ui.navigation.appEntryProvider
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavBlockingOverlayEntryDecorator
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.onramp.CoinbaseOnRampHandler
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.features.shareapp.R
import com.flipcash.services.user.AuthState
import com.getcode.animation.LocalSharedTransitionScope
import com.getcode.libs.biometrics.BiometricsError
import com.getcode.libs.qr.rememberQrBitmapPainter
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.Sheet
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.rememberCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.results.rememberNavResultStateRegistry
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.navigation.scrim.LocalScrimController
import com.getcode.navigation.scrim.ScrimController
import com.getcode.navigation.scrim.ScrimOverlay
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.biometrics.rememberBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.bars.rememberBarManager
import com.getcode.ui.core.RestrictionType
import dev.bmcreations.tipkit.TipScaffold
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.theolm.rinku.DeepLink
import dev.theolm.rinku.compose.ext.DeepLinkListener
import kotlinx.coroutines.flow.first

@Composable
internal fun App(
    tipsEngine: TipsEngine,
) {
    val router = LocalRouter.current!!
    val analytics = rememberAnalytics()
    val viewModel = getActivityScopedViewModel<HomeViewModel>()
    val requireBiometrics by viewModel.requireBiometrics.collectAsStateWithLifecycle()
    val biometricsState = rememberBiometricsState(
        requireBiometrics = requireBiometrics,
        onError = { error ->
            if (error == BiometricsError.NoBiometrics) {
                viewModel.onMissingBiometrics()
            }
        }
    )

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.onResume()
        }
    }

    var deepLink by remember { mutableStateOf<DeepLink?>(null) }
    var deeplinkHandled by remember { mutableStateOf(false) }
    val userManager = LocalUserManager.current!!
    DeepLinkListener {
        analytics.deeplinkOpened(it.data)
        deepLink = it
    }

    val session = LocalSessionController.current!!
    val userState by userManager.state.collectAsState()

    FlipcashTheme {
        rememberQrBitmapPainter(
            content = stringResource(
                R.string.app_download_link,
                stringResource(id = R.string.app_download_link_qr_ref)
            ),
            size = CodeTheme.dimens.screenWidth * 0.60f,
            padding = 0.25.dp
        )

        val barManager = rememberBarManager()

        BillPlaygroundScaffold {
            TipScaffold(tipsEngine = tipsEngine) {
                    val backStack = remember { NavBackStack<NavKey>(AppRoute.Loading) }
                    val resultStateRegistry = rememberNavResultStateRegistry()
                    val codeNavigator = rememberCodeNavigator(
                        backStack = backStack,
                        resultStateRegistry = resultStateRegistry,
                        onRootReached = { /* handled by activity back press */ },
                    )

                    val semanticsModifier = if (BuildConfig.DEBUG) {
                        Modifier.semantics { testTagsAsResourceId = true }
                    } else Modifier

                    val scrimController = remember { ScrimController() }

                    Box(modifier = semanticsModifier) {
                        SharedTransitionLayout {
                            CompositionLocalProvider(
                                LocalCodeNavigator provides codeNavigator,
                                LocalBiometricsState provides biometricsState,
                                LocalScrimController provides scrimController,
                                LocalSharedTransitionScope provides this,
                            ) {
                                    CoinbaseOnRampHandler {
                                        AppNavHost(
                                            navigator = codeNavigator,
                                            resultStateRegistry = resultStateRegistry,
                                            decorators = listOf(
                                                rememberNavMessagingEntryDecorator(
                                                    codeNavigator.backStack,
                                                    barManager
                                                ),
                                                rememberNavBlockingOverlayEntryDecorator(),
                                            ),
                                            sceneStrategies = listOf(
                                                ModalBottomSheetSceneStrategy(
                                                    codeNavigator.resultStore
                                                ) {
                                                    codeNavigator.backStack.getOrNull(
                                                        codeNavigator.backStack.lastIndex - 1
                                                    )
                                                },
                                                SinglePaneSceneStrategy(),
                                            ),
                                            transitionSpec = {
                                                val shouldCrossfade =
                                                    initialState.key == AppRoute.Loading.toString() ||
                                                            targetState.key == AppRoute.Loading.toString() ||
                                                            targetState.key.toString()
                                                                .startsWith("Login")
                                                when {
                                                    shouldCrossfade -> fadeIn(tween(300)) togetherWith fadeOut(
                                                        tween(300)
                                                    )

                                                    targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                                                        EnterTransition.None togetherWith ExitTransition.None

                                                    else -> slideInHorizontally(initialOffsetX = { it }) togetherWith
                                                            slideOutHorizontally(targetOffsetX = { -it })
                                                }
                                            },
                                            popTransitionSpec = {
                                                val shouldCrossfade =
                                                    initialState.key == AppRoute.Loading.toString() ||
                                                            targetState.key == AppRoute.Loading.toString() ||
                                                            targetState.key.toString()
                                                                .startsWith("Login")
                                                when {
                                                    shouldCrossfade -> fadeIn(tween(300)) togetherWith fadeOut(
                                                        tween(300)
                                                    )

                                                    targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                                                        EnterTransition.None togetherWith ExitTransition.None

                                                    else -> slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                                            slideOutHorizontally(targetOffsetX = { it })
                                                }
                                            },
                                            predictivePopTransitionSpec = {
                                                val shouldCrossfade =
                                                    initialState.key == AppRoute.Loading.toString() ||
                                                            targetState.key == AppRoute.Loading.toString() ||
                                                            targetState.key.toString()
                                                                .startsWith("Login")
                                                when {
                                                    shouldCrossfade -> fadeIn(tween(300)) togetherWith fadeOut(
                                                        tween(300)
                                                    )

                                                    targetState is OverlayScene<*> || initialState is OverlayScene<*> ->
                                                        EnterTransition.None togetherWith ExitTransition.None

                                                    else -> slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                                            slideOutHorizontally(targetOffsetX = { it })
                                                }
                                            },
                                            onBack = { codeNavigator.navigateBack() },
                                            entryProvider = appEntryProvider(
                                                resultStateRegistry = resultStateRegistry,
                                                barManager = barManager,
                                                deepLink = { deepLink },
                                            ),
                                        )

                                        ScrimOverlay(scrimController)
                                    }

                                val emailCodeChannel = LocalEmailCodeChannel.current
                                LaunchedEffect(deepLink) {
                                    val link = deepLink ?: return@LaunchedEffect

                                    if (codeNavigator.currentRouteKey is AppRoute.Loading) {
                                        // Cold start — MainRoot handles it via the deepLink lambda
                                        return@LaunchedEffect
                                    }

                                    val action = router.dispatch(link)
                                    deeplinkHandled = action != DeeplinkAction.None
                                    when (action) {
                                        is DeeplinkAction.Navigate -> {
                                            // If a verification code targets a screen already open,
                                            // deliver via side-channel and skip navigation.
                                            val verification = action.routes
                                                .filterIsInstance<AppRoute.Verification>()
                                                .firstOrNull()
                                            val email = verification?.email
                                            val code = verification?.emailVerificationCode
                                            val delivered = if (email != null && code != null) {
                                                emailCodeChannel.deliverCode(email, code)
                                            } else false

                                            if (!delivered) {
                                                codeNavigator.navigateAll(action.routes)
                                            }
                                        }

                                        is DeeplinkAction.Login -> viewModel.handleLoginEntropy(
                                            action.entropy,
                                            onSwitchAccount = {
                                                codeNavigator.replaceAll(
                                                    AppRoute.OnboardingFlow(
                                                        seed = action.entropy,
                                                        fromDeeplink = true
                                                    )
                                                )
                                            },
                                            onDismissed = { }
                                        )

                                        is DeeplinkAction.OpenCashLink -> session.openCashLink(
                                            action.entropy
                                        )

                                        DeeplinkAction.None -> {}
                                    }
                                    deepLink = null
                                }

                                LaunchedEffect(userState.authState) {
                                    if (userState.authState == AuthState.LoggedOut) {
                                        val current = codeNavigator.currentRouteKey
                                        if (current !is AppRoute.Loading && current !is AppRoute.OnboardingFlow) {
                                            codeNavigator.pendingSheetDismiss = null
                                            val switchEntropy =
                                                viewModel.consumePendingSwitchEntropy()
                                            codeNavigator.replaceAll(
                                                AppRoute.OnboardingFlow(
                                                    seed = switchEntropy
                                                )
                                            )
                                        }
                                    }
                                }

                                LaunchedEffect(userState.isTimelockUnlocked) {
                                    if (userState.isTimelockUnlocked) {
                                        codeNavigator.replaceAll(
                                            AppRoute.Main.AppRestricted(
                                                RestrictionType.TIMELOCK_UNLOCKED
                                            )
                                        )
                                    }
                                }

                                OnLifecycleEvent { _, event ->
                                    when (event) {
                                        Lifecycle.Event.ON_RESUME -> {
                                            session.onAppInForeground()
                                        }

                                        Lifecycle.Event.ON_STOP,
                                        Lifecycle.Event.ON_DESTROY -> {
                                            session.onAppInBackground()
                                        }

                                        else -> Unit
                                    }
                                }

                                BackgroundResetEffect(
                                    navigator = codeNavigator,
                                    deepLink = { deepLink },
                                    deeplinkHandled = { deeplinkHandled },
                                    onReset = { deeplinkHandled = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun BackgroundResetEffect(
    navigator: CodeNavigator,
    deepLink: () -> DeepLink?,
    deeplinkHandled: () -> Boolean,
    onReset: () -> Unit,
) {
    val featureFlags = LocalFeatureFlags.current
    val option by featureFlags.getOption(FeatureFlag.BackgroundReset)
        .collectAsStateWithLifecycle()

    var pendingReset by remember { mutableStateOf(false) }
    var backgroundedAt by remember { mutableLongStateOf(0L) }

    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                backgroundedAt = System.currentTimeMillis()
            }
            Lifecycle.Event.ON_RESUME -> {
                val timeout = runCatching { BackgroundResetTimeout.valueOf(option) }
                    .getOrNull()
                    ?.duration

                if (timeout != null && backgroundedAt > 0L) {
                    val elapsed = System.currentTimeMillis() - backgroundedAt
                    if (elapsed >= timeout.inWholeMilliseconds) {
                        pendingReset = true
                    }
                }
                backgroundedAt = 0L
            }
            else -> Unit
        }
    }

    LaunchedEffect(pendingReset) {
        if (!pendingReset) return@LaunchedEffect
        // Wait for any pending deeplink to be consumed before deciding
        snapshotFlow { deepLink() }.first { it == null }
        if (!deeplinkHandled()) {
            navigator.popUntil { it !is Sheet }
        }
        onReset()
        pendingReset = false
    }
}

