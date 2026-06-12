package com.flipcash.app.login

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.flipcash.app.analytics.Action
import com.flipcash.app.analytics.Button
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.core.onboarding.OnboardingResult
import com.flipcash.app.core.onboarding.OnboardingStep
import com.flipcash.app.login.internal.LoginAccessKeyViewModel
import com.flipcash.app.login.internal.screens.PhotoAccessKeyScreen
import com.flipcash.app.login.internal.screens.AccessKeyScreen
import com.flipcash.app.login.internal.screens.LoginRouterScreenContent
import com.flipcash.app.login.internal.screens.SeedInputContent
import com.flipcash.app.login.router.LoginViewModel
import com.flipcash.app.login.internal.SeedInputViewModel
import androidx.compose.runtime.rememberCoroutineScope
import com.flipcash.app.contacts.LocalContactCoordinator
import com.flipcash.app.permissions.asContactAccessHandle
import com.flipcash.app.permissions.internal.contacts.ContactScreenContent
import com.flipcash.app.permissions.internal.notifications.NotificationRationalePermissionContent
import com.flipcash.app.permissions.internal.notifications.NotificationScreenContent
import com.flipcash.app.purchase.internal.PurchaseAccountScreenContent
import com.flipcash.app.purchase.internal.PurchaseAccountViewModel
import com.flipcash.features.login.R
import com.flipcash.services.user.AuthState
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.NavOptions
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.util.permissions.LocalPermissionChecker
import com.getcode.util.permissions.PermissionConfigs
import com.getcode.util.permissions.PermissionResult
import com.getcode.util.permissions.rememberContactPermission
import com.getcode.util.permissions.rememberNotificationPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Entry point for the unified onboarding flow.
 *
 * ```
 * 1. New account (ResumePoint.Login → ProceedToVerification)
 *
 *    Start → Verification² → AccessKey ──┬──────────────→ Contacts¹ → Notifications → Scanner
 *                                         └→ Purchase ─┘
 *
 * 2. Seed restore (ResumePoint.Login → LoggedIn via SeedInput)
 *
 *    Start → SeedInput ──┬──────────────→ Contacts¹ → Notifications → Scanner
 *                         └→ Purchase ─┘
 *
 * 3. App resume (ResumePoint.PostAccessKey)
 *
 *    → Notifications → Scanner
 *    (contacts and verification skipped — existing users encounter these in-app)
 *
 * 4. Mid-flow resume (ResumePoint.AccessKey / AccessKeyThenPurchase)
 *
 *    Same as (1) but initialStack resumes at the AccessKey or Purchase step.
 * ```
 *
 * ¹ Contact permission is shown only when [FeatureFlag.PhoneNumberSend] is enabled
 *   **and** [FeatureFlag.ContactPickerMode] is off. When ContactPickerMode is on,
 *   contacts are accessed via the system picker at call site (no READ_CONTACTS needed).
 *   Already-granted permissions are auto-skipped via [PermissionsPhaseFlowHost].
 * ² Phone verification is shown only when [FeatureFlag.PhoneNumberSend] is enabled
 *   and no phone is linked. Skipped entirely when the flag is off.
 *   Uses `target` to replace the nav stack with AccessKey on success.
 */
@Composable
fun OnboardingFlowScreen(
    route: AppRoute.OnboardingFlow,
    resultStateRegistry: NavResultStateRegistry,
) {
    when {
        route.phase == AppRoute.OnboardingFlow.Phase.Permissions ->
            PermissionsPhaseFlowHost(route, resultStateRegistry)
        route.resumeAt == AppRoute.OnboardingFlow.ResumePoint.PostAccessKey ->
            CompleteExistingUserOnboarding()
        else ->
            AccountPhaseFlowHost(route, resultStateRegistry)
    }
}

@Composable
private fun CompleteExistingUserOnboarding() {
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(Unit) {
        navigator.replace(
            AppRoute.OnboardingFlow(
                phase = AppRoute.OnboardingFlow.Phase.Permissions,
                skipContacts = true,
            )
        )
    }
}

