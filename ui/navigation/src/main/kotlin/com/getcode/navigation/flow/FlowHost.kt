package com.getcode.navigation.flow

import android.os.Parcelable
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.getcode.navigation.AppNavHost
import com.getcode.navigation.NonDismissableRoute
import com.getcode.navigation.NonDraggableRoute
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.NavOptions
import com.getcode.navigation.core.rememberCodeNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.asKey
import com.getcode.navigation.scenes.LocalSheetNavigator

/**
 * Why the flow exited.
 */
sealed interface FlowExitReason<out R : Parcelable> {
    /** A step called [FlowNavigator.exitWithResult] with a typed result. */
    data class Completed<R : Parcelable>(val result: R) : FlowExitReason<R>

    /** A step called [FlowNavigator.exitCanceled]. */
    data object Canceled : FlowExitReason<Nothing>

    /**
     * The user pressed back at the root of the inner back stack, or the initial stack was empty
     * at entry time.
     */
    data object BackedOutOfRoot : FlowExitReason<Nothing>
}

private val DefaultFlowTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
    }
}

private val DefaultFlowPopTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    if (targetState is OverlayScene<*> || initialState is OverlayScene<*>) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
    }
}

/**
 * Linear flow overload — for ordered flows with [FlowNavigator.proceed].
 *
 * The backstack is seeded with `steps[resumeAt]`. Calling [FlowNavigator.proceed] advances
 * through the [steps] list in order, or exits with [completedResult] at the end.
 *
 * @param steps        Ordered list of steps in the flow.
 * @param resumeAt     Index in [steps] to start at. If `>= steps.size`, the flow exits
 *                     immediately with [FlowExitReason.BackedOutOfRoot] (all steps done).
 * @param completedResult Result delivered when the flow reaches the end of [steps].
 * @param onProceed    Optional callback invoked by [FlowNavigator.proceed] before the default
 *                     step-list behavior. Return `true` if handled, `false` to fall through.
 *                     Receives [FlowNavigator] as `this` so it can call [FlowNavigator.navigateTo],
 *                     [FlowNavigator.exitWithResult], etc.
 */
@Composable
fun <S : FlowStep, R : Parcelable> FlowHost(
    steps: List<S>,
    resumeAt: Int = 0,
    resultStateRegistry: NavResultStateRegistry,
    onExit: (reason: FlowExitReason<R>, isSheetRoot: Boolean) -> Unit,
    completedResult: R? = null,
    onProceed: (FlowNavigator<S, R>.(currentStep: S) -> Boolean)? = null,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>> = emptyList(),
    sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowTransitionSpec,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowPopTransitionSpec,
) {
    val clampedResumeAt = resumeAt.coerceIn(0, steps.size)
    val initialStack = if (clampedResumeAt < steps.size) listOf(steps[clampedResumeAt]) else emptyList()
    FlowHostImpl(
        initialStack = initialStack,
        steps = steps,
        completedResult = completedResult,
        onProceed = onProceed,
        resultStateRegistry = resultStateRegistry,
        onExit = onExit,
        entryProvider = entryProvider,
        decorators = decorators,
        sceneStrategies = sceneStrategies,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
    )
}

/**
 * Non-linear flow overload — for flows that manage their own navigation.
 *
 * The backstack is seeded with all items in [initialStack]. [FlowNavigator.proceed] is a no-op;
 * steps use [FlowNavigator.navigateTo] / [FlowNavigator.exitWithResult] directly.
 */
@Composable
fun <S : FlowStep, R : Parcelable> FlowHost(
    initialStack: List<S>,
    resultStateRegistry: NavResultStateRegistry,
    onExit: (reason: FlowExitReason<R>, isSheetRoot: Boolean) -> Unit,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>> = emptyList(),
    sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowTransitionSpec,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowPopTransitionSpec,
) {
    FlowHostImpl(
        initialStack = initialStack,
        steps = emptyList(),
        completedResult = null,
        onProceed = null,
        resultStateRegistry = resultStateRegistry,
        onExit = onExit,
        entryProvider = entryProvider,
        decorators = decorators,
        sceneStrategies = sceneStrategies,
        transitionSpec = transitionSpec,
        popTransitionSpec = popTransitionSpec,
    )
}

