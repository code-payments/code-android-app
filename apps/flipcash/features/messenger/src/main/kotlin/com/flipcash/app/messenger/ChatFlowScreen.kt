package com.flipcash.app.messenger

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatSendResult
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.GroupInviteSheet
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.flipcash.app.messenger.internal.screens.MuteChatSheet
import com.flipcash.app.messenger.internal.screens.ReportSheet
import com.flipcash.app.messenger.internal.screens.cash.ChatAmountEntryContent
import com.flipcash.app.messenger.internal.screens.cash.ChatInitPaymentSheet
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileScreen
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileViewModel
import com.flipcash.app.messenger.internal.screens.profile.GroupProfileScreen
import com.flipcash.shared.chat.ui.rememberIsMuted
import com.getcode.navigation.annotatedEntry
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.FlowHost
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.navigation.flow.rememberInitialStack
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.NavResultStateRegistry
import com.getcode.navigation.results.navigateForResult
import com.getcode.navigation.results.resultBackNavigator
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.navigation.scenes.LocalSheetNavigator
import com.getcode.navigation.scenes.ModalBottomSheetSceneStrategy
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
fun ChatFlowScreen(
    route: AppRoute.Messaging.Chat,
    resultStateRegistry: NavResultStateRegistry,
) {
    val navigator = LocalCodeNavigator.current
    val keyboard = rememberKeyboardController()

    FlowHost<ChatStep, Parcelable>(
        initialStack = route.rememberInitialStack(),
        resultStateRegistry = resultStateRegistry,
        // Put the keyboard away before the chat leaves. Every way out of the conversation lands
        // here — the top bar's up control pops the inner navigator, which at the flow root reaches
        // onRootReached, and so does system back — so this is the one place that has to do it.
        // Popping with the IME still up drags the screen behind it out from under the keyboard.
        onExit = { _, _ -> keyboard.hideIfVisible { navigator.pop() } },
        entryProvider = chatEntryProvider(route.identifier, route.openKeyboard),
        // ChatStep.AmountEntry, ChatStep.InitPayment, ChatStep.InviteToGroup, ChatStep.MuteChat
        // and ChatStep.Report are Sheets, so the
        // flow needs the sheet strategy to draw them as such; without it the step would fall
        // through to SinglePane and cover the thread. Amount entry
        // returns its result inside the flow (resultBackNavigator), so the strategy's own
        // dismiss-delivers-Canceled path has nothing to address here — hence the null key. A
        // swipe-dismiss just leaves the pending callback unclaimed, which is what a cancel means.
        sceneStrategies = listOf(
            ModalBottomSheetSceneStrategy(navigator.resultStore) { null },
            SinglePaneSceneStrategy(),
        ),
    )
}

@Composable
private fun chatEntryProvider(
    identifier: ChatIdentifier,
    openKeyboard: Boolean,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<ChatStep.Conversation> {
        FlowConversationScreen(identifier, openKeyboard)
    }
    annotatedEntry<ChatStep.AmountEntry> {
        FlowAmountEntryScreen()
    }
    annotatedEntry<ChatStep.InitPayment> {
        FlowInitPaymentScreen()
    }
    annotatedEntry<ChatStep.InviteToGroup> {
        FlowGroupInviteSheet()
    }
    annotatedEntry<ChatStep.MuteChat> {
        FlowMuteChatSheet()
    }

    annotatedEntry<ChatStep.Report> { step ->
        FlowReportSheet(step.subject)
    }
    annotatedEntry<ChatStep.Profile> { step ->
        FlowChatProfileScreen(step.contact)
    }
    annotatedEntry<ChatStep.GroupProfile> {
        FlowGroupProfileScreen()
    }
}

@Composable
private fun FlowConversationScreen(identifier: ChatIdentifier, openKeyboard: Boolean) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val navigator = LocalCodeNavigator.current
    // The sheet-owning (root) navigator — the one whose back stack holds this chat's Main.Sheet and
    // whose pendingSheetDismiss the dismiss animation observes. openAsSheet is stack-local (it
    // inspects/mutates this navigator's own back stack), so it can't ride the type dispatcher and
    // must target the sheet navigator explicitly.
    val sheetNavigator = LocalSheetNavigator.current
    val keyboard = rememberKeyboardController()

    LaunchedEffect(viewModel, identifier) {
        viewModel.dispatchEvent(ChatViewModel.Event.OnChatOpened(identifier))
    }

    var hasOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            if (!hasOpened) {
                viewModel.dispatchEvent(ChatViewModel.Event.OnStartMessageInput)
                hasOpened = true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToAmountEntry>()
            .collect {
                // ChatStep.AmountEntry is a FlowStep -> the dispatcher keeps this push on the inner
                // flow stack and registers the result callback on the inner store (intra-flow).
                navigator.navigateForResult<ChatSendResult>(ChatStep.AmountEntry) { result ->
                    if (result is NavResultOrCanceled.ReturnValue) {
                        viewModel.dispatchEvent(ChatViewModel.Event.OnStartMessageInput)
                    }
                }
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToInitPayment>()
            .collect {
                navigator.navigateForResult<ChatSendResult>(ChatStep.InitPayment) { result ->
                    if (result is NavResultOrCanceled.ReturnValue) {
                        viewModel.dispatchEvent(ChatViewModel.Event.OnStartMessageInput)
                    }
                }
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.OpenScreen>()
            .collect { (route, asSheet) ->
                keyboard.hideIfVisible {
                    if (asSheet) {
                        // Dismiss this chat sheet and open [route] as a fresh sheet. openAsSheet on the
                        // sheet-owning navigator animates the current sheet closed (via pendingSheetDismiss)
                        // before opening the new one.
                        sheetNavigator?.openAsSheet(route)
                    } else {
                        // A full AppRoute (never a ChatStep) -> the dispatcher bubbles it up the parent
                        // chain to the outer app nav host, the same destination as the old
                        // outerNavigator.navigate(route).
                        navigator.navigate(route)
                    }
                }
            }
    }

    // The selection bar reaches the report sheet from here rather than navigating itself: the bar
    // is rebuilt from the selection and the sheet has to outlive clearing it. The two profile rows
    // call `navigateTo` directly because they are already inside the flow and have nothing to
    // outlive.
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.OpenReportSheet>()
            .collect { (subject) ->
                keyboard.hideIfVisible {
                    flowNavigator.navigateTo(ChatStep.Report(subject))
                }
            }
    }

    MessengerScreen(viewModel)
}