@Composable
private fun PermissionsPhaseFlowHost(
    route: AppRoute.OnboardingFlow,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current
    val checker = LocalPermissionChecker.current
    val contactConfig = PermissionConfigs.contacts()
    val notificationConfig = PermissionConfigs.notifications()
    val analytics = LocalAnalytics.current

    val featureFlags = LocalFeatureFlags.current
    val userManager = LocalUserManager.current
    val userFlags = userManager?.state?.collectAsStateWithLifecycle()?.value?.flags
    val phoneNumberSendFlagEnabled by featureFlags.observe(FeatureFlag.PhoneNumberSend).collectAsStateWithLifecycle()
    val phoneNumberSendEnabled = remember(userFlags?.enablePhoneNumberSend, phoneNumberSendFlagEnabled) {
        phoneNumberSendFlagEnabled || userFlags?.enablePhoneNumberSend == true
    }
    val contactPickerMode by featureFlags.observe(FeatureFlag.ContactPickerMode).collectAsStateWithLifecycle()

    val permissionsSteps = buildList {
        if (!route.skipContacts && phoneNumberSendEnabled && !contactPickerMode) add(OnboardingStep.ContactPermission)
        add(OnboardingStep.NotificationPermission)
    }

    // Compute resumeAt once per steps-list identity. This recomputes when the flag loads
    // (steps changes) but NOT when permissions are granted mid-flow, preventing a stale
    // recomposition from triggering a spurious BackedOutOfRoot exit.
    val resumeAt = remember(permissionsSteps.map { it::class }, contactPickerMode) {
        val contactsGranted = checker.isGranted(contactConfig.permission)
        val notificationsGranted = !notificationConfig.requiresRuntimeRequest ||
            checker.isGranted(notificationConfig.permission)
        when {
            !route.skipContacts && phoneNumberSendEnabled && !contactsGranted -> 0
            !notificationsGranted -> permissionsSteps.indexOfFirst {
                it is OnboardingStep.NotificationPermission
            }.coerceAtLeast(0)
            else -> permissionsSteps.size // all granted
        }
    }

    FlowHost<OnboardingStep, OnboardingResult>(
        steps = permissionsSteps,
        resumeAt = resumeAt,
        resultStateRegistry = resultStateRegistry,
        completedResult = OnboardingResult.Completed,
        onExit = { reason, _ ->
            when (reason) {
                is FlowExitReason.Completed -> {
                    analytics.action(Action.CompletedOnboarding)
                    userManager?.set(AuthState.Ready)
                    outerNavigator.navigate(
                        route = AppRoute.Main.Scanner,
                        options = NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll),
                    )
                }

                FlowExitReason.BackedOutOfRoot -> {
                    // All permissions already granted
                    userManager?.set(AuthState.Ready)
                    outerNavigator.navigate(
                        route = AppRoute.Main.Scanner,
                        options = NavOptions(popUpTo = NavOptions.PopUpTo.ClearAll),
                    )
                }

                FlowExitReason.Canceled -> Unit
            }
        },
        entryProvider = onboardingEntryProvider(route),
    )
}

@Composable
private fun AccountPhaseFlowHost(
    route: AppRoute.OnboardingFlow,
    resultStateRegistry: NavResultStateRegistry,
) {
    val outerNavigator = LocalCodeNavigator.current

    val initialStack = route.rememberInitialStack<OnboardingStep>()

    FlowHost(
        initialStack = initialStack,
        resultStateRegistry = resultStateRegistry,
        onExit = { reason, _ ->
            when (reason) {
                is FlowExitReason.Completed -> {
                    val route = resolvePostAccountRoute(reason.result)
                    route?.let { outerNavigator.replace(it) }
                }

                FlowExitReason.BackedOutOfRoot -> Unit
                FlowExitReason.Canceled -> Unit
            }
        },
        entryProvider = onboardingEntryProvider(route),
    )
}

internal fun resolvePostAccountRoute(
    result: OnboardingResult,
    skipContacts: Boolean = true,
): AppRoute? {
    val permissionsRoute = AppRoute.OnboardingFlow(
        phase = AppRoute.OnboardingFlow.Phase.Permissions,
        skipContacts = skipContacts,
    )
    return when (result) {
        is OnboardingResult.ProceedToVerification -> permissionsRoute
        OnboardingResult.LoggedIn -> permissionsRoute
        OnboardingResult.Completed -> null
    }
}

