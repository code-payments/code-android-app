package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import com.flipcash.shared.chat.RequestAge
import com.getcode.theme.White10
import com.getcode.theme.White05
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.text.style.TextAlign
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Clock
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.PersonOff
import com.getcode.ui.components.SubmenuDropdownMenu
import com.getcode.ui.components.SubmenuRow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.analytics.GroupInviteSheetSource
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.core.tokens.TokenInfoEntry
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.rememberMutedLabel
import com.flipcash.app.messenger.internal.screens.components.ChatSubjectAvatar
import com.flipcash.app.messenger.internal.invites.InviteHostViewModel
import com.flipcash.app.messenger.internal.screens.openGroupInvite
import com.flipcash.app.messenger.internal.screens.openInviteLinkShare
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.common.ui.profile.BalanceRequirementsCard
import com.flipcash.shared.common.ui.profile.ChattingGridItem
import com.flipcash.shared.common.ui.profile.GroupChattingGrid
import com.flipcash.shared.common.ui.profile.ProfileActionButton
import com.flipcash.shared.common.ui.profile.ProfileHeader
import com.flipcash.shared.common.ui.profile.ProfilePinnedActionBar
import com.flipcash.shared.common.ui.profile.ProfileStatusChip
import com.flipcash.shared.common.ui.profile.ProfileTopScrollEdge
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.BottomScrollEdge
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The group's own profile, reached from the info card at the head of its transcript.
 *
 * Driven by the conversation's [ChatViewModel] rather than one of its own: the title, picture,
 * rules and membership are state the transcript already holds and keeps current. What is specific
 * to this screen (who is chatting, the pinned action's standing, the names of the tokens the rules
 * ask for) is loaded or derived there too, keyed off [ChatViewModel.Event.GroupProfileOpened].
 */
