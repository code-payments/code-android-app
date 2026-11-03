package com.flipcash.app.messenger.internal.screens.profile

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.MemberActions
import com.flipcash.app.messenger.internal.MemberModerationViewModel
import com.flipcash.app.messenger.internal.memberActions
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SwipeAction
import com.getcode.ui.components.SwipeActionRow
import com.getcode.ui.components.SwipeRevealGroup
import com.getcode.ui.components.rememberSwipeRevealGroup
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.resources.icons.SpeakerSlash

/**
 * Node 11210:70 — every member of a private group: the creator pinned first with a Host tag, then
 * the newest joins. Tap opens the person's member sheet (S3).
 *
 * A host gets a trailing swipe on each row they may act on (S1): Restrict, or Lift for a
 * restricted member, and Remove. One row open at a time, as on the chat list. A restricted
 * member's row says so in place of when they joined.
 *
 * Reads the flow's shared [ChatViewModel], which holds the roster from GroupRosters, so this list
 * and the profile's grid show the same people.
 */
@Composable
internal fun MembersScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val group = state.subject as? ChatSubject.Group
    val navigator = LocalCodeNavigator.current
    val moderation = flowSharedViewModel<MemberModerationViewModel>()
    val restrictions by moderation.restrictions.collectAsStateWithLifecycle()
    val viewerId = LocalUserManager.current?.accountId
    val isHost = canModerate(state.subject, state.viewerState)
    val grants = state.viewerState?.permissions?.moderation

    // RosterUpdate can skip versions, so the list refetches whenever it comes on screen.
    LaunchedEffect(Unit) { viewModel.dispatchEvent(ChatViewModel.Event.MembersOpened) }
    LaunchedEffect(state.chatId, isHost) {
        val chatId = state.chatId
        if (isHost && chatId != null) moderation.loadRestrictions(chatId)
    }

    val now by rememberMinuteClock(restrictions)
    // One row open at a time: swiping another row closes the one left revealed.
    val revealGroup = rememberSwipeRevealGroup()

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_members),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { flowNavigator.back() },
            )
        },
    ) { innerPadding ->
        val unknownName = stringResource(R.string.title_unnamedUser)
        val count = (group?.memberCount ?: state.members.size.toLong()).toInt()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x4),
        ) {
            item {
                Text(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(top = CodeTheme.dimens.grid.x2, bottom = CodeTheme.dimens.grid.x1),
                    text = pluralStringResource(R.plurals.header_memberCount, count, count),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                )
            }
            items(state.members, key = { it.userId }) { member ->
                val name = chatterName(member.userProfile.displayName, member.userProfile.username, unknownName)
                val silence = restrictions[member.userId]
                val actions = memberActions(
                    viewerId = viewerId,
                    memberId = member.userId,
                    creatorId = group?.creator,
                    isMember = group?.isMember == true,
                    canSilence = grants?.canSilence == true,
                    canBan = grants?.canBan == true,
                    silence = silence,
                    now = now,
                )
                val firstName = name.substringBefore(' ').ifEmpty { name }
                val chatId = state.chatId
                val groupTitle = group?.title.orEmpty()
                MemberSwipeRow(
                    actions = actions,
                    stateKey = member.userId,
                    revealGroup = revealGroup,
                    onRestrict = {
                        chatId ?: return@MemberSwipeRow
                        navigator.push(AppRoute.Messaging.SilenceUser(chatId, member.userId, firstName, groupTitle))
                    },
                    onLift = {
                        chatId ?: return@MemberSwipeRow
                        moderation.lift(chatId, member.userId, name)
                    },
                    onRemove = {
                        chatId ?: return@MemberSwipeRow
                        moderation.confirmRemove(chatId, member.userId, firstName, groupTitle)
                    },
                ) {
                    MemberRow(
                        member = member,
                        name = name,
                        isHost = member.userId == group?.creator,
                        age = member.joinedAt?.let { JoinAge.of(it, now) },
                        restriction = if (actions.showsRestriction) restrictionLabel(silence, now) else null,
                        onOpen = {
                            flowNavigator.navigateTo(
                                memberTapStep(
                                    participant = ChatParticipant.TipUser(member.userId, member.userProfile),
                                    origin = ProfileOrigin.GroupMember,
                                    viewerId = viewerId,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun MemberRow(
    member: ChatMember,
    name: String,
    isHost: Boolean,
    age: JoinAge?,
    /** "Restricted · 6h 12m left", shown to a host in place of [age]. */
    restriction: String? = null,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque, so the swipe's actions behind the row show only where it has moved off them.
            .background(CodeTheme.colors.background)
            .clickable(onClick = onOpen)
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        ContactAvatar(userProfile = member.userProfile, modifier = Modifier.size(40.dp).clip(CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isHost) {
                Text(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    text = stringResource(R.string.label_host),
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textMain,
                )
            } else if (restriction != null) {
                Text(
                    text = restriction,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.warning,
                    maxLines = 1,
                )
            } else if (age != null) {
                Text(
                    text = joinAgeLabel(age),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun joinAgeLabel(age: JoinAge): String = when (age) {
    JoinAge.JustNow -> stringResource(R.string.subtitle_joinedJustNow)
    is JoinAge.Minutes -> stringResource(R.string.subtitle_joinedMinutesAgo, age.count)
    is JoinAge.Hours -> stringResource(R.string.subtitle_joinedHoursAgo, age.count)
    JoinAge.Yesterday -> stringResource(R.string.subtitle_joinedYesterday)
    is JoinAge.Days -> stringResource(R.string.subtitle_joinedDaysAgo, age.count)
    JoinAge.LastWeek -> stringResource(R.string.subtitle_joinedLastWeek)
    is JoinAge.Weeks -> stringResource(R.string.subtitle_joinedWeeksAgo, age.count)
    JoinAge.LastMonth -> stringResource(R.string.subtitle_joinedLastMonth)
    is JoinAge.Months -> stringResource(R.string.subtitle_joinedMonthsAgo, age.count)
}

/**
 * S1's trailing swipe on a Members row: Restrict (or Lift) inside, Remove outside, each only when
 * [actions] allows it, and no swipe at all when it allows nothing. Tonal, as the chat list's
 * Mute and Archive are.
 *
 * [SwipeActionRow] fires its outermost action on a full swipe. Every action here resets instead
 * of settling, so a full swipe opens that action's sheet or confirm and the row springs back,
 * never acting on its own and never left open under the sheet.
 */
@Composable
internal fun MemberSwipeRow(
    actions: MemberActions,
    stateKey: Any,
    revealGroup: SwipeRevealGroup?,
    onRestrict: () -> Unit,
    onLift: () -> Unit,
    onRemove: () -> Unit,
    /** Opens the row on its first frame, for screenshots. */
    initiallyRevealed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val restrictLabel = stringResource(R.string.content_description_restrictMember)
    val liftLabel = stringResource(R.string.content_description_liftRestriction)
    val removeLabel = stringResource(R.string.content_description_removeMember)
    // Labels drawn under the swipe pills: the member sheet's titles, with Remove
    // shortened since the row is already in the group.
    val restrictTitle = stringResource(R.string.action_silenceMemberShort)
    val liftTitle = stringResource(R.string.action_liftRestriction)
    val removeTitle = stringResource(R.string.action_remove)
    val swipeActions = buildList {
        if (actions.restrict) {
            add(
                SwipeAction(
                    background = CodeTheme.colors.warning.copy(alpha = 0.15f),
                    onTriggered = onRestrict,
                    resetOnDismiss = true,
                    label = restrictTitle,
                ) {
                    SwipeIcon(SpeakerSlash, restrictLabel, CodeTheme.colors.warning)
                }
            )
        }
        if (actions.lift) {
            add(
                SwipeAction(
                    background = CodeTheme.colors.surfaceVariant,
                    onTriggered = onLift,
                    resetOnDismiss = true,
                    label = liftTitle,
                ) {
                    SwipeIcon(Icons.Outlined.LockOpen, liftLabel, CodeTheme.colors.textMain)
                }
            )
        }
        if (actions.remove) {
            add(
                SwipeAction(
                    background = CodeTheme.colors.error.copy(alpha = 0.15f),
                    onTriggered = onRemove,
                    resetOnDismiss = true,
                    label = removeTitle,
                ) {
                    SwipeIcon(Icons.Outlined.PersonRemove, removeLabel, CodeTheme.colors.error)
                }
            )
        }
    }
    if (swipeActions.isEmpty()) {
        content()
        return
    }
    SwipeActionRow(
        actions = swipeActions,
        // A swipe is out of reach with a screen reader, so the row offers the same actions there.
        modifier = Modifier.semantics {
            customActions = buildList {
                if (actions.restrict) add(CustomAccessibilityAction(restrictLabel) { onRestrict(); true })
                if (actions.lift) add(CustomAccessibilityAction(liftLabel) { onLift(); true })
                if (actions.remove) add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
            }
        },
        stateKey = stateKey,
        initiallyRevealed = initiallyRevealed,
        revealGroup = revealGroup,
        content = content,
    )
}

@Composable
private fun SwipeIcon(icon: ImageVector, label: String, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
    )
}