private fun onboardingEntryProvider(
    route: AppRoute.OnboardingFlow,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<OnboardingStep.Start> { step ->
        LoginStepContent(step.seed)
    }
    annotatedEntry<OnboardingStep.SeedInput> {
        SeedInputStepContent()
    }
    annotatedEntry<OnboardingStep.AccessKey> {
        AccessKeyStepContent()
    }
    annotatedEntry<OnboardingStep.AccessKeySavedLocation> {
        PhotoAccessKeyScreen()
    }
    annotatedEntry<OnboardingStep.Purchase> {
        PurchaseStepContent()
    }
    annotatedEntry<OnboardingStep.ContactPermission> {
        ContactPermissionStepContent()
    }
    annotatedEntry<OnboardingStep.NotificationPermission> {
        NotificationPermissionStepContent()
    }
    annotatedEntry<OnboardingStep.NotificationPermissionRationale> { step ->
        NotificationRationaleStepContent(permanentlyDenied = step.permanentlyDenied)
    }
}

// -- Step composables --

@Composable
private fun LoginStepContent(seed: String?) {
    val vm = hiltViewModel<LoginViewModel>()
    val state by vm.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()
    val outerNavigator = LocalOuterCodeNavigator.current
    var visible by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        activity?.reportFullyDrawn()
        visible = true
    }

    LaunchedEffect(vm) {
        vm.eventFlow
            .filterIsInstance<LoginViewModel.Event.OnAccountCreated>()
            .onEach {
                if (state.needsPhoneVerification) {
                    flowNavigator.navigate(
                        AppRoute.Verification(
                            origin = AppRoute.OnboardingFlow(),
                            includePhone = true,
                            includeEmail = false,
                            target = AppRoute.OnboardingFlow(
                                resumeAt = AppRoute.OnboardingFlow.ResumePoint.AccessKey,
                            ),
                            fullScreen = true,
                        )
                    )
                } else {
                    flowNavigator.navigateTo(OnboardingStep.AccessKey)
                }
            }
            .launchIn(this)
    }

    LaunchedEffect(vm) {
        vm.eventFlow
            .filterIsInstance<LoginViewModel.Event.LoggedInSuccessfully>()
            .onEach { delay(500) }
            .onEach { flowNavigator.exitWithResult(OnboardingResult.LoggedIn) }
            .launchIn(this)
    }

    LaunchedEffect(vm) {
        vm.eventFlow
            .filterIsInstance<LoginViewModel.Event.LoggedInRequiresPayment>()
            .onEach { delay(500) }
            .onEach {
                flowNavigator.replaceStack(
                    listOf(
                        OnboardingStep.Start(),
                        OnboardingStep.AccessKey,
                        OnboardingStep.Purchase,
                    )
                )
            }
            .launchIn(this)
    }

    LaunchedEffect(seed) {
        if (seed != null) {
            vm.dispatchEvent(LoginViewModel.Event.LogIn(seed, true))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
    ) {
        LoginRouterScreenContent(
            isLoggingIn = state.loggingIn,
            createAccount = { vm.dispatchEvent(LoginViewModel.Event.CreateAccount) },
            login = { flowNavigator.navigateTo(OnboardingStep.SeedInput) },
            isLabsOpen = state.betaOptionsVisible,
            onLogoTapped = { vm.dispatchEvent(LoginViewModel.Event.OnLogoTapped) },
            openBetaFlags = { outerNavigator.openAsSheet(AppRoute.Menu.Lab) },
        )
    }
}

@Composable
private fun SeedInputStepContent() {
    val viewModel: SeedInputViewModel = hiltViewModel()
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()

    Column {
        AppBarWithTitle(
            modifier = Modifier.fillMaxWidth(),
            backButton = true,
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { flowNavigator.back() },
            title = stringResource(R.string.title_enterAccessKeyWords),
        )
        SeedInputContent(
            viewModel = viewModel,
            onCantFind = { flowNavigator.navigateTo(OnboardingStep.AccessKeySavedLocation) },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                SeedInputViewModel.NavigationEvent.LoggedIn ->
                    flowNavigator.exitWithResult(OnboardingResult.LoggedIn)
                SeedInputViewModel.NavigationEvent.NeedsPurchase ->
                    flowNavigator.navigateTo(OnboardingStep.Purchase)
                SeedInputViewModel.NavigationEvent.ResetToLogin ->
                    flowNavigator.replaceStack(listOf(OnboardingStep.Start()))
                SeedInputViewModel.NavigationEvent.TimelockUnlocked ->
                    flowNavigator.replaceStack(listOf(OnboardingStep.Start()))
            }
        }
    }
}

