package com.flipcash.app.messenger

import com.flipcash.app.messenger.internal.swipepay.SwipePayUiState
import com.getcode.ui.components.toast.LocalFloatingToastHost
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatSendResult
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.GroupInviteResult
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.chat.ReactionBoostAmountResult
import com.flipcash.app.core.extensions.openAsSheet
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.services.models.chat.ChatType
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.GroupInviteViewModel
import com.flipcash.app.core.chat.InviteLinkAction
import com.flipcash.app.core.chat.NewInviteLinkChoice
import com.flipcash.app.core.chat.PinnedMessageJump
import com.flipcash.app.messenger.internal.pins.PinnedRow
import com.flipcash.app.messenger.internal.pins.pinnedListDone
import com.flipcash.app.messenger.internal.screens.components.PinnedMessagesSheet
import com.flipcash.app.messenger.internal.invites.InviteLinkActions
import com.flipcash.app.messenger.internal.invites.NewInviteLinkContent
import com.flipcash.app.messenger.internal.invites.toChoice
import com.flipcash.app.messenger.internal.invites.InviteLinkActionsViewModel
import com.flipcash.app.messenger.internal.invites.InviteLinkViewModel
import com.flipcash.app.messenger.internal.invites.ManageInvitesScreen
import com.flipcash.app.messenger.internal.screens.EmojiPickerViewModel
import com.flipcash.app.messenger.internal.screens.GroupInviteSheet
import com.flipcash.app.messenger.internal.screens.MessengerScreen
import com.flipcash.app.messenger.internal.screens.ReactionPickerSheet
import com.flipcash.app.messenger.internal.screens.ReactorsSheet
import com.flipcash.app.messenger.internal.screens.ReactorsViewModel
import com.flipcash.app.messenger.internal.screens.ShareToChatsSubject
import com.flipcash.app.messenger.internal.screens.openGroupInvite
import com.flipcash.app.messenger.internal.request.ChatRequestsViewModel
import com.flipcash.app.messenger.internal.request.RequestDetailsScreen
import com.flipcash.app.messenger.internal.request.RequestMoneyScreen
import com.flipcash.app.messenger.internal.screens.boost.ReactionBoostAmountViewModel
import com.flipcash.app.messenger.internal.screens.boost.ReactionBoostCurrencySheet
import com.flipcash.app.messenger.internal.screens.boost.ReactionBoostSheet
import com.flipcash.app.messenger.internal.screens.boost.ReactionBoostViewModel
import com.getcode.theme.CodeTheme
import com.flipcash.app.messenger.internal.groupsend.GroupSendState
import com.flipcash.app.messenger.internal.groupsend.GroupSendStep
import com.flipcash.app.messenger.internal.screens.cash.ChatAmountEntryContent
import com.flipcash.app.messenger.internal.screens.cash.GroupSelectPeopleContent
import com.flipcash.app.messenger.internal.screens.cash.GroupSendDetailsSheet
import com.flipcash.app.messenger.internal.screens.cash.GroupSendConfirmSheet
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileScreen
import com.flipcash.app.messenger.internal.screens.profile.ChatProfileViewModel
import com.flipcash.app.messenger.internal.screens.profile.ProfileGroup
import com.flipcash.app.messenger.internal.screens.profile.GroupProfileScreen
import com.flipcash.app.messenger.internal.screens.profile.JoinRequestsScreen
import com.flipcash.app.messenger.internal.screens.profile.RestrictedMembersScreen
import com.flipcash.app.messenger.internal.RestrictedKind
import com.flipcash.app.messenger.internal.screens.profile.MembersScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupCoverScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupDescriptionScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupBalanceRequirementScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupNameScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupPictureScreen
import com.flipcash.app.messenger.internal.screens.profile.edit.EditGroupScreen
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.amountentry.AmountEntryScreen
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.annotatedEntry
import com.flipcash.services.models.chat.GroupBalanceRole
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
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.LocalSheetOverhang
import com.getcode.ui.utils.rememberKeyboardController
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.getcode.utils.base58