@Composable
internal fun GroupProfileScreen(viewModel: ChatViewModel) {
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val navigator = LocalCodeNavigator.current
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val group = state.subject as? ChatSubject.Group
    val isMember = group?.isMember == true
    val inviteHost = hiltViewModel<InviteHostViewModel>()
    val canInviteByChat by inviteHost.canInvite.collectAsStateWithLifecycle()
    val canInvite = state.chatId?.let { canInviteByChat[it] } == true
    LaunchedEffect(state.chatId, group?.isPrivate) {
        // Only a private group has invite links, and the answer is the server's, not the cached chat's.
        if (group?.isPrivate == true && isMember) state.chatId?.let(inviteHost::refresh)
    }
    val moderation = state.viewerState?.permissions?.moderation
    val canModerate = isMember && (moderation?.canSilence == true || moderation?.canBan == true)
    // A private group's non-member has no chat to open: no envelope, no transcript. The profile is
    // where they ask in, and where they wait.
    val privateJoin = PrivateGroupJoin.resolve(
        isPrivate = group?.isPrivate == true,
        isMember = group?.isMember,
        requestedAt = state.lobbyRequestedAt,
    )

    // Each time the screen is entered, not once per flow: the view model outlives this screen, and
    // who is chatting moves between visits.
    LaunchedEffect(viewModel, state.chatId) {
        if (state.chatId != null) viewModel.dispatchEvent(ChatViewModel.Event.GroupProfileOpened)
    }

    // Leaving exits the whole chat flow rather than popping this screen, landing back on the chat
    // list: a group you have left is not a conversation you are still in, and closing to the
    // transcript would leave you reading it from behind the gate you just put yourself outside of.
    // FlowHost.onExit pops the Chat route.
    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatViewModel.Event.LeftChat>()
            .collect { flowNavigator.exitCanceled() }
    }

    // The chat is usually the screen underneath. A flow opened on the profile
    // (AppRoute.Messaging.Chat.openOnProfile) has nothing underneath, so the transcript is pushed on
    // top and back from it returns here, as iOS does. Read once: back pops the stack before this
    // screen finishes animating out, and a live read would bring the pinned bar back for the exit.
    val openedFromChat = remember { flowNavigator.canGoBack }

    fun openChat() {
        if (openedFromChat) {
            flowNavigator.back()
        } else {
            flowNavigator.navigateTo(ChatStep.Conversation)
        }
    }

    val standing = state.profileStanding
    val requirements = remember(group?.rules) { GroupBalanceRequirements.from(group?.rules) }
    val tokens = state.ruleTokens
    // The amounts read "$1 of Moony" once the token is named; held back until then so they don't
    // read "$1" first and change under the reader. A token that never resolves stops holding
    // them after a moment, and they read as a bare amount, as they did before.
    var namesTimedOut by remember(requirements) { mutableStateOf(false) }
    LaunchedEffect(requirements) {
        delay(RuleNameHold)
        namesTimedOut = true
    }
    val namesReady = namesTimedOut || requirements == null ||
        listOfNotNull(requirements.join, requirements.chat)
            .mapNotNull { it.mints.firstOrNull()?.let { m -> Mint(m.bytes) } }
            .filterNot { it == Mint.usdf }
            .all { tokens.containsKey(it.bytes) }

    var menuOpen by remember { mutableStateOf(false) }
    var pinnedHeight by remember { mutableStateOf(0.dp) }
    // Opened from the chat, back already returns to it, so nothing is pinned: Leave Chat sits at
    // the foot of the page instead.
    val clearance = if (openedFromChat && privateJoin == null) 0.dp else pinnedHeight
    val hazeState = rememberHazeState()

    CodeScaffold { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("group_profile_screen"),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The whole page, so the pinned bar and the top edge blur whatever scrolls under them.
                    .hazeSource(hazeState)
                    .verticalScroll(scrollState)
                    // At least a screen tall, so a short page can hold Leave Chat to the bottom.
                    .heightIn(min = maxHeight)
                    .padding(bottom = clearance),
            ) {
                ProfileHeader(
                    cover = group?.coverPicture,
                    access = group?.let { BlobAccessContext.ChatProfile(it.chatId) } ?: BlobAccessContext.Owned,
                    avatar = { modifier ->
                        ChatSubjectAvatar(subject = group, modifier = modifier)
                    },
                    title = group?.title.orEmpty(),
                    // No member count under the name; the avatar row and body carry the group.
                    subtitle = null,
                    body = group?.description?.takeIf { it.isNotBlank() },
                    onCover = {
                        val mutedLabel = rememberMutedLabel(state.viewerState)
                        if (isMember && mutedLabel != null) {
                            // On the cover's bottom edge so a late mute state never shifts the
                            // header, and the action row keeps its width for Edit Group and Share.
                            ProfileStatusChip(
                                modifier = Modifier.weight(1f, fill = false),
                                overCover = true,
                                icon = Icons.Outlined.NotificationsOff,
                                text = mutedLabel,
                                compactText = stringResource(R.string.label_muted),
                            )
                        }
                    },
                    actions = {
                        // Server-computed, and absent for a non-member, so an unresolved viewer
                        // state reads as "may not edit" rather than a permission re-derived here.
                        if (state.viewerState?.permissions?.canEdit == true) {
                            ProfileActionButton(
                                text = stringResource(R.string.title_editGroup),
                                onClick = { flowNavigator.navigateTo(ChatStep.EditGroup) },
                            )
                        }
                        // Always offered: the link is the chat's id, so a non-member can hand it
                        // out as well as a member can.
                        ProfileActionButton(
                            icon = ImageVector.vectorResource(R.drawable.ic_share_os),
                            contentDescription = stringResource(R.string.action_share),
                            onClick = {
                                viewModel.dispatchEvent(
                                    ChatViewModel.Event.InviteSheetOpened(GroupInviteSheetSource.PROFILE)
                                )
                                // A host sees the three-way sheet (frame 1a); a member without
                                // can_invite keeps the single join link.
                                if (canInvite) {
                                    inviteHost.presentHostSheet(
                                        groupName = group?.groupTitle.orEmpty(),
                                        onShareInviteLink = { navigator.openInviteLinkShare() },
                                        onShareJoinLink = { navigator.openGroupInvite() },
                                        onManageInvites = { flowNavigator.navigateTo(ChatStep.ManageInvites) },
                                    )
                                } else {
                                    navigator.openGroupInvite()
                                }
                            },
                        )
                    },
                )

                if (group?.isPrivate == true) {
                    PrivateGroupNote(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x3),
                        summary = stringResource(
                            R.string.subtitle_privateGroupMembers,
                            pluralStringResource(
                                R.plurals.subtitle_memberCount,
                                group.memberCount.toInt(),
                                group.memberCount.toString(),
                            ),
                        ),
                        detail = stringResource(R.string.subtitle_hostApprovesJoins),
                    )
                }

                // The host's way into Join Requests; gone when nobody is waiting.
                AnimatedVisibility(
                    visible = state.isLobbyHost && state.joinRequests.isNotEmpty(),
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    var shownCount by remember { mutableStateOf(state.joinRequests.size) }
                    if (state.joinRequests.isNotEmpty()) shownCount = state.joinRequests.size
                    JoinRequestsCard(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x4),
                        count = shownCount,
                        onClick = { flowNavigator.navigateTo(ChatStep.JoinRequests) },
                    )
                }

                val unknownName = stringResource(R.string.title_unnamedUser)
                val chatters = remember(state.chatters, unknownName) {
                    state.chatters.mapNotNull { chatter ->
                        val profile = chatter.userProfile
                        val userId = profile.userId ?: return@mapNotNull null
                        ChattingGridItem(
                            userId = userId,
                            name = chatterName(profile.displayName, profile.username, unknownName),
                            picture = profile.profilePicture,
                            isHost = chatter.isCreator,
                        )
                    }
                }
                // A private group's members see its roster instead of a sample: creator first,
                // then the newest joins, with the count opening the full list (node 11210:68).
                // Until the server lists the roster, the grid shows who this device knows,
                // without a count: there is no full list to open.
                val hasRoster = state.members.isNotEmpty()
                val gridMembers = if (hasRoster) state.members else state.knownMembers
                val members = remember(gridMembers, unknownName) {
                    gridMembers.take(MembersGridLimit).map { member ->
                        val profile = member.userProfile
                        ChattingGridItem(
                            userId = member.userId,
                            name = chatterName(profile.displayName, profile.username, unknownName),
                            picture = profile.profilePicture,
                            isHost = member.userId == group?.creator,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = group?.isPrivate == true && members.isNotEmpty(),
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    GroupChattingGrid(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x6),
                        chatters = members,
                        title = stringResource(R.string.title_members),
                        count = if (hasRoster) {
                            (group?.memberCount ?: state.members.size.toLong()).toString()
                        } else null,
                        onShowAll = { flowNavigator.navigateTo(ChatStep.Members) },
                        onOpen = { item ->
                            val member = gridMembers.firstOrNull { it.userId == item.userId }
                                ?: return@GroupChattingGrid
                            flowNavigator.navigateTo(
                                ChatStep.Profile(
                                    contact = ChatParticipant.TipUser(item.userId, member.userProfile),
                                    origin = ProfileOrigin.GroupMember,
                                )
                            )
                        },
                    )
                }

                // The sample lands after the header; opening the grid in place moves the
                // requirements card down with it rather than jumping it.
                AnimatedVisibility(
                    visible = group != null && isChattingGridVisible(group.isPrivate, state.chatters),
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    GroupChattingGrid(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x6),
                        chatters = chatters,
                        onOpen = { item ->
                            val chatter = state.chatters.firstOrNull { it.userProfile.userId == item.userId }
                                ?: return@GroupChattingGrid
                            // Straight to the person's profile: resolving a DM destination first
                            // would send a tap on a portrait to a chat.
                            flowNavigator.navigateTo(
                                ChatStep.Profile(
                                    contact = ChatParticipant.TipUser(item.userId, chatter.userProfile),
                                    origin = ProfileOrigin.GroupMember,
                                )
                            )
                        },
                    )
                }

                AnimatedVisibility(
                    visible = requirements != null && namesReady && group?.isPrivate != true,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    requirements ?: return@AnimatedVisibility
                    val soleToken = requirements.soleToken
                    val soleTokenCurrency = soleToken?.let { tokens[it.bytes] }
                    // With the token named in its own row, Join and Chat state bare amounts.
                    val namesToken = soleTokenCurrency == null
                    BalanceRequirementsCard(
                        // "Your Balance" arrives with the standing, after the rules.
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x6)
                            .animateContentSize(),
                        communityCurrency = soleTokenCurrency?.let { currency ->
                            {
                                TokenIconWithName(
                                    modifier = Modifier.padding(end = CodeTheme.dimens.staticGrid.x1),
                                    tokenName = currency.name,
                                    tokenImage = currency.imageUrl,
                                    imageSize = 24.dp,
                                    textStyle = CodeTheme.typography.textMedium,
                                    spacing = CodeTheme.dimens.staticGrid.x2,
                                )
                            }
                        },
                        // The same route and analytics source as a token tapped in the transcript.
                        onCommunityCurrencyClick = {
                            soleToken?.let { navigator.push(AppRoute.Token.Info(it, TokenInfoEntry.Chat)) }
                        },
                        join = requirements.join?.let { holdingLabel(it, tokens, namesToken) },
                        chat = requirements.chat?.let { holdingLabel(it, tokens, namesToken) },
                        yourBalance = (requirements.join ?: requirements.chat)?.let { rule ->
                            standing?.yourBalance?.let { holdingLabel(it, rule.mints.firstOrNull()?.let { m -> Mint(m.bytes) }, tokens) }
                        },
                    )
                }
                if (openedFromChat && isMember) {
                    // Takes up whatever the page leaves over; nothing once it scrolls.
                    Spacer(modifier = Modifier.weight(1f))
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x6),
                        buttonState = ButtonState.Subtle,
                        text = stringResource(R.string.action_leaveChat),
                        isLoading = state.leaving,
                        enabled = !state.leaving,
                        onClick = { viewModel.dispatchEvent(ChatViewModel.Event.LeaveChat) },
                    )
                }
                Box(
                    modifier = Modifier
                        .then(if (openedFromChat) Modifier.navigationBarsPadding() else Modifier)
                        .padding(bottom = CodeTheme.dimens.staticGrid.x6)
                )
            }

            ProfileTopScrollEdge(hazeState = hazeState, scrollState = scrollState)
            if (openedFromChat) {
                BottomScrollEdge(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hazeState = hazeState,
                    scrollState = scrollState,
                )
            }

            AppBarWithTitle(
                onBackIconClicked = { flowNavigator.back() },
                hazeState = hazeState,
                endContent = {
                    val density = LocalDensity.current
                    var anchorHeight by remember { mutableStateOf(0.dp) }
                    Box(modifier = Modifier.onSizeChanged { anchorHeight = with(density) { it.height.toDp() } }) {
                        CircularIconButton(hazeState = hazeState, onClick = { menuOpen = true }) { size ->
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.action_moreProfileActions),
                                tint = Color.White,
                                modifier = Modifier.requiredSize(size),
                            )
                        }
                        GroupProfileMenu(
                            expanded = menuOpen,
                            items = groupProfileMenuActions(
                                isMember,
                                canModerate = canModerate,
                            ),
                            isPrivate = group?.isPrivate == true,
                            anchorHeight = anchorHeight,
                            onDismiss = { menuOpen = false },
                            onSilenced = { flowNavigator.navigateTo(ChatStep.SilencedMembers) },
                            onRemoved = { flowNavigator.navigateTo(ChatStep.RemovedMembers) },
                            onItem = { item ->
                                when (item) {
                                    GroupProfileMenuAction.Moderation -> Unit
                                    GroupProfileMenuAction.Encryption -> navigator.push(
                                        if (group?.isPrivate == true) {
                                            AppRoute.Messaging.E2eePrivateGroupInfo
                                        } else {
                                            AppRoute.Messaging.E2eeGroupInfo
                                        }
                                    )
                                    // The picker handles muting and unmuting both; pushed on the
                                    // outer navigator, as it is a top-level route.
                                    GroupProfileMenuAction.Mute -> state.chatId?.let { chatId ->
                                        navigator.push(AppRoute.Messaging.MuteChat(chatId, ChatType.GROUP))
                                    }
                                    GroupProfileMenuAction.Report -> state.chatId?.let { chatId ->
                                        navigator.push(AppRoute.Messaging.Report(ReportSubject.Chat(chatId)))
                                    }
                                }
                            },
                        )
                    }
                },
            )

            // Open Chat in every standing, as in the design: a viewer short of a minimum is told
            // so, and buys, on the chat's own gate.
            if (privateJoin != null) {
                PrivateJoinBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    join = privateJoin,
                    hostName = state.hostName,
                    lobbyCall = state.lobbyCall,
                    onRequest = { viewModel.dispatchEvent(ChatViewModel.Event.RequestToJoin) },
                    onCancel = { viewModel.dispatchEvent(ChatViewModel.Event.CancelJoinRequest) },
                    hazeState = hazeState,
                    onHeightChanged = { pinnedHeight = it },
                )
            } else if (!openedFromChat) {
                ProfilePinnedActionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    text = stringResource(R.string.action_openChat),
                    onClick = { openChat() },
                    secondaryText = if (isMember) stringResource(R.string.action_leaveChat) else null,
                    onSecondaryClick = { viewModel.dispatchEvent(ChatViewModel.Event.LeaveChat) },
                    isSecondaryLoading = state.leaving,
                    hazeState = hazeState,
                    onHeightChanged = { pinnedHeight = it },
                )
            }
        }
    }
}