@Composable
private fun FlowAmountEntryScreen() {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    // Every way out of this step goes through the sheet's own dismissal, which animates it down to
    // Hidden and pops the entry once it settles. Popping the entry directly — navigateBack, or the
    // pop ResultBackNavigator does by default — deletes the scene mid-frame, so the sheet vanishes
    // instead of closing.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<ChatSendResult>(exit = dismissSheet)

    // No re-shadow: ChatAmountEntryContent reads the inner LocalCodeNavigator, and its
    // navigator.push(AppRoute.Main.RegionSelection) / push(AppRoute.Sheets.TokenSelection)
    // bubble to the app navigator through the dispatcher.
    ChatAmountEntryContent(
        amountDelegate = viewModel.amountDelegate,
        resolveState = state.resolveState,
        token = state.token,
        eventFlow = viewModel.eventFlow,
        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnConfirmRequested) },
        onSendComplete = { resultBack.returnValue(ChatSendResult) }, // intra-flow result -> Conversation
        onExit = dismissSheet,
    )
}

@Composable
private fun FlowInitPaymentScreen() {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    // Same dismissal rule as amount entry: exit through the sheet so it animates down rather than
    // having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<ChatSendResult>(exit = dismissSheet)

    ChatInitPaymentSheet(
        fee = state.chatInitFee,
        token = state.token,
        sendProgress = state.sendProgress,
        eventFlow = viewModel.eventFlow,
        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnInitPaymentConfirmed) },
        onSendComplete = { resultBack.returnValue(ChatSendResult) },
    )
}

/**
 * The invite sheet, on the conversation's own view model.
 *
 * Reached from the empty transcript and from the group's profile, and the same step either way —
 * the link is the chat's, so there is nothing for a second entry point to hold differently.
 */
@Composable
private fun FlowGroupInviteSheet() {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    // Same dismissal rule as the other sheets in this flow: exit through the sheet so it animates
    // down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    GroupInviteSheet(
        inviteUrl = state.groupInviteUrl,
        group = state.subject as? ChatSubject.Group,
        onCopy = { viewModel.dispatchEvent(ChatViewModel.Event.CopyInviteLink) },
        onDismiss = dismissSheet,
    )
}

@Composable
private fun FlowMuteChatSheet() {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    // Same dismissal rule as the other sheets in this flow: exit through the sheet so it animates
    // down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    MuteChatSheet(
        // Asked per composition rather than passed in as a boolean the caller computed: a timed
        // mute lapses with nothing sent to say so, and the sheet's unmute row is what would
        // otherwise be left standing over a chat that is already audible again.
        isMuted = rememberIsMuted(state.viewerState),
        // Dismissed on the tap rather than on the result: the request is fire-and-forget from here,
        // and a failure is reported by the view model's own error bar, which draws over whatever is
        // on screen by then.
        onMute = {
            viewModel.dispatchEvent(ChatViewModel.Event.MuteChat(it))
            dismissSheet()
        },
        onUnmute = {
            viewModel.dispatchEvent(ChatViewModel.Event.UnmuteChat)
            dismissSheet()
        },
        onDismiss = dismissSheet,
    )
}

@Composable
private fun FlowReportSheet(subject: ReportSubject) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    // Same dismissal rule as the other sheets in this flow: exit through the sheet so it animates
    // down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    ReportSheet(
        // Dismissed on submit rather than on the result: the request is fire-and-forget from the
        // sheet's point of view, and the outcome arrives as a bottom bar over whatever is
        // underneath. Holding the sheet open for a round trip would make a two-tap action feel
        // like a form.
        onSubmit = { reason, details ->
            viewModel.dispatchEvent(
                ChatViewModel.Event.ReportSubmitted(subject, reason, details)
            )
            dismissSheet()
        },
        onDismiss = dismissSheet,
    )
}

@Composable
private fun FlowChatProfileScreen(participant: ChatParticipant) {
    val viewModel = flowSharedViewModel<ChatProfileViewModel>()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()

    LaunchedEffect(viewModel, participant) {
        viewModel.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant))
    }

    ChatProfileScreen(viewModel, flowSharedViewModel<ChatViewModel>())

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatProfileViewModel.Event.BlockSuccessful>()
            .onEach {
                // Blocking removes the DM, so exit the whole chat flow (FlowHost.onExit pops the
                // Chat route) and land back on the Tips list the chat was opened from.
                flowNavigator.exitCanceled()
            }.launchIn(this)
    }
}

/**
 * The group's profile, on the conversation's own view model.
 *
 * No [LaunchedEffect] seeding it the way [FlowChatProfileScreen] seeds a participant: the shared
 * view model is already open on this group, so there is nothing to hand it.
 */
@Composable
private fun FlowGroupProfileScreen() {
    GroupProfileScreen(flowSharedViewModel<ChatViewModel>())
}