@Composable
fun ChatFlowScreen(
    route: AppRoute.Messaging.Chat,
    resultStateRegistry: NavResultStateRegistry,
) {
    val navigator = LocalCodeNavigator.current
    val keyboard = rememberKeyboardController()
    // Created here, before any step asks for it, so it opens the chat while being created and the
    // first frame already has what the open can draw from memory (a group's name and picture). The
    // steps get this instance from flowSharedViewModel: the flow's store owner is this entry's.
    val viewModel = hiltViewModel<ChatViewModel, ChatViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(
                route.identifier,
                route.jumpToMessageId,
                route.returnToTitle,
                linkedAsGroup = route.jumpToMessageId != null && route.isGroupLink,
            )
        },
    )

    // A group link the server has no record of: say so and leave, rather than sit on an empty chat
    // with a composer. Other opens ignore it (see AppRoute.Messaging.Chat.isGroupLink).
    if (route.isGroupLink) {
        val resources = LocalResources.current
        LaunchedEffect(viewModel) {
            viewModel.stateFlow.first { it.chatNotFound }
            BottomBarManager.showError(
                title = resources.getString(R.string.error_title_groupNotFound),
                message = resources.getString(R.string.error_description_groupNotFound),
            )
            keyboard.hideIfVisible { navigator.pop() }
        }
    }

    FlowHost<ChatStep, Parcelable>(
        initialStack = route.rememberInitialStack(),
        resultStateRegistry = resultStateRegistry,
        // Put the keyboard away before the chat leaves. Every way out of the conversation lands
        // here — the top bar's up control pops the inner navigator, which at the flow root reaches
        // onRootReached, and so does system back — so this is the one place that has to do it.
        // Popping with the IME still up drags the screen behind it out from under the keyboard.
        onExit = { _, _ -> keyboard.hideIfVisible { navigator.pop() } },
        entryProvider = chatEntryProvider(route.openKeyboard, resultStateRegistry),
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
    openKeyboard: Boolean,
    resultStateRegistry: NavResultStateRegistry,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    annotatedEntry<ChatStep.Conversation> {
        FlowConversationScreen(openKeyboard)
    }
    annotatedEntry<ChatStep.AmountEntry> {
        FlowAmountEntryScreen(resultStateRegistry, toMembers = false)
    }
    annotatedEntry<ChatStep.GroupSendAmountEntry> {
        FlowAmountEntryScreen(resultStateRegistry, toMembers = true)
    }
    annotatedEntry<ChatStep.InviteToGroup> {
        FlowGroupInviteSheet()
    }
    annotatedEntry<ChatStep.ShareInviteLink> { step ->
        FlowInviteLinkSheet(step.inviteIdHex)
    }
    annotatedEntry<ChatStep.NewInviteLink> {
        FlowNewInviteLinkSheet()
    }
    annotatedEntry<ChatStep.InviteLinkActions> { step ->
        FlowInviteLinkActionsSheet(step.inviteIdHex)
    }
    annotatedEntry<ChatStep.ManageInvites> {
        ManageInvitesScreen(flowSharedViewModel<ChatViewModel>().stateFlow.collectAsStateWithLifecycle().value.chatId)
    }
    annotatedEntry<ChatStep.ShareMessage> { step ->
        FlowShareMessageSheet(step.messageId)
    }

    annotatedEntry<ChatStep.Profile> { step ->
        FlowChatProfileScreen(step.contact, step.origin)
    }
    annotatedEntry<ChatStep.GroupProfile> {
        FlowGroupProfileScreen()
    }
    annotatedEntry<ChatStep.EditGroup> {
        FlowEditGroupScreen()
    }
    annotatedEntry<ChatStep.JoinRequests> {
        JoinRequestsScreen(flowSharedViewModel<ChatViewModel>())
    }
    annotatedEntry<ChatStep.SilencedMembers> {
        RestrictedMembersScreen(RestrictedKind.Silenced, flowSharedViewModel<ChatViewModel>())
    }
    annotatedEntry<ChatStep.RemovedMembers> {
        RestrictedMembersScreen(RestrictedKind.Removed, flowSharedViewModel<ChatViewModel>())
    }
    annotatedEntry<ChatStep.Members> {
        MembersScreen(flowSharedViewModel<ChatViewModel>())
    }
    annotatedEntry<ChatStep.EditGroupName> {
        FlowEditGroupNameScreen()
    }
    annotatedEntry<ChatStep.EditGroupPicture> {
        FlowEditGroupPictureScreen()
    }
    annotatedEntry<ChatStep.EditGroupCover> {
        FlowEditGroupCoverScreen()
    }
    annotatedEntry<ChatStep.EditGroupDescription> {
        FlowEditGroupDescriptionScreen()
    }

    annotatedEntry<ChatStep.GroupSendDetails> { step ->
        val viewModel = flowSharedViewModel<ChatViewModel>()
        val dismissSheet = LocalBottomSheetDismissDispatcher.current
        val navigator = LocalCodeNavigator.current
        // Closed by its X only, as Request details is: no flow host to carry NonDraggableRoute.
        val sheetNavigator = LocalSheetNavigator.current
        DisposableEffect(sheetNavigator) {
            val previous = sheetNavigator?.sheetDragDisabled ?: false
            sheetNavigator?.sheetDragDisabled = true
            onDispose { sheetNavigator?.sheetDragDisabled = previous }
        }
        GroupSendDetailsSheet(
            tokenName = step.tokenName,
            tokenImageUrl = step.tokenImageUrl,
            sender = viewModel.groupSendPerson(step.senderIdHex),
            recipients = step.recipientIdsHex.map(viewModel::groupSendPerson),
            onOpenProfile = { participant ->
                // The sheet pops whatever is on top once its hide animation ends, so a push made
                // alongside dismissSheet() lands first and is the entry that gets popped. Push from
                // the scene's after-dismiss hook instead, once the sheet itself is gone.
                navigator.pendingSheetDismiss = {
                    navigator.push(ChatStep.Profile(contact = participant, origin = ProfileOrigin.GroupMember))
                }
            },
            onOpenToken = {
                navigator.pendingSheetDismiss = {
                    navigator.push(AppRoute.Token.Info(step.mint, TokenInfoEntry.Chat))
                }
            },
            onClose = { dismissSheet() },
        )
    }

    annotatedEntry<ChatStep.RequestMoney> { step ->
        val navigator = LocalCodeNavigator.current
        val dismissSheet = LocalBottomSheetDismissDispatcher.current
        RequestMoneyScreen(
            chatId = step.chatId,
            resultStateRegistry = resultStateRegistry,
            onClose = { dismissSheet() },
            onOpenScreen = { route -> navigator.navigate(route) },
        )
    }

    annotatedEntry<ChatStep.RequestDetails> { step ->
        val navigator = LocalCodeNavigator.current
        val dismissSheet = LocalBottomSheetDismissDispatcher.current
        // No inner flow host to carry a NonDraggableRoute up to the sheet, so turn the drag off
        // here, as FlowHost would: the app bar's close is the only way out. The flag is the chat
        // flow's, shared with a Request Money sheet underneath, so put back what was there.
        val sheetNavigator = LocalSheetNavigator.current
        DisposableEffect(sheetNavigator) {
            val previous = sheetNavigator?.sheetDragDisabled ?: false
            sheetNavigator?.sheetDragDisabled = true
            onDispose { sheetNavigator?.sheetDragDisabled = previous }
        }
        // Opened from the expanded deck, which stays as it was while the sheet rises over it. Once
        // the sheet is fully up, snap the deck minimized where nobody sees it, so coming back shows
        // the chat with the deck in the app bar and no collapse playing as the sheet leaves.
        val requests = flowSharedViewModel<ChatRequestsViewModel>()
        val overhang = LocalSheetOverhang.current
        LaunchedEffect(requests) {
            snapshotFlow { overhang() }.first { it > 1f }
            snapshotFlow { overhang() }.first { it <= 1f }
            requests.setExpanded(false)
        }
        RequestDetailsScreen(
            chatId = step.chatId,
            requestId = step.requestId,
            onBack = { dismissSheet() },
            onOpenScreen = { route -> navigator.navigate(route) },
        )
    }

    annotatedEntry<ChatStep.EditGroupBalanceRequirement> { step ->
        FlowEditGroupBalanceRequirementScreen(step.role)
    }

    annotatedEntry<ChatStep.ReactionPicker> { step ->
        FlowReactionPickerScreen(step.messageId)
    }
    annotatedEntry<ChatStep.ReactionBoost> { step ->
        FlowReactionBoostScreen(step)
    }
    annotatedEntry<ChatStep.ReactionBoostCurrency> {
        ReactionBoostCurrencySheet()
    }
    annotatedEntry<ChatStep.SendCurrency> {
        ReactionBoostCurrencySheet()
    }
    annotatedEntry<ChatStep.ReactionBoostAmount> { step ->
        FlowReactionBoostAmountScreen(step)
    }
    annotatedEntry<ChatStep.Reactors> { step ->
        FlowReactorsScreen(step.chatId, step.messageId)
    }
    annotatedEntry<ChatStep.PinnedMessages> { step ->
        FlowPinnedMessagesSheet(step.pinAfterUnpin)
    }
}

