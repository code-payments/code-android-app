package com.flipcash.app.messenger.internal.screens

import com.flipcash.services.models.paymentrequest.unpaidShareOf
import com.flipcash.app.messenger.internal.request.RequestDeckStrip
import com.flipcash.app.messenger.internal.request.rememberDeckHostState
import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.flipcash.shared.chat.ui.media.ChatPhotoOverlay
import com.flipcash.shared.chat.ui.media.ChatPhotoSources
import com.flipcash.shared.chat.ui.media.chatPhotoTranscript
import com.flipcash.shared.chat.ui.media.LocalChatMediaProgress
import com.flipcash.shared.chat.ui.media.LocalChatPhotoSources
import com.flipcash.shared.chat.ui.LocalUpdateAppAction
import com.flipcash.shared.chat.ui.UpdateAppAction
import com.flipcash.app.updates.LocalAppUpdater
import com.flipcash.features.messenger.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import com.flipcash.shared.chat.alerts.LocalReactionAlerts
import com.flipcash.shared.chat.alerts.ReactionAlertAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.analytics.GroupGateFunding
import com.flipcash.analytics.GroupInviteSheetSource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.core.tokens.SwapResult
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.chat.ChatStep
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.links.ExternalLinkUriHandler
import com.flipcash.app.core.links.LocalTrustedWebsites
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.ownReactionEmoji
import com.flipcash.app.messenger.internal.reactionStripFor
import com.flipcash.app.messenger.internal.withLiveSelfReactions
import com.flipcash.app.messenger.internal.MentionDestination
import com.flipcash.app.messenger.internal.link.CashCardTap
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.deckBackdropSpec
import com.flipcash.app.messenger.internal.screens.components.chatBackdrop
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge.softTopEdge
import com.flipcash.app.messenger.internal.screens.components.GatedLandingRow
import com.flipcash.app.messenger.internal.screens.components.JoinRequestsAnchor
import com.flipcash.app.messenger.internal.screens.components.ReturnToChatChip
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.app.messenger.internal.screens.components.UserControlBottomBar
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.BlurredContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.results.NavResultOrCanceled
import com.getcode.navigation.results.navigateForResult
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import com.getcode.ui.utils.rememberKeyboardController
import com.getcode.utils.hexEncodedString
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.filterIsInstance
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.common.ui.profile.PrefetchProfileCover
import com.flipcash.app.messenger.internal.request.ChatRequestsViewModel
import com.flipcash.app.messenger.internal.request.rememberPaymentRequestCards
import com.flipcash.app.messenger.internal.request.ExpandedRequests
import com.flipcash.app.messenger.internal.request.RequestDeckLayout
import com.flipcash.app.messenger.internal.request.RequestDeckPill
import com.flipcash.app.messenger.internal.request.deckTitle
import com.flipcash.shared.chat.ui.request.LocalPaymentRequestCards
import com.flipcash.services.models.chat.ChatType
import com.getcode.utils.base58
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
internal fun MessengerScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val previewsReady by viewModel.previewsReady.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val mediaProgress = viewModel.mediaSendProgress.collectAsStateWithLifecycle()
    val mediaProgressOf = remember(mediaProgress) { { mediaProgress.value } }
    val otherReadPointer by viewModel.otherReadPointer.collectAsStateWithLifecycle(null)
    val unreadBelowCount by viewModel.unreadBelowCount.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Links a sender typed are the ones that can lead anywhere, so only the transcript asks before
    // leaving. The cash card goes through [uriHandler] above: it is ours, and opens directly.
    val trustedWebsites = LocalTrustedWebsites.current
    // A message link is followed in the app before anything else sees it: through the system it
    // would come back as a cold deeplink, which replaces the stack and loses this chat.
    val transcriptUriHandler = remember(context, uriHandler, trustedWebsites, viewModel) {
        val external = ExternalLinkUriHandler(context, uriHandler, trustedWebsites)
        object : UriHandler {
            override fun openUri(uri: String) {
                if (!viewModel.followMessageLink(uri)) external.openUri(uri)
            }
        }
    }

    val userManager = LocalUserManager.current
    val shareController = LocalShareController.current
    val scope = rememberCoroutineScope()

    val hazeState = rememberHazeState()
    // Measured by the bar and read by the transcript: the blur has to cover the bar's own
    // height, which the selection and editing modes change.
    var barHeight by remember { mutableStateOf(0.dp) }
    val keyboard = rememberKeyboardController()
    // The photo bubbles on screen, and the photo opened over them. The overlay sits above the whole
    // screen rather than being a route, so it can grow out of and shrink back into its bubble.
    val photoSources = remember { ChatPhotoSources() }
    var openPhoto by remember { mutableStateOf<ChatListItem.ContentBubble?>(null) }

    // A private group the viewer is not in has no transcript to show: no key envelope, so nothing
    // to decrypt. A share link or deep link only knows the chat id and lands here first, so once the
    // group resolves as private, swap to its profile, where the viewer can ask in.
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val privateOutsider = (state.subject as? ChatSubject.Group)?.let { it.isPrivate && it.isMember != true } == true
    LaunchedEffect(privateOutsider) {
        if (privateOutsider) flowNavigator.replaceStack(listOf(ChatStep.GroupProfile))
    }

    // A reaction pill tapped while this chat is open asks to scroll to its message and pulse it.
    val reactionAlerts = LocalReactionAlerts.current
    val currentChatId by rememberUpdatedState(state.chatId)
    LaunchedEffect(reactionAlerts, viewModel) {
        reactionAlerts?.actions
            ?.filterIsInstance<ReactionAlertAction.ScrollTo>()
            ?.collect { action ->
                if (action.chatId == currentChatId) {
                    viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(action.messageId))
                }
            }
    }

    // Here rather than in the action handler because the lookup comes first: the tap only knows a
    // handle, and where it leads is the view model's to decide once the handle has an answer.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.OpenMention>()
            .collect { (destination) ->
                keyboard.hideIfVisible {
                    when (destination) {
                        // As a person card linking to the viewer does: their card is on the You tab.
                        MentionDestination.OwnTipCard ->
                            navigator.rootNavigator.navigateAll(listOf(AppRoute.Tabs.Menu))
                        is MentionDestination.Profile ->
                            navigator.push(ChatStep.Profile(destination.participant, destination.origin))
                        // Raised as dialogs by the view model; they never reach here.
                        is MentionDestination.NoSuchAccount,
                        MentionDestination.LookupFailed -> Unit
                    }
                }
            }
    }

    // The unsupported-message bubble offers the update whenever Play has one, with none of the
    // toast's priority, staleness or once-per-version rules.
    val updater = LocalAppUpdater.current
    val optionalUpdate by updater.optionalUpdate.collectAsStateWithLifecycle()
    val updateLabel = stringResource(R.string.action_updateFlipcash)
    val updateAppAction = remember(optionalUpdate != null, updater, updateLabel) {
        if (optionalUpdate == null) null
        else UpdateAppAction(label = updateLabel, onClick = { scope.launch { updater.startOptionalUpdate() } })
    }

    val chatActionHandler = { action: ChatAction ->
        when (action) {
            is ChatAction.AdvanceReadPointer -> {
                viewModel.dispatchEvent(ChatViewModel.Event.AdvanceReadPointer(action.messageId))
            }

            is ChatAction.OpenPhoto -> {
                // After the keyboard is down, so the bubble the photo grows from has settled.
                keyboard.hideIfVisible { openPhoto = action.bubble }
            }

            is ChatAction.RetryMessage -> {
                keyboard.hideIfVisible {
                    viewModel.dispatchEvent(
                        ChatViewModel.Event.RetryMessage(
                            action.bubble.pendingClientIdHex,
                            action.bubble.content
                        )
                    )
                }
            }

            is ChatAction.ViewToken -> {
                keyboard.hideIfVisible {
                    viewModel.dispatchEvent(
                        ChatViewModel.Event.OpenScreen(
                            // A drill-in from the transcript, so push it: the fade-in-place
                            // expand is the wallet card growing into its own detail, and there
                            // is no card here for it to grow from.
                            AppRoute.Token.Info(action.mint, TokenInfoEntry.Chat)
                        )
                    )
                }
            }

            is ChatAction.ViewGroupSend -> {
                val cash = action.bubble.content as? MessageContent.Cash
                if (cash != null) keyboard.hideIfVisible {
                    navigator.push(
                        ChatStep.GroupSendDetails(
                            mint = cash.mint,
                            tokenName = cash.tokenName,
                            tokenImageUrl = cash.tokenImageUrl,
                            senderIdHex = action.bubble.authorId?.hexEncodedString(),
                            // A one-recipient group payment names its payee in `receiver`, not `multiReceiver`.
                            recipientIdsHex = cash.multiReceiver.map { it.receiver }
                                .ifEmpty { listOfNotNull(cash.receiver) }
                                .map { it.hexEncodedString() },
                        )
                    )
                }
            }

            is ChatAction.BuyGateToken -> {
                viewModel.dispatchEvent(ChatViewModel.Event.GateFundingTapped(GroupGateFunding.BUY_TOKEN))
                keyboard.hideIfVisible {
                    // Straight to the buy screen, as iOS's gate does. Finishing the buy lands back
                    // on this chat, in front of Join.
                    navigator.navigateForResult<SwapResult>(
                        AppRoute.Token.Swap(SwapPurpose.Buy(action.mint))
                    ) { result ->
                        if (result is NavResultOrCanceled.ReturnValue && result.value == SwapResult.OpenDeposit) {
                            navigator.push(AppRoute.Transfers.Deposit(showOtherOptions = false))
                        }
                    }
                }
            }

            ChatAction.OpenEncryptionInfo -> keyboard.hideIfVisible {
                // The marker shows in DMs and in private groups; each has its own copy.
                val privateGroup = (state.subject as? ChatSubject.Group)?.isPrivate == true
                navigator.push(
                    if (privateGroup) AppRoute.Messaging.E2eePrivateGroupInfo else AppRoute.Messaging.E2eeDmInfo
                )
            }

            ChatAction.AddCash -> {
                // The gate is the only place in the transcript that offers it.
                viewModel.dispatchEvent(ChatViewModel.Event.GateFundingTapped(GroupGateFunding.ADD_CASH))
                keyboard.hideIfVisible {
                    viewModel.dispatchEvent(ChatViewModel.Event.PresentDepositOptions)
                }
            }

            is ChatAction.OpenGroup -> {
                // An invite to the chat already on screen has nowhere to go.
                if (action.chatId != state.chatId) {
                    viewModel.dispatchEvent(ChatViewModel.Event.InviteCardFollowed)
                    keyboard.hideIfVisible {
                        viewModel.dispatchEvent(
                            ChatViewModel.Event.OpenScreen(
                                // Pushed, so Back returns to this chat. A resolved card opens
                                // the group's profile, as a featured group does: Open Chat there
                                // pushes the transcript, which gates itself for a non-member.
                                // Until the card resolves there is no record to show, so it opens
                                // the chat.
                                AppRoute.Messaging.Chat(
                                    identifier = ChatIdentifier.ByChatId(action.chatId),
                                    openOnProfile = action.resolved,
                                    isGroupLink = true,
                                )
                            )
                        )
                    }
                }
            }

            is ChatAction.OpenUser -> {
                val counterpart = (state.participant as? ChatParticipant.TipUser)?.userId
                when {
                    // Your own card lives on the You tab, where scanning it and its deep link
                    // already send you. There is no chat with yourself to open.
                    action.isOwn -> keyboard.hideIfVisible {
                        navigator.rootNavigator.navigateAll(listOf(AppRoute.Tabs.Menu))
                    }
                    // A link to the person this DM is with has nowhere to go.
                    action.userId == counterpart -> Unit
                    else -> keyboard.hideIfVisible {
                        viewModel.dispatchEvent(
                            ChatViewModel.Event.OpenScreen(
                                // The profile their flipcash.com link opens, pushed as the group
                                // card pushes its chat, so Back returns here. Its Message shortcut
                                // goes on to the DM.
                                AppRoute.Messaging.Profile(ProfileAddress.ById(action.userId))
                            )
                        )
                    }
                }
            }

            is ChatAction.OpenMention -> {
                viewModel.dispatchEvent(ChatViewModel.Event.MentionTapped(action.username))
            }

            is ChatAction.ToggleSelection -> {
                viewModel.dispatchEvent(ChatViewModel.Event.ToggleMessageSelection(action.bubble))
            }

            is ChatAction.PresentReactionStrip -> {
                viewModel.dispatchEvent(ChatViewModel.Event.PresentReactionStrip(action.bubble))
            }

            ChatAction.ClearSelection -> {
                viewModel.dispatchEvent(ChatViewModel.Event.ClearMessageSelection)
            }

            ChatAction.CancelEdit -> {
                viewModel.dispatchEvent(ChatViewModel.Event.CancelEdit)
            }

            // Both reply entry points land on one event so the citation is resolved in one place;
            // turning a bubble into a quote needs a database read.
            is ChatAction.ReplyTo -> {
                viewModel.dispatchEvent(ChatViewModel.Event.ReplyRequested(action.bubble))
            }

            ChatAction.CancelReply -> {
                viewModel.dispatchEvent(ChatViewModel.Event.CancelReply)
            }

            is ChatAction.CashLinkOpened -> when (val tap = state.cashCardTap) {
                CashCardTap.JoinToCollect,
                CashCardTap.ChatToCollect -> viewModel.dispatchEvent(ChatViewModel.Event.CashLinkRefused(tap))
                // Reported before the link leaves, so the tap is on record by the time the claim
                // can come back.
                is CashCardTap.Collect -> {
                    viewModel.dispatchEvent(ChatViewModel.Event.CashLinkOpened(action.entropy))
                    uriHandler.openUri(action.url)
                }
            }

            is ChatAction.JumpToMessage -> {
                viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(action.messageId, origin = action.fromMessageId))
            }

            // The profile a share-profile widget names, shared the way a profile's Share shortcut
            // shares it (`rememberProfileShare`): the handle form, titled with their name.
            is ChatAction.ShareProfile -> scope.launch {
                shareController.present(
                    Shareable.Profile(
                        userId = action.userId,
                        displayName = action.displayName,
                        username = action.username,
                    )
                )
            }

            ChatAction.JoinChat -> viewModel.dispatchEvent(ChatViewModel.Event.JoinChat)

            is ChatAction.ViewProfile -> {
                // The trigger (the top-bar tap) is only clickable for subjects
                // that have a profile (see State.canViewProfile), so no gating is needed here.
                // Which profile depends on the subject: a DM's is its counterparty's, and a group
                // is its own — it has no participant to open one on.
                keyboard.hideIfVisible {
                    when (state.subject) {
                        is ChatSubject.Group -> {
                            viewModel.dispatchEvent(ChatViewModel.Event.GroupInfoOpened)
                            navigator.push(ChatStep.GroupProfile)
                        }
                        else -> state.participant?.let { navigator.push(ChatStep.Profile(it)) }
                    }
                }
            }

            is ChatAction.ViewMemberProfile -> {
                // Resolved in the view model: the profile that drew the picture in the gutter is
                // the one to open, and the transcript already holds it.
                viewModel.memberParticipant(action.userId)?.let {
                    keyboard.hideIfVisible { navigator.push(ChatStep.Profile(it)) }
                }
            }

            is ChatAction.ToggleReaction -> {
                viewModel.dispatchEvent(
                    ChatViewModel.Event.ToggleReaction(
                        messageId = action.messageId,
                        emoji = action.emoji,
                        clearsSelection = action.fromStrip,
                    )
                )
            }

            is ChatAction.OpenReactionPicker -> {
                navigator.push(ChatStep.ReactionPicker(action.messageId))
            }
            is ChatAction.OpenBoost -> {
                val chatId = state.chatId
                if (chatId != null) navigator.push(ChatStep.ReactionBoost(chatId, action.messageId))
            }

            is ChatAction.OpenReactors -> {
                val chatId = state.chatId
                if (chatId != null) navigator.push(ChatStep.Reactors(chatId, action.messageId))
            }

            is ChatAction.RefreshReactionIds -> {
                viewModel.dispatchEvent(ChatViewModel.Event.RefreshReactionIds(action.messageIds))
            }
        }

        Unit
    }

    // Back unwinds the message actions before it leaves the conversation, innermost first: an edit
    // in progress, then the selection bar. Registered here rather than around the whole flow so
    // they are the innermost handlers and take the gesture before it pops the conversation.
    BackHandler(enabled = state.editing != null) {
        viewModel.dispatchEvent(ChatViewModel.Event.CancelEdit)
    }
    BackHandler(enabled = state.editing == null && state.selection != null) {
        viewModel.dispatchEvent(ChatViewModel.Event.ClearMessageSelection)
    }

    // The group's profile is one tap away; have its cover downloaded before that tap.
    // The flow's, so the Request details step can minimize the deck once it covers the chat.
    val requestsViewModel = flowSharedViewModel<ChatRequestsViewModel>()
    val requestCards = rememberPaymentRequestCards(
        viewModel = requestsViewModel,
        chatId = state.chatId.takeIf { state.chatType == ChatType.GROUP },
        onOpen = { chatId, id -> keyboard.hideIfVisible { navigator.push(ChatStep.RequestDetails(chatId, id.base58)) } },
    )

    (state.subject as? ChatSubject.Group)?.let { group ->
        PrefetchProfileCover(group.coverPicture, BlobAccessContext.ChatProfile(group.chatId))
    }

    // Before the deck is read, so requests already held are there on the first frame.
    val deckChat = state.chatId.takeIf { state.chatType == ChatType.GROUP }
    remember(deckChat) { deckChat?.also(requestsViewModel::attach) }
    val deck by requestsViewModel.deck.collectAsStateWithLifecycle()
    val deckExpanded by requestsViewModel.expanded.collectAsStateWithLifecycle()
    val deckNow by requestsViewModel.now.collectAsStateWithLifecycle()
    val drawnDeck = remember(deck) { deck.filter { it.deckTitle != null } }
    val viewerId = requestsViewModel.viewerId
    // One per visit: reopening the chat while still owing brings the strip back.
    val owed = drawnDeck.filter { it.unpaidShareOf(viewerId, deckNow) != null }.map { it.id }.toSet()
    val deckHosts = rememberDeckHostState(deckChat, owed)
    val deckTransition = rememberTransition(deckHosts.transition, label = "request deck")
    // Read here rather than in the effect, so expanding recomposes this scope and reaches the hosts.
    val expandedNow = deckExpanded
    SideEffect {
        deckHosts.owing(owed, hasDeck = drawnDeck.isNotEmpty())
        deckHosts.expanded = expandedNow
    }
    val expandDeck = {
        // Partway into the pill, a tap waits for the scroll to settle it.
        if (!deckHosts.collapsing) keyboard.hideIfVisible { requestsViewModel.setExpanded(true) }
    }
    val openRequest: (com.flipcash.services.models.paymentrequest.PaymentRequest) -> Unit = { request ->
        // The deck stays expanded while the details slide over it; the step minimizes it once covered.
        state.chatId?.let { chatId ->
            keyboard.hideIfVisible { navigator.push(ChatStep.RequestDetails(chatId, request.id.base58)) }
        }
    }

    // The collapsed pill's last bounds, where the expanded deck puts its close button.
    var deckPill by remember { mutableStateOf<Rect?>(null) }

    RequestDeckLayout(modifier = Modifier.fillMaxSize()) {
    CodeScaffold(
        // The input bar rides the keyboard; the message list is inset by it either way.
        // The expanded request deck raises its cards over the whole screen, the bars included.
        modifier = Modifier
            .imePadding()
            .chatBackdrop(behind = deckExpanded, animationSpec = deckBackdropSpec(deckExpanded)),
        // The list runs the full height and passes under both bars, each of which fades it out
        // against the background at its own edge.
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = {
            // The anchor rides in the top slot rather than over the list, so the transcript's top
            // inset grows by its height and the oldest message clears it. barHeight stays the bar's
            // own: the soft top edge belongs to the bar, and the anchor is glass over the scroll.
            Column {
                ChatTopBar(
                    navigator = navigator,
                    state = state,
                    onBarHeightChange = { barHeight = it },
                    chatActionHandler = chatActionHandler,
                    dispatch = viewModel::dispatchEvent,
                    trailing = {
                        if (state.chatType == ChatType.GROUP) {
                            RequestDeckPill(
                                deck = drawnDeck,
                                now = deckNow,
                                viewerId = viewerId,
                                transition = deckTransition,
                                onExpand = expandDeck,
                                onCycle = requestsViewModel::cycle,
                                modifier = Modifier.onGloballyPositioned {
                                    // Kept from the last frame it had a size; it empties while expanded.
                                    if (it.size.width > 0) deckPill = it.boundsInWindow()
                                },
                            )
                        }
                    },
                )
                if (state.chatType == ChatType.GROUP) {
                    RequestDeckStrip(
                        deck = drawnDeck,
                        now = deckNow,
                        viewerId = viewerId,
                        transition = deckTransition,
                        rest = deckHosts.rest.host,
                        onExpand = expandDeck,
                        onCycle = requestsViewModel::cycle,
                        onPay = { request ->
                            state.chatId?.let { chatId ->
                                keyboard.hideIfVisible {
                                    navigator.push(ChatStep.RequestDetails(chatId, request.id.base58, pay = true))
                                }
                            }
                        },
                        onHeightChange = { deckHosts.stripPx = it.toFloat() },
                    )
                }
                // Hold the last list through the exit, so the card doesn't empty as it slides away.
                var lastRequests by remember { mutableStateOf(state.joinRequests) }
                if (state.joinRequests.isNotEmpty()) lastRequests = state.joinRequests
                AnimatedVisibility(
                    visible = state.showsJoinRequestsAnchor,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    JoinRequestsAnchor(
                        requests = lastRequests,
                        onReview = { keyboard.hideIfVisible { navigator.push(ChatStep.JoinRequests) } },
                        hazeState = hazeState,
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.grid.x2),
                    )
                }
                // Held through the exit for the same reason as the requests above.
                var lastReturnTitle by remember { mutableStateOf(state.returnToTitle) }
                state.returnToTitle?.let { lastReturnTitle = it }
                AnimatedVisibility(
                    visible = state.returnToTitle != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    ReturnToChatChip(
                        title = lastReturnTitle.orEmpty(),
                        onClick = { keyboard.hideIfVisible { navigator.pop() } },
                        hazeState = hazeState,
                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                    )
                }
            }
        },
        bottomBar = {
            // Behind the backdrop with the transcript while a message is raised, dimmed and blurred
            // as its rows are, and a tap on it dismisses the selection as a tap on them does. An
            // edit raises a message too, but it is the composer doing the editing, so it stays sharp.
            val behindBackdrop = state.selection != null && state.editing == null
            Box {
                Box(modifier = Modifier.chatBackdrop(behind = behindBackdrop)) {
                    UserControlBottomBar(
                        state = state,
                        hazeState = hazeState,
                        onAction = chatActionHandler,
                        dispatch = viewModel::dispatchEvent,
                        topBarHeight = barHeight,
                    )
                }
                if (behindBackdrop) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    viewModel.dispatchEvent(ChatViewModel.Event.ClearMessageSelection)
                                }
                            },
                    )
                }
            }
        },
    ) { overlapPadding ->
        // The transcript and the info card go behind a blur together, because they are one surface
        // to the reader: legible metadata over an unreadable conversation would be the gate half
        // open. The title bar stays sharp — it is how a non-member knows what they are looking at.
        BlurredContent(
            modifier = Modifier.fillMaxSize(),
            enabled = state.obscuresTranscript,
        ) {
            CompositionLocalProvider(
                LocalUriHandler provides transcriptUriHandler,
                LocalChatMediaProgress provides mediaProgressOf,
                LocalChatPhotoSources provides photoSources,
                LocalUpdateAppAction provides updateAppAction,
                LocalPaymentRequestCards provides requestCards,
            ) {
                // Held for the saved link previews, 300 ms at most and the first chat only.
                if (previewsReady) {
                    MessageList(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_message_list")
                            .nestedScroll(deckHosts.scrollConnection)
                            .softTopEdge(ChatTopEdge.blurHold(barHeight))
                            .hazeSource(hazeState)
                            // Inside the edge blur and the haze, so a photo landing under the bars
                            // passes behind them the way its bubble does.
                            .chatPhotoTranscript(
                                sources = photoSources,
                                top = barHeight,
                                bottom = overlapPadding.calculateBottomPadding(),
                            ),
                        state = state,
                        contentPadding = overlapPadding,
                        messages = messages,
                        separatorConfig = state.separatorConfig,
                        otherReadPointer = otherReadPointer,
                        onAction = chatActionHandler,
                        linkCardResolution = viewModel.linkCardResolution,
                        onJumpConsumed = { viewModel.dispatchEvent(ChatViewModel.Event.JumpConsumed) },
                        topBarBottom = barHeight,
                        unreadBelowCount = unreadBelowCount,
                        onUserScrolled = { viewModel.dispatchEvent(ChatViewModel.Event.UserScrolledTranscript) },
                        onReturnToOrigin = { viewModel.dispatchEvent(ChatViewModel.Event.ReturnToOrigin) },
                        onOriginsReached = { viewModel.dispatchEvent(ChatViewModel.Event.ReturnOriginsReached(it)) },
                        onReachedNewest = { viewModel.dispatchEvent(ChatViewModel.Event.ReturnStackCleared) },
                        hazeState = hazeState,
                    )
                }
            }
        }
        // Outside the blur: a message link that lands where the transcript is withheld still shows
        // the message it points at, over the placeholder rows, between the bars.
        state.gatedLanding?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(overlapPadding),
                contentAlignment = Alignment.Center,
            ) {
                GatedLandingRow(item = item)
            }
        }
    }

    ExpandedRequests(
        deck = drawnDeck,
        now = deckNow,
        viewerId = viewerId,
        expanded = deckExpanded,
        transition = deckTransition,
        top = barHeight,
        pill = deckPill,
        onCollapse = { requestsViewModel.setExpanded(false) },
        onOpen = openRequest,
    )

    openPhoto?.let { opened ->
        // The bubble the photo opened from is a snapshot; the strip highlights the live picks.
        val live by remember(opened.messageId) { viewModel.selfReactionsOf(opened.messageId) }
            .collectAsStateWithLifecycle(initialValue = null)
        val bubble = opened.withLiveSelfReactions(live)
        ChatPhotoOverlay(
            messageId = bubble.messageId,
            sources = photoSources,
            onClosed = { openPhoto = null },
            onSaveResult = { saved -> viewModel.dispatchEvent(ChatViewModel.Event.PhotoSaveResult(saved)) },
            // The same actions the message's context menu offers, gated by the same capabilities.
            // React sends the event the menu's strip does, and the strip is built the same way.
            onReact = if (bubble.canReact) {
                { emoji ->
                    viewModel.dispatchEvent(
                        ChatViewModel.Event.ToggleReaction(bubble.messageId, emoji, clearsSelection = true)
                    )
                }
            } else null,
            reactionStrip = state.reactionStripFor(bubble),
            ownReactions = bubble.ownReactionEmoji(),
            onOpenPicker = { navigator.push(ChatStep.ReactionPicker(bubble.messageId)) },
            // Reply sets the strip the way the menu's Reply does; the overlay closes itself.
            onReply = if (MessageCapability.Reply in bubble.capabilities) {
                { viewModel.dispatchEvent(ChatViewModel.Event.ReplyRequested(bubble)) }
            } else null,
            onForward = if (MessageCapability.Link in bubble.capabilities) {
                { navigator.openMessageShare(bubble.messageId) }
            } else null,
        )
    }
    }
}