@Composable
private fun <S : FlowStep, R : Parcelable> FlowHostImpl(
    initialStack: List<S>,
    steps: List<S>,
    completedResult: R?,
    onProceed: (FlowNavigator<S, R>.(currentStep: S) -> Boolean)?,
    resultStateRegistry: NavResultStateRegistry,
    onExit: (reason: FlowExitReason<R>, isSheetRoot: Boolean) -> Unit,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>> = emptyList(),
    sceneStrategies: List<SceneStrategy<NavKey>> = listOf(SinglePaneSceneStrategy()),
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowTransitionSpec,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowPopTransitionSpec,
) {
    // Capture the outer flow entry's VM store owner before any override below.
    val flowOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "FlowHost requires a LocalViewModelStoreOwner (the outer flow entry's owner)"
    }

    // Stable references via rememberUpdatedState to avoid re-creating the navigator.
    val currentOnExit = rememberUpdatedState(onExit)
    val currentOnProceed = rememberUpdatedState(onProceed)
    val currentSteps = rememberUpdatedState(steps)
    val currentCompletedResult = rememberUpdatedState(completedResult)

    if (initialStack.isEmpty()) {
        LaunchedEffect(Unit) { currentOnExit.value(FlowExitReason.BackedOutOfRoot, false) }
        return
    }

    // Capture outer navigator and sheet context before overriding locals below.
    val outerNavigator = LocalCodeNavigator.current
    val sheetNavigator = LocalSheetNavigator.current
    val isSheetRoot = remember { sheetNavigator != null && outerNavigator.backStack.size <= 1 }

    // Seed the inner back stack from the initial step list.
    // Uses rememberSaveable so the stack survives composition removal (e.g. when a
    // sheet-level screen like RegionSelection is pushed on top via the outer navigator).
    val innerBackStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { saved ->
                NavBackStack<NavKey>(saved.first()).apply {
                    saved.drop(1).forEach { add(it) }
                }
            }
        )
    ) {
        @Suppress("UNCHECKED_CAST")
        NavBackStack<NavKey>(initialStack.first() as NavKey).apply {
            initialStack.drop(1).forEach { add(it as NavKey) }
        }
    }

    // Re-seed if the caller's initialStack changed before the user has navigated.
    // This handles the case where an async value (e.g. feature flag) settles after
    // the first composition already seeded the backstack with a stale value.
    // We track what was seeded and only re-seed if the backstack is still untouched.
    // Animation is suppressed during re-seed so NavDisplay doesn't slide between
    // the stale and corrected content.
    val seededStack = remember { initialStack.map { it::class } }
    val currentInitialClasses = initialStack.map { it::class }
    val suppressTransition = remember { mutableStateOf(false) }
    LaunchedEffect(currentInitialClasses) {
        if (currentInitialClasses == seededStack) return@LaunchedEffect
        val backstackClasses = innerBackStack.map { it::class }
        if (backstackClasses != seededStack) return@LaunchedEffect
        suppressTransition.value = true
        Snapshot.withMutableSnapshot {
            innerBackStack.clear()
            @Suppress("UNCHECKED_CAST")
            initialStack.forEach { innerBackStack.add(it as NavKey) }
        }
        // Wait two frames so NavDisplay processes the change under the suppressed
        // spec before restoring normal transitions.
        withFrameNanos {}
        withFrameNanos {}
        suppressTransition.value = false
    }

    // Build the inner navigator + flow navigator once and keep them stable.
    // onRootReached and onExit read through rememberUpdatedState so the references
    // never change — preventing unnecessary recompositions of children that read
    // the composition locals.
    val innerNavigator = rememberCodeNavigator(
        backStack = innerBackStack,
        resultStateRegistry = resultStateRegistry,
        onRootReached = remember { { currentOnExit.value(FlowExitReason.BackedOutOfRoot, isSheetRoot) } },
    )

    val flowNavigator = remember(innerNavigator) {
        InnerFlowNavigator<S, R>(
            navigator = innerNavigator,
            outerNavigator = outerNavigator,
            onExit = { reason -> currentOnExit.value(reason, isSheetRoot) },
            steps = { currentSteps.value },
            completedResult = { currentCompletedResult.value },
            onProceed = { step -> currentOnProceed.value?.invoke(this, step) ?: false },
        )
    }

    // Propagate NonDismissableRoute / NonDraggableRoute from the current inner step
    // to the enclosing sheet so that drag-to-dismiss is blocked when a step requires it.
    if (sheetNavigator != null) {
        val currentInnerRoute by remember {
            derivedStateOf { innerBackStack.lastOrNull() }
        }
        val isDragDisabled = currentInnerRoute is NonDraggableRoute
        val isDismissDisabled = currentInnerRoute is NonDismissableRoute
        DisposableEffect(isDragDisabled, isDismissDisabled) {
            if (isDragDisabled) sheetNavigator.sheetDragDisabled = true
            if (isDismissDisabled) sheetNavigator.sheetDismissDisabled = true
            onDispose {
                if (isDragDisabled) sheetNavigator.sheetDragDisabled = false
                if (isDismissDisabled) sheetNavigator.sheetDismissDisabled = false
            }
        }
    }

    // Compute dismiss style for AppBarWithTitle auto-swap (back arrow vs close X).
    val dismissStyle by remember {
        derivedStateOf {
            val atRoot = innerBackStack.size <= 1
            if (isSheetRoot && atRoot) FlowDismissStyle.Close else FlowDismissStyle.BackArrow
        }
    }

    CompositionLocalProvider(
        LocalOuterCodeNavigator provides outerNavigator,
        LocalCodeNavigator provides innerNavigator,
        LocalFlowNavigator provides flowNavigator,
        LocalFlowViewModelStoreOwner provides flowOwner,
        LocalFlowDismissStyle provides dismissStyle,
    ) {
        val noTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
            { EnterTransition.None togetherWith ExitTransition.None }
        AppNavHost(
            navigator = innerNavigator,
            resultStateRegistry = resultStateRegistry,
            sceneStrategies = sceneStrategies,
            transitionSpec = if (suppressTransition.value) noTransition else transitionSpec,
            popTransitionSpec = if (suppressTransition.value) noTransition else popTransitionSpec,
            onBack = { innerNavigator.navigateBack() },
            decorators = decorators,
            entryProvider = entryProvider,
        )
    }
}

