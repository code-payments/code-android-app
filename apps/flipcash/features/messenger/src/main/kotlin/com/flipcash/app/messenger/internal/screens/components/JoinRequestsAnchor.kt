package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.flipcash.app.messenger.internal.screens.profile.RequestAge
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.LobbyMember
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.chatterName
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInputDefaults
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Nodes 11354:70 / 11354:81 / 11354:83 — the host's way into Join Requests from inside the chat: up
 * to three requesters' avatars, "3 people want to join" over their names (or "Jasper wants to join"
 * over the request's age), and a Review capsule. The whole card is the tap target.
 *
 * The ground is [com.flipcash.shared.chat.ui.ComposerReplyStrip]'s: the composer's ultra-thin haze
 * over the background with the divider hairline, so the transcript reads through it as it scrolls
 * under, and the same flat stand-in when there is no [HazeState] to sample.
 */
@Composable
internal fun JoinRequestsAnchor(
    requests: List<LobbyMember>,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val shape = CodeTheme.shapes.medium
    val ground = if (hazeState != null) {
        Modifier
            .hazeBlur(
                HazeInput.Sources(hazeState),
                HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background),
            )
            .background(ChatInputDefaults.ContainerColor)
    } else {
        Modifier.background(Color.White.copy(alpha = 0.1f))
    }

    val unknownName = stringResource(R.string.title_unnamedUser)
    val names = requests.map { chatterName(it.userProfile.displayName, it.userProfile.username, unknownName) }
    val andWord = stringResource(R.string.subtitle_namesAnd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(ground)
            .border(CodeTheme.dimens.border, CodeTheme.colors.divider, shape)
            .clickable(onClick = onReview)
            .heightIn(min = AnchorMinHeight)
            .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = CodeTheme.dimens.grid.x1)
            .testTag("join_requests_anchor"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
    ) {
        AvatarStack(requests.take(MaxAvatars))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (requests.size == 1) {
                    stringResource(R.string.title_wantsToJoin, names.first())
                } else {
                    pluralStringResource(R.plurals.title_peopleWantToJoin, requests.size, requests.size)
                },
                style = CodeTheme.typography.textMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (requests.size == 1) {
                    requestedAgo(requests.first())
                } else {
                    joinNames(names) { head, last -> "$head $andWord $last" }
                },
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Approve's capsule on Join Requests. The card is the target, so this one only shows where.
        CodeButton(
            text = stringResource(R.string.action_review),
            buttonState = ButtonState.Filled,
            shape = CircleShape,
            style = CodeTheme.typography.textSmall,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            overrideContentPadding = true,
            onClick = onReview,
        )
    }
}

/** Newest first, overlapping, each ringed in the background so the overlap reads as a cut. */
@Composable
private fun AvatarStack(members: List<LobbyMember>) {
    val ring = CodeTheme.colors.background
    Box {
        members.forEachIndexed { index, member ->
            ContactAvatar(
                userProfile = member.userProfile,
                modifier = Modifier
                    .offset(x = (AvatarSize - AvatarOverlap) * index)
                    // The newest sits on top of the ones after it.
                    .zIndex((members.size - index).toFloat())
                    .size(AvatarSize)
                    .border(AvatarRing, ring, CircleShape)
                    .padding(AvatarRing)
                    .clip(CircleShape),
            )
        }
        // The offsets don't take up width in a Box, so reserve what the stack spans.
        Box(Modifier.size(width = AvatarSize + (AvatarSize - AvatarOverlap) * (members.size - 1).coerceAtLeast(0), height = AvatarSize))
    }
}

@Composable
private fun requestedAgo(member: LobbyMember): String {
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(1.minutes)
            value = Clock.System.now()
        }
    }
    return when (val age = RequestAge.of(member.enteredAt, now)) {
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

/** "Jasper", "Jasper and Tam", "Jasper, Vincent and Tam": the last name joined by [and]. */
internal fun joinNames(names: List<String>, and: (head: String, last: String) -> String): String =
    when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> and(names.dropLast(1).joinToString(", "), names.last())
    }

private const val MaxAvatars = 3
private val AvatarSize = 30.dp
private val AvatarRing = 2.dp
private val AvatarOverlap = 10.dp
private val AnchorMinHeight = 50.dp
