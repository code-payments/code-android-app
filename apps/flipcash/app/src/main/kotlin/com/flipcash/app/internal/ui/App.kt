package com.flipcash.app.internal.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.bill.customization.BillPlaygroundScaffold
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.contact.verification.EmailVerificationFlow
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.internal.ui.navigation.AppPreloads
import com.flipcash.app.internal.ui.navigation.appEntryProvider
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavBlockingOverlayEntryDecorator
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.onramp.ExternalWalletOnRampHandler
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.onramp.OnRampAmountScaffold
import com.flipcash.app.onramp.rememberExternalWalletState
import com.flipcash.app.payments.PaymentScaffold
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.features.shareapp.R
import com.flipcash.services.user.AuthState
import com.getcode.libs.biometrics.BiometricsError
import com.getcode.libs.qr.rememberQrBitmapPainter
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.rememberCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.results.rememberNavResultStateRegistry
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.solana.rpc.RpcConfig
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.biometrics.rememberBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.bars.rememberBarManager
import com.getcode.ui.core.RestrictionType
import com.flipcash.app.core.extensions.navigateTo
import dev.bmcreations.tipkit.TipScaffold
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.theolm.rinku.DeepLink
import dev.theolm.rinku.compose.ext.DeepLinkListener

@Composable
internal fun App(
    tipsEngine: TipsEngine,
    solanaRpcConfig: RpcConfig,
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
        val externalWalletOnRamp = rememberExternalWalletState(solanaRpcConfig)

        CompositionLocalProvider(
            LocalExternalWalletState provides externalWalletOnRamp
        ) {
            AppPreloads()

            PaymentScaffold {
                OnRampAmountScaffold {
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

                            Box(modifier = semanticsModifier) {
                                CompositionLocalProvider(
                                    LocalCodeNavigator provides codeNavigator,
                                    LocalBiometricsState provides biometricsState,
                                ) {
                                    ExternalWalletOnRampHandler(
                                        state = externalWalletOnRamp,
                                        lifecycleOwner = LocalLifecycleOwner.current,
                                        navigator = codeNavigator,
                                    ) {
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
                                            sceneStrategy = ModalBottomSheetSceneStrategy<NavKey>(
                                                codeNavigator.resultStore
                                            ) {
                                                codeNavigator.backStack.getOrNull(
                                                    codeNavigator.backStack.lastIndex - 1
                                                )
                                            } then SinglePaneSceneStrategy(),
                                            transitionSpec = {
                                                if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                                                    EnterTransition.None togetherWith ExitTransition.None
                                                } else {
                                                    slideInHorizontally(initialOffsetX = { it }) togetherWith
                                                            slideOutHorizontally(targetOffsetX = { -it })
                                                }
                                            },
                                            popTransitionSpec = {
                                                if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                                                    EnterTransition.None togetherWith ExitTransition.None
                                                } else {
                                                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
                                                            slideOutHorizontally(targetOffsetX = { it })
                                                }
                                            },
                                            predictivePopTransitionSpec = {
                                                if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
                                                    EnterTransition.None togetherWith ExitTransition.None
                                                } else {
                                                    slideInHorizontally(initialOffsetX = { -it }) togetherWith
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
                                    }

                                    LaunchedEffect(deepLink) {
                                        val link = deepLink ?: return@LaunchedEffect

                                        if (codeNavigator.currentRouteKey is AppRoute.Loading) {
                                            // Cold start — MainRoot handles it via the deepLink lambda
                                            return@LaunchedEffect
                                        }

                                        when (val action = router.dispatch(link)) {
                                            is DeeplinkAction.Navigate -> {
                                                // If a verification code targets a screen already open,
                                                // deliver via side-channel and skip navigation.
                                                val verification = action.routes
                                                    .filterIsInstance<AppRoute.Verification>()
                                                    .firstOrNull()
                                                val email = verification?.email
                                                val code = verification?.emailVerificationCode
                                                val delivered = if (email != null && code != null) {
                                                    EmailVerificationFlow.deliverCode(email, code)
                                                } else false

                                                if (!delivered) {
                                                    codeNavigator.navigateTo(action.routes)
                                                }
                                            }
                                            is DeeplinkAction.ExternalWallet -> externalWalletOnRamp.handleWalletDeeplink(action.type)
                                            is DeeplinkAction.Login -> viewModel.handleLoginEntropy(
                                                action.entropy,
                                                onSwitchAccount = {
                                                    codeNavigator.replaceAll(
                                                        AppRoute.Onboarding.Login(
                                                            action.entropy,
                                                            fromDeeplink = true
                                                        )
                                                    )
                                                },
                                                onDismissed = { }
                                            )
                                            is DeeplinkAction.OpenCashLink -> session.openCashLink(action.entropy)
                                            DeeplinkAction.None -> {}
                                        }
                                        deepLink = null
                                    }

                                    LaunchedEffect(userState.authState) {
                                        if (userState.authState == AuthState.LoggedOut) {
                                            val current = codeNavigator.currentRouteKey
                                            if (current !is AppRoute.Loading && current !is AppRoute.Onboarding) {
                                                codeNavigator.pendingSheetDismiss = null
                                                codeNavigator.replaceAll(AppRoute.Onboarding.Login())
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
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
