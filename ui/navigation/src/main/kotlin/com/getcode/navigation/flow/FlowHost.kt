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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * Hosts a multi-step flow inside its own private [NavBackStack]. Generalises the nested sub-
 * NavHost pattern used by `SheetContent` into a reusable primitive.
 *
 * Typical use — a flow screen wrapper that lives in the outer entry provider:
 * ```
 * @Composable
 * fun MyFlowScreen(route: AppRoute.MyFlow, resultStateRegistry: NavResultStateRegistry) {
 *     val outer = LocalCodeNavigator.current
 *     FlowHost<MyStep, MyResult>(
 *         initialStack = route.initialStack.filterIsInstance<MyStep>(),
 *         resultStateRegistry = resultStateRegistry,
 *         onExit = { reason ->
 *             val result = when (reason) {
 *                 is FlowExitReason.Completed -> reason.result
 *                 FlowExitReason.Canceled,
 *                 FlowExitReason.BackedOutOfRoot -> MyResult.Canceled
 *             }
 *             outer.deliverFlowResult(route, NavResultOrCanceled.ReturnValue(result))
 *             outer.pop()
 *         },
 *         entryProvider = myEntryProvider(route),
 *     )
 * }
 * ```
 *
 * The host captures [LocalViewModelStoreOwner] at the call site (the outer flow entry's own
 * [androidx.lifecycle.ViewModelStoreOwner]) and exposes it as [LocalFlowViewModelStoreOwner] so
 * that [flowSharedViewModel] can resolve a single shared [androidx.lifecycle.ViewModel] across
 * all steps in the flow.
 */
@Composable
fun <S : FlowStep, R : Parcelable> FlowHost(
    initialStack: List<S>,
    resultStateRegistry: NavResultStateRegistry,
    onExit: (FlowExitReason<R>) -> Unit,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
    decorators: List<NavEntryDecorator<NavKey>> = emptyList(),
    sceneStrategy: SceneStrategy<NavKey> = SinglePaneSceneStrategy(),
    transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowTransitionSpec,
    popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform =
        DefaultFlowPopTransitionSpec,
) {
    // Capture the outer flow entry's VM store owner before any override below.
    val flowOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "FlowHost requires a LocalViewModelStoreOwner (the outer flow entry's owner)"
    }

    // Exit path needs to be a stable reference to avoid re-creating the navigator.
    val currentOnExit = rememberUpdatedState(onExit)

    if (initialStack.isEmpty()) {
        LaunchedEffect(Unit) { currentOnExit.value(FlowExitReason.BackedOutOfRoot) }
        return
    }

    // Seed the inner back stack from the initial step list.
    val innerBackStack = remember {
        @Suppress("UNCHECKED_CAST")
        NavBackStack<NavKey>(initialStack.first() as NavKey).apply {
            initialStack.drop(1).forEach { add(it as NavKey) }
        }
    }

    // Build the inner navigator + flow navigator once and keep them stable.
    // onRootReached and onExit read through rememberUpdatedState so the references
    // never change — preventing unnecessary recompositions of children that read
    // the composition locals.
    val innerNavigator = rememberCodeNavigator(
        backStack = innerBackStack,
        resultStateRegistry = resultStateRegistry,
        onRootReached = remember { { currentOnExit.value(FlowExitReason.BackedOutOfRoot) } },
    )

    val flowNavigator = remember(innerNavigator) {
        InnerFlowNavigator<S, R>(
            navigator = innerNavigator,
            onExit = { reason -> currentOnExit.value(reason) },
        )
    }

    // Propagate NonDismissableRoute / NonDraggableRoute from the current inner step
    // to the enclosing sheet so that drag-to-dismiss is blocked when a step requires it.
    val sheetNavigator = LocalSheetNavigator.current
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

    // Expose the outer navigator so that flowAnnotatedEntry (or manual overrides)
    // can restore it for steps that need to push routes onto the app-level nav graph.
    val outerNavigator = LocalCodeNavigator.current

    CompositionLocalProvider(
        LocalOuterCodeNavigator provides outerNavigator,
        LocalCodeNavigator provides innerNavigator,
        LocalFlowNavigator provides flowNavigator,
        LocalFlowViewModelStoreOwner provides flowOwner,
    ) {
        AppNavHost(
            navigator = innerNavigator,
            resultStateRegistry = resultStateRegistry,
            sceneStrategy = sceneStrategy,
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            onBack = { innerNavigator.navigateBack() },
            decorators = decorators,
            entryProvider = entryProvider,
        )
    }
}

private class InnerFlowNavigator<S : FlowStep, R : Parcelable>(
    private val navigator: CodeNavigator,
    private val onExit: (FlowExitReason<R>) -> Unit,
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
