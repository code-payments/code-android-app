package com.getcode.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.navigation.core.FlowScope
import com.getcode.navigation.flow.FlowRouteWithResult
import com.getcode.navigation.flow.FlowStep
import kotlinx.parcelize.Parcelize

// --- App-level routes (NOT FlowSteps) ---
data object AppHome : NavKey
data object AppRegion : NavKey
data object CallerScreen : NavKey

// --- A sheet route ---
data object DemoSheet : Sheet

// --- Flow steps ---
data object StepOne : FlowStep
data object StepTwo : FlowStep

// --- A flow-host route that returns DemoResult ---
@Parcelize
data class DemoResult(val value: String) : Parcelable

data class DemoFlow(
    override val initialStack: List<NavKey> = listOf(StepOne),
) : FlowRouteWithResult<DemoResult>

/** Records what a flow scope was asked to do, without any real flow/sheet plumbing. */
class RecordingFlowScope(
    override val isSheetRoot: Boolean = false,
) : FlowScope {
    val calls = mutableListOf<String>()
    var deliveredResult: Parcelable? = null

    override fun exitWithResult(result: Parcelable) {
        deliveredResult = result
        calls += "exitWithResult"
    }

    override fun dismiss() {
        calls += "dismiss"
    }
}

/** Builds a [CodeNavigator] over a real [NavBackStack] seeded with [keys]. */
fun testNavigator(
    vararg keys: NavKey,
    parent: CodeNavigator? = null,
    isFlow: Boolean = false,
    scope: FlowScope? = null,
    onRootReached: () -> Unit = {},
): CodeNavigator {
    require(keys.isNotEmpty()) { "seed the navigator with at least one key" }
    val backStack = NavBackStack<NavKey>(keys.first()).apply {
        keys.drop(1).forEach { add(it) }
    }
    return CodeNavigator(
        backStack = backStack,
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = onRootReached,
        parent = parent,
        isFlowNavigator = isFlow,
        flowScope = scope,
    )
}