/**
 * The ⋯ button's menu: Encryption and Mute, the host's Moderation row, then a divider and the red
 * Report. Moderation swaps the menu in place for Silenced and Removed; see [SubmenuDropdownMenu].
 */
@Composable
internal fun GroupProfileMenu(
    expanded: Boolean,
    items: List<GroupProfileMenuAction>,
    isPrivate: Boolean,
    anchorHeight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onItem: (GroupProfileMenuAction) -> Unit,
    onSilenced: () -> Unit,
    onRemoved: () -> Unit,
) {
    SubmenuDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        submenuTitle = stringResource(R.string.title_moderation),
        offset = DpOffset(x = 0.dp, y = -anchorHeight),
        root = { openSubmenu ->
            items.forEachIndexed { index, item ->
                if (index > 0 && items[index - 1].isDestructive != item.isDestructive) {
                    HorizontalDivider(color = CodeTheme.colors.divider)
                }
                if (item == GroupProfileMenuAction.Moderation) {
                    SubmenuRow(
                        label = stringResource(item.labelRes()),
                        icon = item.icon(isPrivate),
                        onClick = openSubmenu,
                    )
                    return@forEachIndexed
                }
                val tint = if (item.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain
                DropdownMenuItem(
                    leadingIcon = { Icon(imageVector = item.icon(isPrivate), contentDescription = null, tint = tint) },
                    text = {
                        Text(
                            text = stringResource(item.labelRes()),
                            style = CodeTheme.typography.textSmall,
                            color = tint,
                        )
                    },
                    onClick = {
                        onDismiss()
                        onItem(item)
                    },
                )
            }
        },
        submenu = {
            ModerationEntry(R.string.title_silencedMembers, Icons.AutoMirrored.Outlined.VolumeOff) {
                onDismiss()
                onSilenced()
            }
            ModerationEntry(R.string.title_removedMembers, Icons.Outlined.PersonOff) {
                onDismiss()
                onRemoved()
            }
        },
    )
}