@Composable
private fun FlowConversationScreen(
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
            .collect { event ->
                // ChatStep.AmountEntry is a FlowStep -> the dispatcher keeps this push on the inner
                // flow stack and registers the result callback on the inner store (intra-flow).
                val step = if (event.toMembers) ChatStep.GroupSendAmountEntry else ChatStep.AmountEntry
                navigator.navigateForResult<ChatSendResult>(step) { result ->
                    if (result is NavResultOrCanceled.ReturnValue) {
                        viewModel.dispatchEvent(ChatViewModel.Event.OnStartMessageInput)
                    }
                }
            }
    }

    // In-app floating toast, like the chat's other notices. Its own scope, so a second lift
    // replaces the first rather than queueing behind it.
    val floatingToasts = LocalFloatingToastHost.current
    val liftedMessage = stringResource(R.string.toast_silenceLifted)
    val toastScope = rememberCoroutineScope()
    LaunchedEffect(viewModel, floatingToasts) {
        val toasts = floatingToasts ?: return@LaunchedEffect
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.SilenceLifted>()
            .collect { toastScope.launch { toasts.show(liftedMessage) } }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.NavigateToRequestMoney>()
            .collect {
                val chatId = viewModel.stateFlow.value.chatId ?: return@collect
                keyboard.hideIfVisible { navigator.push(ChatStep.RequestMoney(chatId)) }
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
private fun FlowAmountEntryScreen(resultStateRegistry: NavResultStateRegistry, toMembers: Boolean) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    // The chat flow's navigator, read before the sheet's own FlowHost below shadows it.
    val chatNavigator = LocalCodeNavigator.current
    // Every way out of this step goes through the sheet's own dismissal, which animates it down to
    // Hidden and pops the entry once it settles. Popping the entry directly — navigateBack, or the
    // pop ResultBackNavigator does by default — deletes the scene mid-frame, so the sheet vanishes
    // instead of closing.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<ChatSendResult>(exit = dismissSheet)

    // A private group's send carries on past the amount to Select People and its confirm sheet,
    // in this same sheet. The sheet has its own nav host so Select People slides in over the
    // amount; the view model's stage decides which step is on top.
    val inPeople = state.groupSend?.let { it.stage != GroupSendState.Stage.Amount } == true

    FlowHost<GroupSendStep, Parcelable>(
        initialStack = listOf(GroupSendStep.Amount),
        resultStateRegistry = resultStateRegistry,
        onExit = { _, _ -> dismissSheet() },
        entryProvider = entryProvider {
            annotatedEntry<GroupSendStep.Amount> {
                val flowNavigator = rememberFlowNavigator<GroupSendStep, Parcelable>()
                LaunchedEffect(inPeople) {
                    if (inPeople) flowNavigator.navigateTo(GroupSendStep.People)
                }
                // ChatAmountEntryContent's push(ChatStep.SendCurrency) belongs to the chat flow,
                // as a half sheet over this one; on this sheet's navigator it would land here.
                // Its push(AppRoute.Main.RegionSelection) bubbles to the app navigator either way.
                CompositionLocalProvider(LocalCodeNavigator provides chatNavigator) {
                    ChatAmountEntryContent(
                        amountDelegate = viewModel.amountDelegate,
                        resolveState = state.resolveState,
                        token = state.token,
                        eventFlow = viewModel.eventFlow,
                        onConfirm = { viewModel.dispatchEvent(ChatViewModel.Event.OnConfirmRequested) },
                        onSendComplete = { resultBack.returnValue(ChatSendResult) }, // intra-flow result -> Conversation
                        onExit = dismissSheet,
                        // A group send picks its currency on the confirm sheet, so no pill here.
                        title = if (toMembers) stringResource(R.string.title_groupSendTotal) else null,
                    )
                }
            }
            annotatedEntry<GroupSendStep.People> {
                val flowNavigator = rememberFlowNavigator<GroupSendStep, Parcelable>()
                LaunchedEffect(inPeople) {
                    if (!inPeople) flowNavigator.back()
                }
                // Back goes through the view model, whose stage change pops this step above;
                // the nav host popping it alone would leave the stage behind.
                BackHandler { viewModel.dispatchEvent(ChatViewModel.Event.OnGroupSendBack) }
                FlowGroupSelectPeople(viewModel) { resultBack.returnValue(ChatSendResult) }
            }
        },
    )
}

@Composable
private fun FlowGroupSelectPeople(viewModel: ChatViewModel, onSendComplete: () -> Unit) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val groupSend = state.groupSend ?: return
    val candidates = state.groupSendCandidates
    GroupSelectPeopleContent(
        candidates = candidates,
        selected = groupSend.selected,
        onBack = { viewModel.dispatchEvent(ChatViewModel.Event.OnGroupSendBack) },
        onToggle = { viewModel.dispatchEvent(ChatViewModel.Event.OnGroupSendMemberToggled(it.userId)) },
        onSend = { viewModel.dispatchEvent(ChatViewModel.Event.OnGroupSendPeopleConfirmed) },
        title = stringResource(R.string.title_splitBetween),
        buttonText = stringResource(R.string.action_next),
    )
    if (groupSend.stage == GroupSendState.Stage.Confirm) {
        GroupSendConfirmSheet(
            groupSend = groupSend,
            recipients = groupSend.selected.mapNotNull { id -> candidates.find { it.userId == id } },
            swipePay = SwipePayUiState(
                token = state.token,
                progress = state.sendProgress,
                tokenPickable = true,
                backOnly = true,
            ),
            actions = remember(viewModel) { viewModel.groupSendSwipeActions },
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.filterIsInstance<ChatViewModel.Event.SendComplete>()
            .collect { onSendComplete() }
    }
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
    val navigator = LocalCodeNavigator.current
    val resultBack = resultBackNavigator<GroupInviteResult>(exit = dismissSheet)
    LaunchedEffect(inviteViewModel) {
        inviteViewModel.invited.collect { chatId ->
            keyboard.hideIfVisible { resultBack.returnValue(GroupInviteResult(chatId)) }
        }
    }

    GroupInviteSheet(
        inviteUrl = state.shareableGroupInviteUrl,
        subject = ShareToChatsSubject.Group(state.subject as? ChatSubject.Group),
        state = inviteState,
        onShare = { viewModel.dispatchEvent(ChatViewModel.Event.InviteLinkShared) },
        onCopy = { viewModel.dispatchEvent(ChatViewModel.Event.CopyInviteLink) },
        onToggle = inviteViewModel::toggle,
        onMessageChanged = inviteViewModel::onMessageChanged,
        onInvite = { state.shareableGroupInviteUrl?.let(inviteViewModel::invite) },
        onDismiss = dismissSheet,
        // The card is drawn over this sheet, which stays open beneath it.
        onShowCard = (state.subject as? ChatSubject.Group)?.let { group ->
            {
                keyboard.hideIfVisible {
                    navigator.push(
                        AppRoute.Menu.GroupCard(
                            chatId = group.chatId,
                            title = group.groupTitle,
                            picture = group.picture,
                            memberCount = group.memberCount,
                        )
                    )
                }
            }
        },
    )
}

/**
 * [ChatStep.ShareInviteLink]: the invite sheet's invite variant (frames 1b, 1f). The tap on Share Invite
 * Link that opened it has the link made when the host had none; sending goes through the same view model as the
 * join variant, limited to chats an invite link may be sent into.
 */
@Composable
private fun FlowInviteLinkSheet(inviteIdHex: String?) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val inviteViewModel = hiltViewModel<GroupInviteViewModel>()
    val inviteState by inviteViewModel.state.collectAsStateWithLifecycle()
    val linkViewModel = hiltViewModel<InviteLinkViewModel>()
    val link by linkViewModel.state.collectAsStateWithLifecycle()
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val keyboard = rememberKeyboardController()
    val navigator = LocalCodeNavigator.current
    val resultBack = resultBackNavigator<GroupInviteResult>(exit = dismissSheet)
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()

    LaunchedEffect(inviteViewModel, state.chatId) {
        state.chatId?.let(inviteViewModel::exclude)
        inviteViewModel.restrictToEncrypted(true)
    }
    LaunchedEffect(linkViewModel, state.chatId, inviteIdHex) {
        state.chatId?.let { linkViewModel.open(it, inviteIdHex) }
    }
    LaunchedEffect(inviteViewModel) {
        inviteViewModel.invited.collect { chatId ->
            keyboard.hideIfVisible { resultBack.returnValue(GroupInviteResult(chatId)) }
        }
    }

    GroupInviteSheet(
        inviteUrl = null,
        subject = ShareToChatsSubject.GroupInviteLink(state.subject as? ChatSubject.Group, link.link),
        state = inviteState,
        onShare = linkViewModel::onShare,
        onCopy = linkViewModel::onCopy,
        onToggle = inviteViewModel::toggle,
        onMessageChanged = inviteViewModel::onMessageChanged,
        onInvite = linkViewModel::onSend,
        onDismiss = dismissSheet,
        inviteEffects = linkViewModel.effects,
        onInviteEffect = { effect ->
            when (effect) {
                is InviteLinkViewModel.Effect.Send -> inviteViewModel.invite(effect.url)
                // 1e's offer: the plain join link, in place of this sheet.
                InviteLinkViewModel.Effect.OpenJoinLink -> {
                    dismissSheet()
                    navigator.openGroupInvite()
                }
                // 1c's offer: revoke a link there to make room for another.
                InviteLinkViewModel.Effect.OpenManageInvites -> {
                    dismissSheet()
                    flowNavigator.navigateTo(ChatStep.ManageInvites)
                }
                InviteLinkViewModel.Effect.Close -> dismissSheet()
                else -> Unit
            }
        },
    )
}

