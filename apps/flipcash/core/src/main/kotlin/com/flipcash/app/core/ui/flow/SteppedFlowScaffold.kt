package com.flipcash.app.core.ui.flow

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.getcode.navigation.flow.FlowExitReason
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.FlowStep
import com.getcode.navigation.results.NavResultStateRegistry

/**
 * Renders the shared stepped-progress chrome above a [FlowHost]. Owns the progress computation
 * (from [SteppedFlowRoute.progressSteps] and the caller-provided [currentStep]) so consumers no
 * longer track progress in their ViewModel. Per-step [titleContent] / [endContent] are supplied by
 * the consumer (they read their own state); each step wires back / end-action through
 * [LocalFlowStepBar].
 *
 * @param currentStep the step currently shown, typically read from the shared flow ViewModel's state.
 */
@Composable
fun <S : FlowStep, R : Parcelable> SteppedFlowScaffold(
    route: SteppedFlowRoute<R>,
    initialStack: List<S>,
    currentStep: FlowStep?,
    resultStateRegistry: NavResultStateRegistry,
    onExit: (reason: FlowExitReason<R>, isSheetRoot: Boolean) -> Unit,
    titleContent: (@Composable () -> Unit)? = null,
    endContent: (@Composable () -> Unit)? = null,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    val controller = remember { FlowStepBarController() }
    controller.progress = flowProgressFor(currentStep, route.progressSteps)

    CompositionLocalProvider(LocalFlowStepBar provides controller) {
        Column(modifier = Modifier.fillMaxSize()) {
            FlowStepBar(
                controller = controller,
                mainContent = titleContent,
                endContent = endContent,
            )
            FlowHost(
                initialStack = initialStack,
                resultStateRegistry = resultStateRegistry,
                onExit = onExit,
                entryProvider = entryProvider,
            )
        }
    }
}
