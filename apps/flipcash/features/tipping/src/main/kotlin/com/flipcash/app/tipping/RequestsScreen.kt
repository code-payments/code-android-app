package com.flipcash.app.tipping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.tipping.internal.HostedRequest
import com.flipcash.app.tipping.internal.RequestsViewModel
import com.flipcash.app.tipping.internal.SentRequest
import com.flipcash.features.tipping.R
import com.flipcash.shared.chat.RequestAge
import com.flipcash.shared.chat.ui.ChatListRow
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.chat.ui.SubtitleText
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.text.SectionHeader
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Join requests in both directions, behind the Requests button on the Chats header: private groups
 * the viewer hosts with people waiting ("Waiting on you"), and groups the viewer asked to join
 * ("Sent"). Sent knows only about requests made on this device; the server can't list them.
 */
@Composable
fun RequestsScreen() {
    val viewModel = hiltViewModel<RequestsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current

    CodeScaffold(
        topBar = {
            AppBarWithTitle(
                title = stringResource(R.string.title_requests),
                titleAlignment = Alignment.CenterHorizontally,
                onBackIconClicked = { navigator.pop() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("requests_list"),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
        ) {
            if (state.isLoaded && state.isEmpty) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .fillParentMaxSize()
                            .padding(bottom = padding.calculateBottomPadding()),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Join Requests' empty state, without its Share Group button: there's no
                        // one group to share.
                        Column(
                            modifier = Modifier.padding(horizontal = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                        ) {
                            Text(
                                text = stringResource(R.string.title_noRequests),
                                style = CodeTheme.typography.textLarge,
                                color = CodeTheme.colors.textMain,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.subtitle_noRequests),
                                style = CodeTheme.typography.textSmall,
                                color = CodeTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            if (state.hosted.isNotEmpty()) {
                item(key = "hosted_header") {
                    SectionHeader(
                        modifier = Modifier.animateItem(),
                        title = stringResource(R.string.header_waitingOnYou),
                    )
                }
                itemsIndexed(state.hosted, key = { _, it -> "hosted_${it.chat.chatId}" }) { index, request ->
                    HostedRequestRow(
                        modifier = Modifier.animateItem(),
                        request = request,
                        showDivider = index < state.hosted.lastIndex,
                    ) {
                        navigator.push(
                            AppRoute.Messaging.Chat(
                                ChatIdentifier.ByChatId(request.chat.chatId),
                                openOnJoinRequests = true,
                            )
                        )
                    }
                }
            }

            if (state.sent.isNotEmpty()) {
                item(key = "sent_header") {
                    SectionHeader(
                        modifier = Modifier.animateItem(),
                        title = stringResource(R.string.header_sentRequests),
                    )
                }
                itemsIndexed(state.sent, key = { _, it -> "sent_${it.chat.chatId}" }) { index, request ->
                    SentRequestRow(
                        modifier = Modifier.animateItem(),
                        request = request,
                        showDivider = index < state.sent.lastIndex,
                    ) {
                        // The group's profile, where Cancel Request lives: the viewer isn't a member.
                        navigator.push(
                            AppRoute.Messaging.Chat(
                                ChatIdentifier.ByChatId(request.chat.chatId),
                                openOnProfile = true,
                            )
                        )
                    }
                }
            }
        }
    }
}

/** A hosted group with people waiting: the count is secondary, like Archived's, not an unread badge. */
@Composable
private fun HostedRequestRow(
    request: HostedRequest,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RequestRow(
        modifier = modifier.testTag("requests_hosted_row"),
        chat = request.chat,
        subtitle = pluralStringResource(R.plurals.subtitle_joinRequestsWaiting, request.count, request.count),
        count = request.count,
        showDivider = showDivider,
        onClick = onClick,
    )
}

@Composable
private fun SentRequestRow(
    request: SentRequest,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RequestRow(
        modifier = modifier.testTag("requests_sent_row"),
        chat = request.chat,
        subtitle = requestedAgo(request.requestedAt),
        count = null,
        showDivider = showDivider,
        onClick = onClick,
    )
}

@Composable
private fun RequestRow(
    chat: ConversationReference,
    subtitle: String,
    count: Int?,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ChatListRow(
        modifier = modifier,
        avatar = {
            ContactAvatar(
                image = chat.image,
                displayName = chat.name.orEmpty(),
                access = chat.avatarAccess,
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.staticGrid.x8)
                    .clip(CircleShape),
            )
        },
        title = {
            Text(
                modifier = Modifier.weight(1f),
                text = chat.name.orEmpty(),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = { SubtitleText(subtitle) },
        endAction = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
            ) {
                if (count != null) {
                    Text(
                        text = count.toString(),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = CodeTheme.colors.textSecondary,
                )
            }
        },
        showDivider = showDivider,
        onClick = onClick,
    )
}

/** "Requested 2h ago", redrawn each minute, the only granularity it shows. */
@Composable
private fun requestedAgo(at: Instant): String {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1.minutes)
            now = Clock.System.now()
        }
    }
    return when (val age = RequestAge.of(at, now)) {
        RequestAge.JustNow -> stringResource(R.string.title_requestedJustNow)
        is RequestAge.Minutes -> stringResource(R.string.action_requestedMinutesAgo, age.count)
        is RequestAge.Hours -> stringResource(R.string.action_requestedHoursAgo, age.count)
        is RequestAge.Days -> if (age.count == 1L) {
            stringResource(R.string.title_requestedYesterday)
        } else {
            stringResource(R.string.action_requestedDaysAgo, age.count)
        }
    }
}