@Composable
private fun ModerationEntry(@androidx.annotation.StringRes label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = CodeTheme.colors.textMain) },
        text = {
            Text(
                text = stringResource(label),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
            )
        },
        onClick = onClick,
    )
}

/** How long the requirement amounts wait for their token's name before showing without it. */
private val RuleNameHold = 1500.milliseconds

/** Four rows of portraits; the count beside the title opens the rest. */
internal const val MembersGridLimit = 16

/**
 * A line behind a lock under a private group's description, with an optional second line. A
 * member count reads "Private group · 48 members" over "The host approves who joins"; an invite
 * link replaces both ([summary] alone, nodes 12846:59 and 12846:126).
 */
@Composable
internal fun PrivateGroupNote(summary: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(14.dp),
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
            Text(
                text = summary,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        if (detail != null) {
            Text(
                text = detail,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * A private group's pinned action for a non-member: Request to Join, or a disabled Request Sent
 * with Cancel Request under it and how long the request has waited above it.
 */
@Composable
internal fun PrivateJoinBar(
    join: PrivateGroupJoin,
    hostName: String?,
    lobbyCall: LobbyCall?,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    hazeState: HazeState,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (join) {
        PrivateGroupJoin.Request -> ProfilePinnedActionBar(
            modifier = modifier,
            text = stringResource(R.string.action_requestToJoin),
            onClick = onRequest,
            isLoading = lobbyCall == LobbyCall.Request,
            enabled = lobbyCall == null,
            hazeState = hazeState,
            onHeightChanged = onHeightChanged,
        )
        is PrivateGroupJoin.Requested -> {
            // Ticks so "2m ago" does not stand still while the screen stays up.
            val now by produceState(Clock.System.now()) {
                while (true) {
                    delay(1.minutes)
                    value = Clock.System.now()
                }
            }
            // "Request Sent" for the first minute, then how long the request has waited.
            val label = when (val age = RequestAge.of(join.at, now)) {
                RequestAge.JustNow -> stringResource(R.string.action_requestSent)
                is RequestAge.Minutes -> stringResource(R.string.action_requestedMinutesAgo, age.count)
                is RequestAge.Hours -> stringResource(R.string.action_requestedHoursAgo, age.count)
                is RequestAge.Days -> stringResource(R.string.action_requestedDaysAgo, age.count)
            }
            ProfilePinnedActionBar(
                modifier = modifier,
                text = label,
                onClick = {},
                enabled = false,
                above = {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset),
                        text = if (hostName != null) {
                            stringResource(R.string.subtitle_requestPendingHost, hostName)
                        } else {
                            stringResource(R.string.subtitle_requestPendingHostUnnamed)
                        },
                        style = CodeTheme.typography.caption,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                },
                secondaryText = stringResource(R.string.action_cancelRequest),
                onSecondaryClick = onCancel,
                isSecondaryLoading = lobbyCall == LobbyCall.Cancel,
                hazeState = hazeState,
                onHeightChanged = onHeightChanged,
            )
        }
    }
}

/** Node 11205:68 — the host's Join Requests row: title, "3 people are waiting", grey count, chevron. */
@Composable
private fun JoinRequestsCard(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White05)
            .clickable(onClick = onClick)
            .padding(horizontal = CodeTheme.dimens.grid.x3, vertical = CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title_joinRequests),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = pluralStringResource(R.plurals.subtitle_joinRequestsWaiting, count, count),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }
        // Grey rather than the unread badge's colour: a count of people waiting, not unread messages.
        Box(
            modifier = Modifier
                .heightIn(min = 20.dp)
                .clip(CircleShape)
                .background(White10)
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )
    }
}
