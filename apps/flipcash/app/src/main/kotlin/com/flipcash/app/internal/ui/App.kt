package com.flipcash.app.internal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.bill.customization.BillPlaygroundScaffold
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.internal.ui.navigation.AppPreloads
import com.flipcash.app.internal.ui.navigation.appEntryProvider
import com.flipcash.app.internal.ui.navigation.decorators.rememberNavMessagingEntryDecorator
import com.flipcash.app.onramp.ExternalWalletOnRampHandler
import com.flipcash.app.onramp.LocalExternalWalletState
import com.flipcash.app.onramp.OnRampAmountScaffold
import com.flipcash.app.onramp.rememberExternalWalletState
import com.flipcash.app.payments.PaymentScaffold
import com.flipcash.app.router.LocalRouter
import com.flipcash.app.session.LocalSessionController
import com.flipcash.app.theme.FlipcashTheme
import com.flipcash.app.updates.UpdateRequiredBlockingView
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
import com.getcode.ui.biometrics.views.BiometricsBlockingView
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.bars.rememberBarManager
import com.getcode.ui.core.RestrictionType
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
    var loginRequest by remember { mutableStateOf<String?>(null) }
    val userManager = LocalUserManager.current!!
    DeepLinkListener {
        analytics.deeplinkOpened(it.data)
        val type = router.processType(it)
        analytics.deeplinkParsed(type, it.data)
        if (type is DeeplinkType.Login) {
            loginRequest = type.entropy
        }
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
                                        router = router,
                                        deepLink = deepLink,
                                    ) {
                                        AppNavHost(
                                            navigator = codeNavigator,
                                            resultStateRegistry = resultStateRegistry,
                                            decorators = listOf(
                                                rememberNavMessagingEntryDecorator(
                                                    codeNavigator.backStack,
                                                    barManager
                                                )
                                            ),
                                            sceneStrategy = ModalBottomSheetSceneStrategy<NavKey>(
                                                codeNavigator.resultStore
                                            ) {
                                                codeNavigator.backStack.getOrNull(
                                                    codeNavigator.backStack.lastIndex - 1
                                                )
                                            } then SinglePaneSceneStrategy(),
                                            onBack = { codeNavigator.navigateBack() },
                                            entryProvider = { key ->
                                                appEntryProvider(
                                                    key = key,
                                                    resultStateRegistry = resultStateRegistry,
                                                    barManager = barManager,
                                                    deepLink = { deepLink },
                                                )
                                            },
                                        )
                                    }

                                    LaunchedEffect(deepLink) {
                                        if (codeNavigator.currentRouteKey is AppRoute.Loading) return@LaunchedEffect
                                        if (deepLink != null) {
                                            val routes = router.processDestination(deepLink)
                                            if (routes.isNotEmpty()) {
                                                codeNavigator.replaceAll(routes)
                                            }
                                            deepLink = null
                                        }
                                    }

                                    LaunchedEffect(
                                        loginRequest,
                                        codeNavigator.lastItem,
                                        userManager.authState
                                    ) {
                                        if (codeNavigator.currentRouteKey is AppRoute.Loading) return@LaunchedEffect
                                        if (userManager.authState !is AuthState.LoggedInWithUser) {
                                            loginRequest = null
                                            return@LaunchedEffect
                                        }
                                        loginRequest?.let { entropy ->
                                            viewModel.handleLoginEntropy(
                                                entropy,
                                                onSwitchAccount = {
                                                    loginRequest = null
                                                    codeNavigator.replaceAll(
                                                        AppRoute.Onboarding.Login(
                                                            entropy,
                                                            fromDeeplink = true
                                                        )
                                                    )
                                                },
                                                onDismissed = { loginRequest = null }
                                            )
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

            BiometricsBlockingView(modifier = Modifier.fillMaxSize(), biometricsState)
            UpdateRequiredBlockingView(modifier = Modifier.fillMaxSize(), biometricsState = biometricsState)
        }
    }
}
