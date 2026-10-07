package com.flipcash.app.messenger

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.chat.GroupInviteResult
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.GroupInviteViewModel
import com.flipcash.app.messenger.internal.screens.GroupInviteSheet
import com.flipcash.app.messenger.internal.screens.ShareToChatsSubject
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.flipcash.app.messenger.internal.screens.EmojiPickerViewModel
import com.flipcash.app.messenger.internal.screens.ReactionPickerSheet
import com.flipcash.app.messenger.internal.screens.ReactorsSheet
import com.flipcash.app.messenger.internal.screens.ReactorsViewModel
import com.flipcash.app.messenger.internal.screens.cash.ChatAmountEntryContent
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileScreen
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileViewModel
import com.flipcash.app.messenger.internal.screens.profile.GroupProfileScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupNameScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupPictureScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupScreen
import com.getcode.navigation.annotatedEntry
import com.flipcash.services.models.chat.ChatId
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
        // ChatStep.AmountEntry and ChatStep.InviteToGroup are Sheets, so the
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
    annotatedEntry<ChatStep.InviteToGroup> {
        FlowGroupInviteSheet()
    }

    annotatedEntry<ChatStep.Profile> { step ->
        FlowChatProfileScreen(step.contact, step.origin)
    }
    annotatedEntry<ChatStep.GroupProfile> {
        FlowGroupProfileScreen()
    }
    annotatedEntry<ChatStep.EditGroup> {
        EditGroupScreen()
    }
    annotatedEntry<ChatStep.EditGroupName> {
        FlowEditGroupNameScreen()
    }
    annotatedEntry<ChatStep.EditGroupPicture> {
        FlowEditGroupPictureScreen()
    }

    annotatedEntry<ChatStep.ReactionPicker> { step ->
        FlowReactionPickerScreen(step.messageId)
    }
    annotatedEntry<ChatStep.Reactors> { step ->
        FlowReactorsScreen(step.chatId, step.messageId)
    }
}

@Composable
private fun FlowConversationScreen(
    identifier: ChatIdentifier,
    openKeyboard: Boolean,
) {
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
    val inviteViewModel = hiltViewModel<GroupInviteViewModel>()
    val inviteState by inviteViewModel.state.collectAsStateWithLifecycle()
    // Same dismissal rule as the other sheets in this flow: exit through the sheet so it animates
    // down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current

    LaunchedEffect(inviteViewModel, state.chatId) {
        state.chatId?.let(inviteViewModel::exclude)
    }

    // Returned to whichever screen opened the sheet (see openGroupInvite), which navigates. The
    // keyboard goes first: the message field has it up, and leaving with it still open drags the
    // screen behind out from under it.
    val keyboard = rememberKeyboardController()
    val resultBack = resultBackNavigator<GroupInviteResult>(exit = dismissSheet)
    LaunchedEffect(inviteViewModel) {
        inviteViewModel.invited.collect { chatId ->
            keyboard.hideIfVisible { resultBack.returnValue(GroupInviteResult(chatId)) }
        }
    }

    GroupInviteSheet(
        inviteUrl = state.groupInviteUrl,
        subject = ShareToChatsSubject.Group(state.subject as? ChatSubject.Group),
        state = inviteState,
        onShare = { viewModel.dispatchEvent(ChatViewModel.Event.InviteLinkShared) },
        onCopy = { viewModel.dispatchEvent(ChatViewModel.Event.CopyInviteLink) },
        onToggle = inviteViewModel::toggle,
        onMessageChanged = inviteViewModel::onMessageChanged,
        onInvite = { state.groupInviteUrl?.let(inviteViewModel::invite) },
        onDismiss = dismissSheet,
    )
}

@Composable
private fun FlowChatProfileScreen(participant: ChatParticipant, origin: ProfileOrigin) {
    val viewModel = flowSharedViewModel<ChatProfileViewModel>()
    val chatViewModel = flowSharedViewModel<ChatViewModel>()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val rootNavigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel, participant) {
        viewModel.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant))
    }

    ChatProfileScreen(
        viewModel = viewModel,
        onOpenChat = { chatId ->
            val chatState = chatViewModel.stateFlow.value
            if (origin == ProfileOrigin.Chat && chatState.chatId == chatId) {
                // The chat is the one underneath: back to it rather than a second copy.
                flowNavigator.back()
            } else {
                rootNavigator.push(AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId)))
            }
        },
    )

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

// Both edit steps read the chat they are editing off the flow's shared ChatViewModel, the same way
// the profile above them does, and keep the edit itself in their own nav-entry-scoped view model.
// EditGroupScreen needs neither and is registered directly.
@Composable
private fun FlowEditGroupNameScreen() {
    EditGroupNameScreen(flowSharedViewModel<ChatViewModel>())
}

@Composable
private fun FlowEditGroupPictureScreen() {
    EditGroupPictureScreen(flowSharedViewModel<ChatViewModel>())
}

/**
 * The full emoji picker for [messageId] — a tap on any emoji toggles it and dismisses. The search
 * lives in the entry's own [EmojiPickerViewModel]; only the toggle goes to the chat.
 */
@Composable
private fun FlowReactionPickerScreen(messageId: Long) {
    val chatViewModel = flowSharedViewModel<ChatViewModel>()
    val viewModel = hiltViewModel<EmojiPickerViewModel>()
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    ReactionPickerSheet(
        searchState = state.searchFieldState,
        sections = state.sections,
        tones = state.tones,
        loaded = state.loaded,
        onSelected = { emoji ->
            chatViewModel.dispatchEvent(
                ChatViewModel.Event.ToggleReaction(messageId, emoji, clearsSelection = true)
            )
            dismissSheet()
        },
        onDismiss = dismissSheet,
    )
}

/**
 * Who reacted to [messageId], and with what, from the entry's own [ReactorsViewModel]. Tapping a
 * named row dismisses this sheet, then opens that person's profile on the chat.
 */
@Composable
private fun FlowReactorsScreen(chatId: ChatId, messageId: Long) {
    val viewModel = hiltViewModel<ReactorsViewModel>()
    val navigator = LocalCodeNavigator.current
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, chatId, messageId) {
        viewModel.dispatchEvent(ReactorsViewModel.Event.Open(chatId, messageId))
    }

    ReactorsSheet(
        pills = state.pills,
        rows = state.rows,
        loading = state.loading,
        hasMore = state.hasMore,
        onLoadMore = { viewModel.dispatchEvent(ReactorsViewModel.Event.LoadMore) },
        onOpenProfile = { userId ->
            val profile = state.rows.firstOrNull { it.userId == userId }?.display?.profile
            dismissSheet()
            if (profile != null) navigator.push(ChatStep.Profile(contact = ChatParticipant.TipUser(userId, profile)))
        },
        onDismiss = dismissSheet,
    )
}
