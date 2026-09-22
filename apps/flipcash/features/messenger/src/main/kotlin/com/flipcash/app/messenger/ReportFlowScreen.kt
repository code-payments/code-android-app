package com.flipcash.app.messenger

import android.os.Parcelable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.getcode.navigation.scenes.LocalSheetNavigator
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.FlowNavigator
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
    // Read before FlowHost overrides the locals below: non-null only because a sheet scene is what
    // provides it, which makes its presence the answer to "is this flow being shown in a sheet".
    val sheetNavigator = LocalSheetNavigator.current
    val keyboard = rememberKeyboardController()

    FlowHost<ReportStep, Parcelable>(
        initialStack = route.rememberInitialStack(),
        resultStateRegistry = resultStateRegistry,
        // The details step owns the keyboard, so put it away before the chat behind this is
        // uncovered — the same rule the chat and new-group flows follow.
        //
        // Popping outright would take the sheet off in one frame. Handing the sheet navigator a
        // dismiss instead lets the scene animate it down and pop the entry itself once that
        // finishes, so this must not also pop — that pair of pops is what used to land us back on
        // the chat list.
        //
        // FlowHost has an animated exit of its own but will not use it here: it gates on
        // `isSheetRoot`, which is `backStack.size <= 1`, and report opens over a chat. A sheet is
        // no less a sheet for having something underneath it, so that gate is wrong for every
        // flow opened this way — fixing it there is a wider change than this one.
        onExit = { _, _ ->
            keyboard.hideIfVisible {
                if (sheetNavigator != null) {
                    // Empty lambda: animate out, nothing to do afterwards.
                    sheetNavigator.pendingSheetDismiss = {}
                } else {
                    navigator.pop()
                }
            }
        },
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
    val progress by viewModel.state.collectAsStateWithLifecycle()

    ExitOnceConfirmed(viewModel, flowNavigator)

    ReasonSelectionContent(
        progress = progress,
        onChoose = { reason ->
            if (reason == ReportReason.Other) {
                flowNavigator.navigateTo(ReportStep.Details(reason))
            } else {
                // Every other reason is the whole report, so the pick is the submit. Asking for
                // more after someone has already said what is wrong would be asking twice.
                viewModel.submit(subject, reason, details = null)
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
    val progress by viewModel.state.collectAsStateWithLifecycle()

    ExitOnceConfirmed(viewModel, flowNavigator)

    ReportDetailsContent(
        state = details,
        progress = progress,
        onSubmit = { typed -> viewModel.submit(subject, reason, typed) },
        // Not the flow's root, so this steps back to the reasons rather than leaving.
        onNavigateUp = { flowNavigator.back() },
    )
}

/**
 * Closes the flow once the report has been acknowledged.
 *
 * What closes the flow is the confirmation being dismissed, not the submit being called. Reporting
 * someone is not a throwaway action, and the flow used to vanish the instant the button was
 * pressed, which left the confirmation to land on whatever happened to be behind it and said
 * nothing at all when the send had failed.
 *
 * Collected per step rather than once around the host, because [FlowNavigator] is only in scope
 * inside one — and the step that submitted is the one still composed while its confirmation is up.
 */
@Composable
private fun ExitOnceConfirmed(
    viewModel: ReportViewModel,
    flowNavigator: FlowNavigator<ReportStep, Parcelable>,
) {
    LaunchedEffect(viewModel) {
        viewModel.confirmed.collect { flowNavigator.exitCanceled() }
    }
}
