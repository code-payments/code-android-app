package com.getcode.navigation.flow

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.core.CodeNavigator

/**
 * Controls navigation inside a [FlowHost]. Steps use this instead of [com.getcode.navigation.core.CodeNavigator]
 * to move between steps and exit the flow with a result.
 *
 * @param S the sealed hierarchy of [FlowStep]s owned by this flow.
 * @param R the [Parcelable] result type returned when the flow completes.
 */
interface FlowNavigator<S : FlowStep, R : Parcelable> {
    /** The step currently on top of the inner back stack, if any. */
    val currentStep: S?

    /** Whether there is more than one entry on the inner back stack. */
    val canGoBack: Boolean

    /**
     * Push [step] onto the inner back stack.
     * If [popCurrent] is true, the current step is replaced rather than pushed on top of.
     */
    fun navigateTo(step: S, popCurrent: Boolean = false)

    /** Replace the entire inner back stack with [steps]. */
    fun replaceStack(steps: List<S>)

    /**
     * Pop the current step. Returns true if a step was popped, or false if this was the root of
     * the flow (in which case the flow itself should be exited by the host).
     */
    fun back(): Boolean

    /** Exit the flow, delivering [result] to the caller. */
    fun exitWithResult(result: R)

    /** Exit the flow without delivering a result (caller sees it as a cancellation). */
    fun exitCanceled()

    /**
     * Advance the flow from the current step.
     *
     * **Linear flows** (created with the `steps` + `resumeAt` overload of [FlowHost]):
     * tries `onProceed` first — if it returns `true`, the callback handled navigation.
     * Otherwise, advances to the next step in the `steps` list, or exits with `completedResult`
     * when the end is reached. Steps not in the list (e.g. sub-steps like rationale screens)
     * scan the backstack for the most recent parent that *is* in the list and advance from there.
     *
     * **Non-linear flows** (created with the `initialStack` overload): this is a no-op.
     * Steps use [navigateTo] / [exitWithResult] directly.
     */
    fun proceed()

    /**
     * Push [route] onto the *outer* (app-level) navigator.
     * Use this when a flow step needs to open a screen outside the flow
     * (e.g. region selection, token selection) without referencing the outer navigator directly.
     */
    fun navigate(route: NavKey)
}

val LocalFlowNavigator =
    staticCompositionLocalOf<FlowNavigator<*, *>> { error("No FlowNavigator provided") }

/**
 * A no-op [FlowNavigator] for use in Compose `@Preview` functions.
 * All navigation calls are silently ignored.
 */
class PreviewFlowNavigator<S : FlowStep, R : Parcelable> : FlowNavigator<S, R> {
    override val currentStep: S? = null
    override val canGoBack: Boolean = false
    override fun navigateTo(step: S, popCurrent: Boolean) {}
    override fun replaceStack(steps: List<S>) {}
    override fun back(): Boolean = false
    override fun exitWithResult(result: R) {}
    override fun exitCanceled() {}
    override fun proceed() {}
    override fun navigate(route: NavKey) {}
}

/**
 * Returns the currently provided [FlowNavigator] typed to the step and result types of the
 * enclosing [FlowHost]. The cast is unchecked but is safe by construction — a step composable
 * only runs inside the [FlowHost] that owns its step type.
 */
@Composable
inline fun <reified S : FlowStep, reified R : Parcelable> rememberFlowNavigator(): FlowNavigator<S, R> {
    @Suppress("UNCHECKED_CAST")
    return LocalFlowNavigator.current as FlowNavigator<S, R>
}