/**
 * [ChatStep.InviteLinkActions]: frame 2b on the app's sheet scene, like the other choice sheets.
 * It returns the pick and closes; Manage Invites, still beneath it, shares or confirms the revoke.
 * An invite that is gone by the time the sheet draws closes it, as a missing share link does.
 */
@Composable
private fun FlowInviteLinkActionsSheet(inviteIdHex: String) {
    val chatId = flowSharedViewModel<ChatViewModel>().stateFlow.collectAsStateWithLifecycle().value.chatId
    val viewModel = hiltViewModel<InviteLinkActionsViewModel>()
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<InviteLinkAction>(exit = dismissSheet)
    val invite = chatId?.let { viewModel.invite(it, inviteIdHex) }

    LaunchedEffect(invite == null && chatId != null) {
        if (invite == null && chatId != null) dismissSheet()
    }
    invite ?: return

    InviteLinkActions(
        invite = invite,
        onShare = { resultBack.returnValue(InviteLinkAction.Share) },
        onRevoke = { resultBack.returnValue(InviteLinkAction.Revoke) },
        onDismiss = dismissSheet,
    )
}

/**
 * [ChatStep.NewInviteLink]: frame 2d on the app's sheet scene. It returns the chosen settings and
 * closes; Manage Invites, still beneath it, makes the link and shows the creating state.
 */
