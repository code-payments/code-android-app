package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.LocalUserManager
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.core.chat.ReportSubject
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.MemberModerationViewModel
import com.flipcash.app.messenger.internal.memberActions
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ViewerState
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.opencode.model.core.ID
import com.flipcash.app.messenger.internal.MentionDestination
import com.flipcash.services.models.chat.ChatMember
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * What tapping a group member's picture or row opens: their sheet, or for the viewer's own picture
 * the full profile, as it did before the sheet.
 */
internal fun memberTapStep(
    participant: ChatParticipant.TipUser,
    origin: ProfileOrigin,
    viewerId: ID?,
): ChatStep = if (participant.userId == viewerId) {
    ChatStep.Profile(contact = participant, origin = origin)
} else {
    ChatStep.MemberSheet(contact = participant, origin = origin)
}

/**
 * Where a tapped @mention lands. In a group transcript a mention of someone else opens their
 * sheet, with the avatar tap's origin so View Profile leads where the avatar's does; a DM's
 * mention keeps the full profile. A mention of the viewer never gets here: it resolves to
 * [MentionDestination.OwnTipCard].
 */
internal fun mentionStep(
    destination: MentionDestination.Profile,
    inGroup: Boolean,
    viewerId: ID?,
): ChatStep = if (inGroup) {
    memberTapStep(destination.participant, ProfileOrigin.Chat, viewerId)
} else {
    ChatStep.Profile(destination.participant, destination.origin)
}

/**
 * Whether [userId] is still in the group, as far as this device can tell. A private group's roster
 * answers; until it has loaded, and for a public group, which has none, they count as in, since
 * nothing says otherwise. A mention can name someone who has since left or been removed, and the
 * sheet gives the host nothing to do to them.
 */
internal fun isCurrentMember(subject: ChatSubject?, roster: List<ChatMember>, userId: ID): Boolean {
    val group = subject as? ChatSubject.Group ?: return true
    if (!group.isPrivate || roster.isEmpty()) return true
    return userId == group.creator || roster.any { it.userId == userId }
}

/**
 * Whether the viewer is a host of the group whose restrictions are worth reading: a member holding
 * either grant. The same gate the group profile puts on its Moderation rows.
 */
internal fun canModerate(subject: ChatSubject?, viewerState: ViewerState?): Boolean {
    val group = subject as? ChatSubject.Group ?: return false
    val moderation = viewerState?.permissions?.moderation
    return group.isMember == true && (moderation?.canSilence == true || moderation?.canBan == true)
}

/**
 * The current time, moved on once a minute, for the restriction countdowns. It restarts from the
 * current time whenever [key] changes, so a restriction written mid-minute isn't measured against
 * an older reading.
 */
@Composable
internal fun rememberMinuteClock(key: Any?): State<Instant> = produceState(Clock.System.now(), key) {
    value = Clock.System.now()
    while (true) {
        delay(1.minutes)
        value = Clock.System.now()
    }
}

/**
 * [ChatStep.MemberSheet] on the chat flow's sheet scene. Every way on closes the sheet first, then
 * opens the next thing from the scene's after-dismiss hook, so the push isn't the entry the sheet's
 * hide pops. [MemberModerationViewModel] is the flow's, so a Remove confirm tapped after the sheet
 * has gone still has a live scope.
 */
@Composable
internal fun MemberSheetScreen(contact: ChatParticipant, origin: ProfileOrigin) {
    val participant = contact as? ChatParticipant.TipUser ?: return
    val chatViewModel = flowSharedViewModel<ChatViewModel>()
    val moderation = flowSharedViewModel<MemberModerationViewModel>()
    val state by chatViewModel.stateFlow.collectAsStateWithLifecycle()
    val restrictions by moderation.restrictions.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    val viewerId = LocalUserManager.current?.accountId
    val now by rememberMinuteClock(restrictions)

    val group = state.subject as? ChatSubject.Group
    val chatId: ChatId? = state.chatId
    val isHost = canModerate(state.subject, state.viewerState)
    LaunchedEffect(chatId, isHost) {
        if (isHost && chatId != null) moderation.loadRestrictions(chatId)
    }

    val grants = state.viewerState?.permissions?.moderation
    val silence = restrictions[participant.userId]
    val actions = memberActions(
        viewerId = viewerId,
        memberId = participant.userId,
        creatorId = group?.creator,
        isMember = group?.isMember == true,
        targetIsMember = isCurrentMember(state.subject, state.members, participant.userId),
        canSilence = grants?.canSilence == true,
        canBan = grants?.canBan == true,
        silence = silence,
        now = now,
    )
    val name = participant.name ?: stringResource(R.string.title_unnamedUser)
    val firstName = name.substringBefore(' ').ifEmpty { name }
    val groupTitle = group?.title.orEmpty()

    MemberSheetContent(
        profile = participant.profile,
        name = name,
        handle = participant.handle,
        groupTitle = groupTitle,
        actions = actions,
        silence = silence,
        now = now,
        onViewProfile = {
            navigator.pendingSheetDismiss = {
                navigator.push(ChatStep.Profile(contact = participant, origin = origin))
            }
        },
        onReport = {
            navigator.pendingSheetDismiss = {
                navigator.push(AppRoute.Messaging.Report(ReportSubject.User(participant.userId)))
            }
        },
        onRestrict = {
            chatId ?: return@MemberSheetContent
            navigator.pendingSheetDismiss = {
                navigator.push(AppRoute.Messaging.SilenceUser(chatId, participant.userId, firstName, groupTitle))
            }
        },
        onLift = {
            chatId ?: return@MemberSheetContent
            moderation.lift(chatId, participant.userId, name)
            dismissSheet()
        },
        onRemove = {
            chatId ?: return@MemberSheetContent
            navigator.pendingSheetDismiss = {
                moderation.confirmRemove(chatId, participant.userId, firstName, groupTitle)
            }
        },
        onDismiss = dismissSheet,
    )
}
