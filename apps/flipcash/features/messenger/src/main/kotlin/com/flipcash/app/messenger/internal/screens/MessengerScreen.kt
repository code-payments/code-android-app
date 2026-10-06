package com.flipcash.app.messenger.internal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.flipcash.shared.chat.ui.media.ChatPhotoOverlay
import com.flipcash.shared.chat.ui.media.ChatPhotoSources
import com.flipcash.shared.chat.ui.media.LocalChatMediaProgress
import com.flipcash.shared.chat.ui.media.LocalChatPhotoSources
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.analytics.GroupGateFunding
import com.flipcash.analytics.GroupInviteSheetSource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileAddress
import com.flipcash.app.core.links.ExternalLinkUriHandler
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.MentionDestination
import com.flipcash.app.messenger.internal.link.CashCardTap
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge
import com.flipcash.app.messenger.internal.screens.components.ChatTopEdge.softTopEdge
import com.flipcash.app.messenger.internal.screens.components.MessageList
import com.flipcash.app.messenger.internal.screens.components.UserControlBottomBar
import com.getcode.ui.components.BlurredContent
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.shareable.LocalShareController
import com.flipcash.app.shareable.Shareable
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.theme.ScaffoldBarPlacement
import com.getcode.ui.utils.rememberKeyboardController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.filterIsInstance

@Composable
internal fun MessengerScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val mediaProgress = viewModel.mediaSendProgress.collectAsStateWithLifecycle()
    val mediaProgressOf = remember(mediaProgress) { { mediaProgress.value } }
    val otherReadPointer by viewModel.otherReadPointer.collectAsStateWithLifecycle(null)
    val navigator = LocalCodeNavigator.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Links a sender typed are the ones that can lead anywhere, so only the transcript asks before
    // leaving. The cash card goes through [uriHandler] above: it is ours, and opens directly.
    val transcriptUriHandler = remember(context, uriHandler) {
        ExternalLinkUriHandler(context, uriHandler)
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
    var openPhotoId by remember { mutableStateOf<Long?>(null) }

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

    val chatActionHandler = { action: ChatAction ->
        when (action) {
            is ChatAction.AdvanceReadPointer -> {
                viewModel.dispatchEvent(ChatViewModel.Event.AdvanceReadPointer(action.messageId))
            }

            ChatAction.RefreshContact -> {
                viewModel.dispatchEvent(ChatViewModel.Event.RefreshContact)
            }

            is ChatAction.OpenPhoto -> {
                // After the keyboard is down, so the bubble the photo grows from has settled.
                keyboard.hideIfVisible { openPhotoId = action.bubble.messageId }
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
                // Only the gate asks to come back after a buy, so that flag is what marks its tap.
                if (action.returnAfterBuy) {
                    viewModel.dispatchEvent(ChatViewModel.Event.GateFundingTapped(GroupGateFunding.BUY_TOKEN))
                }
                keyboard.hideIfVisible {
                    viewModel.dispatchEvent(
                        ChatViewModel.Event.OpenScreen(
                            // A drill-in from the transcript, so push it: the fade-in-place
                            // expand is the wallet card growing into its own detail, and there
                            // is no card here for it to grow from.
                            AppRoute.Token.Info(
                                action.mint,
                                if (action.returnAfterBuy) TokenInfoEntry.ChatGate else TokenInfoEntry.Chat,
                            )
                        )
                    )
                }
            }

            ChatAction.OpenEncryptionInfo -> keyboard.hideIfVisible {
                navigator.push(AppRoute.Messaging.E2eeDmInfo)
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
                                // Pushed, so Back returns to this chat. The pushed screen gates
                                // itself: a non-member sees the join or the buy there.
                                AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(action.chatId))
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

            is ChatAction.CashLinkOpened -> when (state.cashCardTap) {
                CashCardTap.JoinToCollect -> viewModel.dispatchEvent(ChatViewModel.Event.CashLinkRefused)
                // Reported before the link leaves, so the tap is on record by the time the claim
                // can come back.
                is CashCardTap.Collect -> {
                    viewModel.dispatchEvent(ChatViewModel.Event.CashLinkOpened(action.entropy))
                    uriHandler.openUri(action.url)
                }
            }

            is ChatAction.JumpToMessage -> {
                viewModel.dispatchEvent(ChatViewModel.Event.JumpToMessage(action.messageId))
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

            ChatAction.InviteToGroup -> {
                // The sheet, not the share sheet: copying the link is the other way to hand it out,
                // and going straight to the system share picker would bury it. The sheet reads the
                // url off the same state the CTA that got here is gated on.
                viewModel.dispatchEvent(ChatViewModel.Event.InviteSheetOpened(GroupInviteSheetSource.CHAT))
                keyboard.hideIfVisible { navigator.openGroupInvite() }
            }

            is ChatAction.ViewProfile -> {
                // The triggers (top-bar tap, info-card chevron) are only clickable for subjects
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

    Box(modifier = Modifier.fillMaxSize()) {
    CodeScaffold(
        // The input bar rides the keyboard; the message list is inset by it either way.
        modifier = Modifier.imePadding(),
        // The list runs the full height and passes under both bars, each of which fades it out
        // against the background at its own edge.
        barPlacement = ScaffoldBarPlacement.Overlay,
        topBar = {
            ChatTopBar(
                navigator = navigator,
                state = state,
                onBarHeightChange = { barHeight = it },
                chatActionHandler = chatActionHandler,
                dispatch = viewModel::dispatchEvent,
            )
        },
        bottomBar = {
            // Behind the backdrop with the transcript while a message is raised, dimmed and blurred
            // as its rows are, and a tap on it dismisses the selection as a tap on them does. An
            // edit raises a message too, but it is the composer doing the editing, so it stays sharp.
            val behindBackdrop = state.selection != null && state.editing == null
            val dimAlpha by animateFloatAsState(
                targetValue = if (behindBackdrop) 0.4f else 1f,
                label = "composerDim",
            )
            val dimBlur by animateDpAsState(
                targetValue = if (behindBackdrop) 8.dp else 0.dp,
                label = "composerBlur",
            )
            Box {
                Box(
                    modifier = Modifier
                        .blur(dimBlur, BlurredEdgeTreatment.Unbounded)
                        .graphicsLayer { alpha = dimAlpha },
                ) {
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
            ) {
                MessageList(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_message_list")
                        .softTopEdge(ChatTopEdge.blurHold(barHeight))
                        .hazeSource(hazeState),
                    state = state,
                    contentPadding = overlapPadding,
                    messages = messages,
                    separatorConfig = state.separatorConfig,
                    otherReadPointer = otherReadPointer,
                    onAction = chatActionHandler,
                    linkCardResolution = viewModel.linkCardResolution,
                    canViewProfile = state.canViewProfile,
                    onJumpConsumed = { viewModel.dispatchEvent(ChatViewModel.Event.JumpConsumed) },
                    topBarBottom = barHeight,
                )
            }
        }
    }

    openPhotoId?.let { id ->
        ChatPhotoOverlay(
            messageId = id,
            sources = photoSources,
            onClosed = { openPhotoId = null },
        )
    }
    }
}