@Composable
private fun FlowNewInviteLinkSheet() {
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<NewInviteLinkChoice>(exit = dismissSheet)
    NewInviteLinkContent(
        onCreate = { resultBack.returnValue(it.toChoice()) },
        onDismiss = dismissSheet,
    )
}

/**
 * [ChatStep.ShareMessage]: the invite sheet handing out one message's link. Same send path and
 * result as [FlowGroupInviteSheet]; the link is the whole subject.
 */
@Composable
private fun FlowShareMessageSheet(messageId: Long) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val inviteViewModel = hiltViewModel<GroupInviteViewModel>()
    val inviteState by inviteViewModel.state.collectAsStateWithLifecycle()
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val link = remember(state.chatId, messageId) {
        state.chatId?.let { Linkify.message(it, messageId) }
    }

    // The chat the message is in stays in the list: sending a link to a message back into its own
    // chat is how you point at something further up, and the card draws there like anywhere else.

    val keyboard = rememberKeyboardController()
    val resultBack = resultBackNavigator<GroupInviteResult>(exit = dismissSheet)
    LaunchedEffect(inviteViewModel) {
        inviteViewModel.invited.collect { chatId ->
            keyboard.hideIfVisible { resultBack.returnValue(GroupInviteResult(chatId)) }
        }
    }

    GroupInviteSheet(
        inviteUrl = link,
        subject = ShareToChatsSubject.Message,
        state = inviteState,
        onCopy = { viewModel.dispatchEvent(ChatViewModel.Event.CopyMessageLink(messageId)) },
        onToggle = inviteViewModel::toggle,
        onMessageChanged = inviteViewModel::onMessageChanged,
        onInvite = { link?.let(inviteViewModel::invite) },
        onDismiss = dismissSheet,
    )
}

