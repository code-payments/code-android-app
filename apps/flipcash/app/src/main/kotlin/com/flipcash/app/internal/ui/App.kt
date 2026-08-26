package com.flipcash.app.internal.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.analytics.rememberAnalytics
import com.flipcash.app.android.BuildConfig
import com.flipcash.app.bill.customization.BillPlaygroundScaffold
import com.flipcash.app.cardexpand.CardExpansionController
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.verification.email.LocalEmailCodeChannel
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.featureflags.model.BackgroundResetTimeout
import androidx.core.net.toUri
import com.flipcash.app.MainActivity
import com.flipcash.app.internal.ui.navigation.AppContent
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
import com.getcode.utils.trace
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
import com.getcode.theme.CodeTheme
import com.getcode.ui.biometrics.LocalBiometricsState
import com.getcode.ui.biometrics.rememberBiometricsState
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.components.bars.rememberBarManager
import com.getcode.ui.core.RestrictionType
import com.getcode.ui.utils.rememberKeyboardController
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
    val userState by userManager.state.collectAsStateWithLifecycle()

    // Card-expand (iOS #587) is owned HERE rather than inside AppContent because the deeplink
    // handling below sits outside the shell: a `/token` link opens the wallet's expanded card
    // (see DeeplinkAction.OpenToken), which needs the controller. AppContent provides it to the
    // tree.
    val context = LocalContext.current
    val cardExpansion = remember(context) {
        CardExpansionController().apply {
            // Feed the controller the user's real animation-scale so the expand honours "animations
            // off" (accessibility/battery) yet isn't fooled by a stale ambient MotionDurationScale.
            val resolver = context.contentResolver
            animationScale = {
                android.provider.Settings.Global.getFloat(
                    resolver,
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }
        }
    }

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

                    // UI_TESTABLE is true for debug + the profile-gen/benchmark variants and
                    // false for the shipping release, so testTags aren't exposed in production.
                    val semanticsModifier = if (BuildConfig.UI_TESTABLE) {
                        Modifier.semantics { testTagsAsResourceId = true }
                    } else Modifier

                    val scrimController = remember { ScrimController() }

                    Box(modifier = semanticsModifier.fillMaxSize().background(CodeTheme.colors.background)) {
                        SharedTransitionLayout {
                            CompositionLocalProvider(
                                LocalCodeNavigator provides codeNavigator,
                                LocalBiometricsState provides biometricsState,
                                LocalScrimController provides scrimController,
                                LocalSharedTransitionScope provides this,
                            ) {
                                    CoinbaseOnRampHandler {
                                        AppContent(
                                            codeNavigator = codeNavigator,
                                            resultStateRegistry = resultStateRegistry,
                                            barManager = barManager,
                                            cardExpansion = cardExpansion,
                                            deepLink = { deepLink },
                                            onPendingAction = { action ->
                                                deeplinkHandled = true
                                                when (action) {
                                                    // Cold start: the wallet is already the launch
                                                    // home, so the token just opens as its expanded
                                                    // card on top of it.
                                                    is DeeplinkAction.OpenToken ->
                                                        cardExpansion.beginExpanded(action.mint)
                                                    is DeeplinkAction.OpenCashLink ->
                                                        session.openCashLink(action.entropy)
                                                    is DeeplinkAction.PresentTipCard ->
                                                        session.resolveTipCard(action.owner)
                                                    is DeeplinkAction.OpenExternally ->
                                                        context.openInBrowser(action.url)
                                                    is DeeplinkAction.Login ->
                                                        viewModel.handleLoginEntropy(
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
                                                    else -> {}
                                                }
                                                deepLink = null
                                            }
                                        )

                                        // The scrim + bill overlay are hosted per-entry by
                                        // NavBillOverlayEntryDecorator (added to the AppNavHost
                                        // decorators) rather than as app-root siblings — that keeps
                                        // the bill above the current screen while letting a bottom
                                        // sheet (hosted in the same NavDisplay) render ABOVE the
                                        // bill. See NavBillOverlayEntryDecorator.
                                    }

                                val emailCodeChannel = LocalEmailCodeChannel.current
                                val currentRoute = codeNavigator.currentRouteKey
                                val keyboard = rememberKeyboardController()
                                LaunchedEffect(deepLink, currentRoute) {
                                    val link = deepLink ?: return@LaunchedEffect

                                    if (currentRoute is AppRoute.Loading) {
                                        // Cold start — MainRoot handles Navigate actions;
                                        // other actions (OpenCashLink, Login) wait until
                                        // navigation leaves Loading.
                                        return@LaunchedEffect
                                    }

                                    val action = router.dispatch(link)
                                    deeplinkHandled = action != DeeplinkAction.None

                                    // A link can land while a text field elsewhere in the app still
                                    // holds focus — the common case is resuming from the background
                                    // straight out of a chat, where the window restores the IME for
                                    // the still-focused input as we route. Take the keyboard down
                                    // before anything is presented, so a tip card doesn't come up
                                    // over a keyboard, and none appears while the card is still
                                    // resolving.
                                    if (action != DeeplinkAction.None) keyboard.hide()

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

                                        is DeeplinkAction.OpenToken -> {
                                            // Land on the wallet tab (clearing anything pushed on
                                            // it) and open the token as its EXPANDED CARD — the
                                            // same overlay, chrome and dismissal a tap on the card
                                            // gives. Pushing it instead reads as a modal on a stack
                                            // the user never navigated. Mirrors iOS
                                            // DeepLinkController's requestedCardMint.
                                            codeNavigator.navigateAll(listOf(AppRoute.Sheets.Wallet))
                                            cardExpansion.beginExpanded(action.mint)
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

                                        is DeeplinkAction.PresentTipCard ->
                                            session.resolveTipCard(action.owner)
                                        is DeeplinkAction.OpenExternally ->
                                            context.openInBrowser(action.url)
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

/**
 * Hand a link back to the web — the tail of [DeeplinkAction.OpenExternally].
 *
 * Not `ChromeTabsUtils.launchUrl`, and not `LocalUriHandler`: both send a package-less `ACTION_VIEW`,
 * and this URL is on a host we are a verified handler for, so it would resolve straight back to us
 * and the tap would loop. The browser has to be named. Resolving the default one keeps the hop
 * invisible, which is what a tap on `flipcash.com/download` should feel like; when there isn't one to
 * name — no default set, or the resolver activity answered — the chooser does it instead, with our
 * own activity excluded so it can't be picked.
 */
private fun Context.openInBrowser(url: String) {
    val uri = url.toUri()
    val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    // Probed against a host we make no claim on, so the answer is a browser rather than ourselves.
    val probe = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
    val browser = packageManager
        .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName
        ?.takeIf { it != packageName && it != "android" }

    val intent = if (browser != null) {
        view.setPackage(browser)
    } else {
        Intent.createChooser(view, null).putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, MainActivity::class.java)),
        )
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { startActivity(intent) }
        .onFailure { trace(tag = "Deeplink", message = "No browser to open $url", error = it) }
}