private class InnerFlowNavigator<S : FlowStep, R : Parcelable>(
    private val navigator: CodeNavigator,
    private val outerNavigator: CodeNavigator,
    private val onExit: (FlowExitReason<R>) -> Unit,
    private val steps: () -> List<S>,
    private val completedResult: () -> R?,
    private val onProceed: (FlowNavigator<S, R>.(S) -> Boolean)?,
) : FlowNavigator<S, R> {

    @Suppress("UNCHECKED_CAST")
    override val currentStep: S?
        get() = navigator.backStack.lastOrNull() as? S

    override val canGoBack: Boolean
        get() = navigator.backStack.size > 1

    override fun navigateTo(step: S, popCurrent: Boolean) {
        navigator.navigate(
            route = step,
            options = if (popCurrent) NavOptions(popUpTo = NavOptions.PopUpTo.PopLast) else NavOptions(),
        )
    }

    override fun replaceStack(steps: List<S>) {
        if (steps.isEmpty()) {
            onExit(FlowExitReason.BackedOutOfRoot)
            return
        }
        Snapshot.withMutableSnapshot {
            navigator.backStack.clear()
            steps.forEach { navigator.backStack.add(it) }
        }
    }

    override fun back(): Boolean {
        return if (canGoBack) {
            navigator.backStack.removeAt(navigator.backStack.lastIndex)
            true
        } else {
            onExit(FlowExitReason.BackedOutOfRoot)
            false
        }
    }

    override fun exitWithResult(result: R) {
        onExit(FlowExitReason.Completed(result))
    }

    override fun exitCanceled() {
        onExit(FlowExitReason.Canceled)
    }

    override fun proceed() {
        val step = currentStep ?: return
        val currentSteps = steps()
        if (currentSteps.isEmpty()) return // non-linear flow, proceed is a no-op

        // Let the callback handle it first
        if (onProceed?.invoke(this, step) == true) return

        // Default: advance through the steps list.
        // If the current step isn't in the list (e.g. a rationale sub-step), scan the
        // backstack for the most recent step that IS in the list and advance from there.
        val anchorIndex = currentSteps.indexOfFirst { it::class == step::class }.let { idx ->
            if (idx >= 0) return@let idx
            navigator.backStack.asReversed().firstNotNullOfOrNull { entry ->
                currentSteps.indexOfFirst { it::class == entry::class }.takeIf { it >= 0 }
            } ?: -1
        }
        val nextIndex = anchorIndex + 1
        if (anchorIndex >= 0 && nextIndex <= currentSteps.lastIndex) {
            navigateTo(currentSteps[nextIndex])
        } else {
            val result = completedResult()
            if (result != null) exitWithResult(result)
        }
    }

    override fun navigate(route: NavKey) {
        outerNavigator.push(route)
    }
}

/**
 * Deliver [value] to the entry below [route] in the outer back stack.
 * This mirrors the lookup in [com.getcode.navigation.results.ResultBackNavigator.returnResult]
 * but targets the flow route's position in the back stack, not the current top.
 *
 * Intended to be called from a [FlowHost] wrapper's `onExit` handler on the *outer* navigator —
 * capture the outer navigator before providing a child one via [FlowHost].
 */
inline fun <reified T : Parcelable> CodeNavigator.deliverFlowResult(
    route: FlowRouteWithResult<T>,
    value: NavResultOrCanceled<T>,
) {
    val routeIndex = backStack.indexOfLast { it == route }
    if (routeIndex <= 0) return
    val callerId = backStack[routeIndex - 1]
    resultStore.deliverOrPersist(
        callerEntryId = callerId,
        key = route.asKey(),
        value = value,
    )
}