@Composable
private fun FlowChatProfileScreen(participant: ChatParticipant, origin: ProfileOrigin) {
    val viewModel = flowSharedViewModel<ChatProfileViewModel>()
    val chatViewModel = flowSharedViewModel<ChatViewModel>()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val rootNavigator = LocalCodeNavigator.current

    LaunchedEffect(viewModel, participant, origin) {
        viewModel.dispatchEvent(ChatProfileViewModel.Event.OnParticipantSet(participant, origin = origin))
    }

    val chatState by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    // Only a profile opened from the chat itself has that chat underneath; a member's portrait in
    // a group is ProfileOrigin.Chat too, but its DM is not the group.
    val chatUnderneath = chatState.chatId.takeIf { origin == ProfileOrigin.Chat }

    // The profile belongs to a group when the flow's chat is one, whichever way it was opened: the
    // members list says GroupMember and an avatar in the transcript says Chat. The viewer's grants
    // are what show the host's actions, and the creator is left out by profileMenuItems.
    val group = chatState.chatId?.let { chatId ->
        val moderation = chatState.viewerState?.permissions?.moderation
        val subject = chatState.subject as? ChatSubject.Group
        if (chatState.chatType == ChatType.GROUP && subject != null) {
            ProfileGroup(
                chatId = chatId,
                title = subject.title,
                creatorId = subject.creator,
                canSilence = moderation?.canSilence == true,
                canBan = moderation?.canBan == true,
            )
        } else {
            null
        }
    }

    ChatProfileScreen(
        viewModel = viewModel,
        group = group,
        chatUnderneath = chatUnderneath,
        onOpenChat = { chatId ->
            if (chatUnderneath != null && chatUnderneath == chatId) {
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

// The edit steps read the chat they are editing off the flow's shared ChatViewModel, the same way
// the profile above them does, and keep the edit itself in their own nav-entry-scoped view model.
// The list itself holds no edit, but reads the group, its permissions and its rules the same way.
@Composable
private fun FlowEditGroupScreen() {
    EditGroupScreen(flowSharedViewModel<ChatViewModel>())
}

@Composable
private fun FlowEditGroupCoverScreen() {
    EditGroupCoverScreen(flowSharedViewModel<ChatViewModel>())
}

@Composable
private fun FlowEditGroupDescriptionScreen() {
    EditGroupDescriptionScreen(flowSharedViewModel<ChatViewModel>())
}

@Composable
private fun FlowEditGroupNameScreen() {
    EditGroupNameScreen(flowSharedViewModel<ChatViewModel>())
}

@Composable
private fun FlowEditGroupPictureScreen() {
    EditGroupPictureScreen(flowSharedViewModel<ChatViewModel>())
}

@Composable
private fun FlowEditGroupBalanceRequirementScreen(role: GroupBalanceRole) {
    EditGroupBalanceRequirementScreen(flowSharedViewModel<ChatViewModel>(), role)
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
 * [ChatStep.PinnedMessages]: the conversation's pins on the app's sheet scene (L1, L1c, and L1b when
 * [pinAfterUnpin] is set). The pins are the conversation's own, so this reads its view model; a
 * tapped row closes the sheet and returns to the conversation as a [PinnedMessageJump].
 */
@Composable
private fun FlowPinnedMessagesSheet(pinAfterUnpin: Long?) {
    val viewModel = flowSharedViewModel<ChatViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<PinnedMessageJump>(exit = dismissSheet)

    // Built off the main thread: a cash row looks up its token before it can draw.
    val rows by produceState<List<PinnedRow>?>(null, state.pins) { value = viewModel.pinnedRows(state.pins) }
    // The last rows stay up while the sheet slides away, so it never empties on the way out.
    var shownRows by remember { mutableStateOf<List<PinnedRow>>(emptyList()) }
    rows?.takeIf { it.isNotEmpty() }?.let { shownRows = it }

    // The last unpin, or the waiting message landing in the slot it freed, leaves nothing to do here.
    val done = state.pins.pinnedListDone(pinAfterUnpin)
    LaunchedEffect(done) { if (done) dismissSheet() }

    PinnedMessagesSheet(
        rows = shownRows,
        canUnpin = state.canPin,
        full = pinAfterUnpin != null,
        fullQuote = state.waitingPin?.takeIf { it.messageId == pinAfterUnpin }?.quote,
        onOpen = { resultBack.returnValue(PinnedMessageJump(it)) },
        onUnpin = { viewModel.dispatchEvent(ChatViewModel.Event.UnpinMessage(it, pinAfter = pinAfterUnpin)) },
        onClose = { dismissSheet() },
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

/**
 * The Paid Reactions sheet for the viewer's own reactions on [step]'s message, from the entry's
 * own [ReactionBoostViewModel]. A boost that goes through closes the sheet, as does taking back
 * the last reaction there is to boost; one the balance can't cover closes it for the deposit
 * options the chat already offers.
 */
@Composable
private fun FlowReactionBoostScreen(step: ChatStep.ReactionBoost) {
    val chatViewModel = flowSharedViewModel<ChatViewModel>()
    val viewModel = hiltViewModel<ReactionBoostViewModel>()
    val navigator = LocalCodeNavigator.current
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, step) {
        viewModel.dispatchEvent(ReactionBoostViewModel.Event.Open(step.chatId, step.messageId))
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                ReactionBoostViewModel.Event.Completed,
                ReactionBoostViewModel.Event.ReactionsGone -> dismissSheet()
                ReactionBoostViewModel.Event.AddMoney -> {
                    dismissSheet()
                    chatViewModel.dispatchEvent(ChatViewModel.Event.PresentDepositOptions)
                }
                else -> Unit
            }
        }
    }

    ReactionBoostSheet(
        emoji = state.emoji,
        split = state.split,
        canCycle = state.canCycle,
        token = state.token,
        presetCents = ReactionBoostViewModel.PresetCents,
        selectedCents = state.selectedCents,
        customCents = state.customCents,
        sendProgress = state.sendProgress,
        canConfirm = state.canConfirm,
        onCycleEmoji = { viewModel.dispatchEvent(ReactionBoostViewModel.Event.CycleEmoji) },
        onSelectToken = { navigator.push(ChatStep.ReactionBoostCurrency(step.chatId, step.messageId)) },
        onSelectAmount = { viewModel.dispatchEvent(ReactionBoostViewModel.Event.SelectAmount(it)) },
        onCustomAmount = {
            navigator.navigateForResult<ReactionBoostAmountResult>(
                ChatStep.ReactionBoostAmount(step.chatId, step.messageId)
            ) { result ->
                if (result is NavResultOrCanceled.ReturnValue) {
                    viewModel.dispatchEvent(ReactionBoostViewModel.Event.CustomAmountEntered(result.value.cents))
                }
            }
        },
        onConfirm = { viewModel.dispatchEvent(ReactionBoostViewModel.Event.Confirm) },
    )
}

/**
 * The keypad behind the Paid Reactions sheet's "…" chip, returning the amount in whole cents. An
 * entry of zero leaves the sheet open, the same rule as the group form's custom amount.
 */
@Composable
private fun FlowReactionBoostAmountScreen(step: ChatStep.ReactionBoostAmount) {
    val viewModel = hiltViewModel<ReactionBoostAmountViewModel>()
    val split by viewModel.split.collectAsStateWithLifecycle()
    LaunchedEffect(step.chatId, step.messageId) { viewModel.load(step.chatId, step.messageId) }
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val resultBack = resultBackNavigator<ReactionBoostAmountResult>(exit = dismissSheet)

    AmountEntryScreen(
        controller = viewModel.amountDelegate,
        onConfirm = {
            viewModel.enteredCents()?.let { resultBack.returnValue(ReactionBoostAmountResult(it)) }
        },
        largeHeader = true,
        appBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_paidReactions),
                titleAlignment = Alignment.CenterHorizontally,
                endContent = { AppBarDefaults.Close(onClick = dismissSheet) },
            )
        },
        // Absent until the split is known, as on the sheet.
        headerCaption = split?.let { copy ->
            {
                Text(
                    text = stringResource(
                        when (copy) {
                            ReactionBoostViewModel.SplitCopy.Dm -> R.string.subtitle_paidReactionsDm
                            ReactionBoostViewModel.SplitCopy.Shared -> R.string.subtitle_paidReactionsSplit
                            ReactionBoostViewModel.SplitCopy.Whole -> R.string.subtitle_paidReactionsWhole
                        },
                    ),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Start,
                )
            }
        },
    )
}