@Composable
private fun AccessKeyStepContent() {
    val viewModel = hiltViewModel<LoginAccessKeyViewModel>()
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_accessKey),
            titleAlignment = Alignment.CenterHorizontally,
        )
        AccessKeyScreen(
            viewModel = viewModel,
            onExit = { flowNavigator.replaceStack(listOf(OnboardingStep.Start())) },
        ) { requiresIap ->
            if (requiresIap) {
                flowNavigator.navigateTo(OnboardingStep.Purchase)
            } else {
                flowNavigator.exitWithResult(OnboardingResult.ProceedToVerification)
            }
        }

        BackHandler { /* swallow back during onboarding permissions */ }
    }
}

@Composable
private fun PurchaseStepContent() {
    val viewModel = hiltViewModel<PurchaseAccountViewModel>()
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PurchaseAccountViewModel.Event.OnAccountCreated>()
            .onEach { flowNavigator.exitWithResult(OnboardingResult.ProceedToVerification) }
            .launchIn(this)
    }

    Column {
        AppBarWithTitle()
        PurchaseAccountScreenContent(state, viewModel::dispatchEvent)
    }


    BackHandler { /* swallow back during onboarding permissions */ }
}

@Composable
private fun ContactPermissionStepContent() {
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()
    val analytics = LocalAnalytics.current
    val contactCoordinator = LocalContactCoordinator.current
    val scope = rememberCoroutineScope()

    val permissionState = rememberContactPermission { result ->
        when (result) {
            PermissionResult.Granted -> {
                analytics.action(Button.AllowContacts)
                scope.launch { contactCoordinator.sync() }
                flowNavigator.proceed()
            }
            PermissionResult.Denied,
            PermissionResult.PermanentlyDenied -> flowNavigator.proceed()
            PermissionResult.NotRequested -> Unit
        }
    }

    ContactScreenContent(
        accessHandle = permissionState.asContactAccessHandle(),
        onSkip = {
            analytics.action(Button.SkipContacts)
            flowNavigator.proceed()
        },
    )

    BackHandler { /* swallow back during onboarding permissions */ }
}

@Composable
private fun NotificationPermissionStepContent() {
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()
    val analytics = LocalAnalytics.current
    val checker = LocalPermissionChecker.current
    val notificationConfig = PermissionConfigs.notifications()

    // Auto-complete if notification permission is already granted or not required.
    val alreadyGranted = !notificationConfig.requiresRuntimeRequest ||
            checker.isGranted(notificationConfig.permission)
    if (alreadyGranted) {
        LaunchedEffect(Unit) { flowNavigator.proceed() }
        return
    }

    val permissionState = rememberNotificationPermission { result ->
        when (result) {
            PermissionResult.Granted -> {
                analytics.action(Button.AllowPush)
                flowNavigator.proceed()
            }
            PermissionResult.Denied -> flowNavigator.navigateTo(
                OnboardingStep.NotificationPermissionRationale(false)
            )
            PermissionResult.PermanentlyDenied -> flowNavigator.navigateTo(
                OnboardingStep.NotificationPermissionRationale(true)
            )
            PermissionResult.NotRequested -> Unit
        }
    }

    NotificationScreenContent(
        permissionState = permissionState,
        onSkip = {
            analytics.action(Button.SkipPush)
            flowNavigator.proceed()
        },
    )

    BackHandler { /* swallow back during onboarding permissions */ }
}

@Composable
private fun NotificationRationaleStepContent(permanentlyDenied: Boolean) {
    val flowNavigator = rememberFlowNavigator<OnboardingStep, OnboardingResult>()
    NotificationRationalePermissionContent(
        permanentlyDenied = permanentlyDenied,
        onComplete = { flowNavigator.proceed() },
    )
}
