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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.core.chat.ChatStep
import com.flipcash.app.core.chat.ProfileOrigin
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Node 11210:70 — every member of a private group: the creator pinned first with a Host tag, then
 * the newest joins. Tap opens the person's profile; there is no remove action.
 *
 * Reads the flow's shared [ChatViewModel], which holds the roster from GroupRosters, so this list
 * and the profile's grid show the same people.
 */
@Composable
internal fun MembersScreen(viewModel: ChatViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val flowNavigator = rememberFlowNavigator<ChatStep, Parcelable>()
    val group = state.subject as? ChatSubject.Group

    // RosterUpdate can skip versions, so the list refetches whenever it comes on screen.
    LaunchedEffect(Unit) { viewModel.dispatchEvent(ChatViewModel.Event.MembersOpened) }

    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(1.minutes)
            value = Clock.System.now()
        }
    }

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
                MemberRow(
                    member = member,
                    name = chatterName(member.userProfile.displayName, member.userProfile.username, unknownName),
                    isHost = member.userId == group?.creator,
                    age = member.joinedAt?.let { JoinAge.of(it, now) },
                    onOpen = {
                        flowNavigator.navigateTo(
                            ChatStep.Profile(
                                contact = ChatParticipant.TipUser(member.userId, member.userProfile),
                                origin = ProfileOrigin.GroupMember,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: ChatMember,
    name: String,
    isHost: Boolean,
    age: JoinAge?,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            } else if (age != null) {
                Text(
                    text = joinAgeLabel(age),
                    style = CodeTheme.typography.caption,
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
