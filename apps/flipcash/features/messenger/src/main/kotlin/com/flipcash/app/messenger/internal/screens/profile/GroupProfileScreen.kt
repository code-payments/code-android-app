package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.analytics.GroupGateFunding
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
import com.flipcash.app.messenger.internal.screens.openGroupInvite
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
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.extraLarge
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.theme.CodeScaffold
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.filterIsInstance

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
    // (AppRoute.Messaging.Chat.openOnProfile) has nothing underneath, so the transcript takes the
    // profile's place and back from it leaves the flow, as it would from any chat.
    fun openChat() {
        if (flowNavigator.canGoBack) {
            flowNavigator.back()
        } else {
            flowNavigator.navigateTo(ChatStep.Conversation, popCurrent = true)
        }
    }

    // A join that went through lands on the chat.
    // The success flag is sticky on the view model, so only a join started from this screen counts:
    // someone who joined from the gate and opens the profile afterwards must not be bounced.
    var joinedHere by remember { mutableStateOf(false) }
    LaunchedEffect(state.joinProgress.loading) {
        if (state.joinProgress.loading) joinedHere = true
    }
    LaunchedEffect(state.joinProgress.success, joinedHere) {
        if (state.joinProgress.success && joinedHere) openChat()
    }

    val standing = state.profileStanding
    val cta = standing?.cta ?: GroupProfileCta.None
    val requirements = remember(group?.rules) { GroupBalanceRequirements.from(group?.rules) }
    val tokens = state.ruleTokens

    var menuOpen by remember { mutableStateOf(false) }
    var pinnedHeight by remember { mutableStateOf(0.dp) }
    val hasPinned = cta != GroupProfileCta.None || isMember
    val clearance = if (hasPinned) pinnedHeight else 0.dp
    val hazeState = rememberHazeState()

    CodeScaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // The page fades out under the app bar. At the bottom it runs on under the pinned
                    // bar, which blurs it, as on iOS.
                    .verticalScrollStateGradient(
                        scrollState = scrollState,
                        showAtEnd = false,
                        startInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    )
                    .hazeSource(hazeState)
                    .verticalScroll(scrollState)
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
                                navigator.openGroupInvite()
                            },
                        )
                    },
                )

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
                if (group != null && isChattingGridVisible(group.isPrivate, state.chatters)) {
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

                val soleToken = requirements?.soleToken
                val soleTokenCurrency = soleToken?.let { tokens[it.bytes] }
                if (soleToken != null && soleTokenCurrency != null) {
                    GroupTokenCard(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x6),
                        name = soleTokenCurrency.name,
                        imageUrl = soleTokenCurrency.imageUrl,
                        // The same route and analytics source as a token tapped in the transcript.
                        onClick = { navigator.push(AppRoute.Token.Info(soleToken, TokenInfoEntry.Chat)) },
                    )
                }

                if (requirements != null) {
                    BalanceRequirementsCard(
                        modifier = Modifier
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(top = CodeTheme.dimens.staticGrid.x6),
                        join = requirements.join?.let { holdingLabel(it, tokens) },
                        chat = requirements.chat?.let { holdingLabel(it, tokens) },
                        yourBalance = (requirements.join ?: requirements.chat)?.let { rule ->
                            standing?.yourBalance?.let { holdingLabel(it, rule.mints.firstOrNull()?.let { m -> Mint(m.bytes) }, tokens) }
                        },
                    )
                }
                Box(modifier = Modifier.padding(bottom = CodeTheme.dimens.staticGrid.x6))
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
                            items = groupProfileMenuActions(isMember),
                            anchorHeight = anchorHeight,
                            onDismiss = { menuOpen = false },
                            onItem = { item ->
                                when (item) {
                                    GroupProfileMenuAction.Encryption ->
                                        navigator.push(AppRoute.Messaging.E2eeGroupInfo)
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

            if (hasPinned) {
                val bar = (cta as? GroupProfileCta.BuyToJoin)?.requirement
                    ?: (cta as? GroupProfileCta.BuyToChat)?.requirement
                val buyMint = bar?.mints?.firstOrNull()?.let { Mint(it.bytes) }?.takeUnless { it == Mint.usdf }
                val shortfall = (standing?.shortfall ?: bar?.amount)?.formatted(Fiat.FormattingRule.Truncated)
                val isJoin = cta is GroupProfileCta.BuyToJoin

                ProfilePinnedActionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    text = when (cta) {
                        GroupProfileCta.Join -> stringResource(R.string.action_join)
                        GroupProfileCta.OpenChat, GroupProfileCta.None -> stringResource(R.string.action_openChat)
                        is GroupProfileCta.BuyToJoin -> stringResource(R.string.action_buyToJoin, shortfall.orEmpty())
                        is GroupProfileCta.BuyToChat -> stringResource(R.string.action_buyToChat, shortfall.orEmpty())
                    },
                    // A member whose standing has not arrived has nothing to open yet.
                    enabled = cta != GroupProfileCta.None && !state.joinProgress.loading,
                    isLoading = state.joinProgress.loading,
                    onClick = {
                        when (cta) {
                            GroupProfileCta.Join -> viewModel.dispatchEvent(ChatViewModel.Event.JoinChat)
                            GroupProfileCta.OpenChat -> openChat()
                            is GroupProfileCta.BuyToJoin, is GroupProfileCta.BuyToChat -> {
                                if (buyMint == null) {
                                    viewModel.dispatchEvent(ChatViewModel.Event.GateFundingTapped(GroupGateFunding.ADD_CASH))
                                    viewModel.dispatchEvent(ChatViewModel.Event.PresentDepositOptions)
                                } else {
                                    viewModel.dispatchEvent(ChatViewModel.Event.GateFundingTapped(GroupGateFunding.BUY_TOKEN))
                                    navigator.push(AppRoute.Token.Info(buyMint, TokenInfoEntry.ChatGate))
                                }
                            }
                            GroupProfileCta.None -> Unit
                        }
                    },
                    above = if (bar != null) {
                        {
                            Text(
                                modifier = Modifier.padding(bottom = CodeTheme.dimens.staticGrid.x2),
                                text = stringResource(
                                    if (isJoin) R.string.label_groupRequiredToJoin else R.string.label_groupRequiredToChat,
                                    holdingLabel(bar, tokens),
                                ),
                                style = CodeTheme.typography.textSmall,
                                color = CodeTheme.colors.textSecondary,
                            )
                        }
                    } else null,
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
 * A rule's amount as the profile writes it: "$10", "$2.50", and "$10 of NYC" for a token other
 * than USDF whose name is known. A rule naming no mint, or the reserve, states the amount alone.
 */
@Composable
private fun holdingLabel(
    rule: ChatRuleRequirement.MinimumBalance,
    tokens: Map<List<Byte>, com.flipcash.app.messenger.internal.RuleCurrency>,
): String = holdingLabel(rule.amount, rule.mints.firstOrNull()?.let { Mint(it.bytes) }, tokens)

@Composable
private fun holdingLabel(
    amount: Fiat,
    mint: Mint?,
    tokens: Map<List<Byte>, com.flipcash.app.messenger.internal.RuleCurrency>,
): String {
    val formatted = amount.formatted(Fiat.FormattingRule.Truncated)
    val name = mint?.takeUnless { it == Mint.usdf }?.let { tokens[it.bytes] }?.name
        ?: return formatted
    return stringResource(R.string.label_chat_preview_cash_suffix, formatted, name)
}

/** The ⋯ button's menu: Encryption and Mute, then a divider and the red Report. */
@Composable
private fun GroupProfileMenu(
    expanded: Boolean,
    items: List<GroupProfileMenuAction>,
    anchorHeight: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onItem: (GroupProfileMenuAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = CodeTheme.colors.brandLight,
        shape = CodeTheme.shapes.extraLarge,
        offset = DpOffset(x = 0.dp, y = -anchorHeight),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0 && items[index - 1].isDestructive != item.isDestructive) {
                HorizontalDivider(color = CodeTheme.colors.divider)
            }
            val tint = if (item.isDestructive) CodeTheme.colors.errorText else CodeTheme.colors.textMain
            DropdownMenuItem(
                leadingIcon = { Icon(imageVector = item.icon(), contentDescription = null, tint = tint) },
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
    }
}

/**
 * The one token a group's requirements name, as a tappable row that opens its info screen. No price
 * line: the rule token the profile already holds carries a name and an icon, not a quote.
 */
@Composable
private fun GroupTokenCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.title_groupProfileToken),
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(White05)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenIconWithName(
                modifier = Modifier.weight(1f),
                tokenName = name,
                tokenImage = imageUrl,
                imageSize = 32.dp,
                textStyle = CodeTheme.typography.textMedium,
                spacing = CodeTheme.dimens.staticGrid.x2,
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = CodeTheme.colors.textSecondary,
            )
        }
    }
}
