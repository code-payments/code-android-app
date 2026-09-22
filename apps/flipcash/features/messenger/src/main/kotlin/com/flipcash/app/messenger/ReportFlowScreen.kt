package com.flipcash.app.messenger

import android.os.Parcelable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.core.reporting.ReportStep
import com.flipcash.app.messenger.internal.ReportViewModel
import com.flipcash.app.messenger.internal.screens.ReasonSelectionContent
import com.flipcash.app.messenger.internal.screens.ReportDetailsContent
import com.flipcash.reporting.ReportReason
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.utils.rememberKeyboardController

/**
 * Reporting a person, a group, or a message.
 *
 * Two steps, and most reports only see the first: picking a reason files it. Only
 * [ReportReason.Other] has a second screen, because only it has nothing to go on without one.
 *
 * Steps rather than one screen that grows: the earlier version swapped its own contents inside a
 * wrap-content sheet and deadlocked the main thread. Such a sheet takes its drag anchors from the
 * height its content reports and reads those anchors again while placing that content, so content
 * that resizes itself keeps changing the number it was measured against, and the loop outruns the
 * frame clock that would have ended it. Here the route is a fixed-height sheet and each step is a
 * fixed layout, so the height changes once, between them.
 */
@Composable
fun ReportFlowScreen(
    route: AppRoute.Messaging.Report,
    resultStateRegistry: NavResultStateRegistry,
) {
    val navigator = LocalCodeNavigator.current
    val keyboard = rememberKeyboardController()

    FlowHost<ReportStep, Parcelable>(
        initialStack = route.rememberInitialStack(),
        resultStateRegistry = resultStateRegistry,
        // The details step owns the keyboard, so put it away before the chat behind this is
        // uncovered — the same rule the chat and new-group flows follow.
        onExit = { _, _ -> keyboard.hideIfVisible { navigator.pop() } },
        entryProvider = reportEntryProvider(route.subject),
        sceneStrategies = listOf(
            ModalBottomSheetSceneStrategy(navigator.resultStore) { null },
            SinglePaneSceneStrategy(),
        ),
    )
}

@Composable
private fun reportEntryProvider(
    subject: ReportSubject,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<ReportStep.ReasonSelection> { FlowReasonSelection(subject) }
    annotatedEntry<ReportStep.Details> { step -> FlowReportDetails(subject, step.reason) }
}

@Composable
private fun FlowReasonSelection(subject: ReportSubject) {
    val viewModel = flowSharedViewModel<ReportViewModel>()
    val flowNavigator = rememberFlowNavigator<ReportStep, Parcelable>()

    ReasonSelectionContent(
        onChoose = { reason ->
            if (reason == ReportReason.Other) {
                flowNavigator.navigateTo(ReportStep.Details(reason))
            } else {
                // Every other reason is the whole report, so the pick is the submit. Asking for
                // more after someone has already said what is wrong would be asking twice.
                viewModel.submit(subject, reason, details = null)
                flowNavigator.exitCanceled()
            }
        },
        // `back` on its own: at the flow's root it exits the flow itself, so pairing it with an
        // `exitCanceled()` for the false return would leave twice and pop the chat with us.
        onNavigateUp = { flowNavigator.back() },
    )
}

@Composable
private fun FlowReportDetails(subject: ReportSubject, reason: ReportReason) {
    val viewModel = flowSharedViewModel<ReportViewModel>()
    val flowNavigator = rememberFlowNavigator<ReportStep, Parcelable>()
    // Remembered against the step, so going back to the reasons and returning keeps what was
    // typed — leaving by the back arrow is usually a second thought about the reason, not about
    // the words.
    val details = rememberTextFieldState()

    ReportDetailsContent(
        state = details,
        onSubmit = { typed ->
            viewModel.submit(subject, reason, typed)
            flowNavigator.exitCanceled()
        },
        // Not the flow's root, so this steps back to the reasons rather than leaving.
        onNavigateUp = { flowNavigator.back() },
    )
}
