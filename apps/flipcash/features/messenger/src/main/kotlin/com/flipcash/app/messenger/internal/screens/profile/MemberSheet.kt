package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.messenger.internal.MemberActions
import com.flipcash.app.messenger.internal.TimeLeft
import com.flipcash.app.messenger.internal.timeLeft
import com.flipcash.features.messenger.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.SilenceState
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.ChoiceRow
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.resources.icons.SpeakerSlash
import kotlin.time.Instant

/**
 * S3–S5: who was tapped, View Profile, and either Report or, for a host, the group's own section.
 * Drawn on the app's sheet scene at the height of its content, as the duration sheets are.
 *
 * Block and Mute stay on the full profile's ⋯, so this never grows into a second menu.
 *
 * @param silence the member's restriction, shown only when [actions] says the viewer may see it.
 */
@Composable
internal fun MemberSheetContent(
    profile: UserProfile,
    name: String,
    handle: String?,
    groupTitle: String,
    actions: MemberActions,
    silence: SilenceState?,
    now: Instant,
    onViewProfile: () -> Unit,
    onReport: () -> Unit,
    onRestrict: () -> Unit,
    onLift: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.inset, bottom = CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            ContactAvatar(
                userProfile = profile,
                modifier = Modifier
                    .size(CodeTheme.dimens.staticGrid.x13)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (handle != null && handle != name) {
                    Text(
                        text = handle,
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            AppBarDefaults.Close(
                modifier = Modifier.align(Alignment.Top),
                onClick = onDismiss,
            )
        }

        if (actions.showsRestriction) {
            restrictionLabel(silence, now)?.let { label ->
                Text(
                    text = label,
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.warning,
                )
            }
        }

        CodeButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CodeTheme.dimens.grid.x1),
            text = stringResource(R.string.action_viewProfile),
            buttonState = ButtonState.Filled,
            onClick = onViewProfile,
        )

        if (actions.report) {
            ChoiceRow(
                label = stringResource(R.string.title_report),
                icon = rememberVectorPainter(Icons.Outlined.Feedback),
                onClick = onReport,
            )
        } else {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                text = stringResource(R.string.title_menuSection_inGroup, groupTitle),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (actions.restrict) {
                ChoiceRow(
                    label = stringResource(R.string.action_silenceMemberShort),
                    icon = rememberVectorPainter(SpeakerSlash),
                    contentColor = CodeTheme.colors.warningText,
                    onClick = onRestrict,
                )
            }
            if (actions.lift) {
                ChoiceRow(
                    label = stringResource(R.string.action_liftRestriction),
                    icon = rememberVectorPainter(Icons.Outlined.LockOpen),
                    onClick = onLift,
                )
            }
            if (actions.remove) {
                ChoiceRow(
                    label = stringResource(R.string.action_removeFromGroup),
                    icon = rememberVectorPainter(Icons.Outlined.PersonRemove),
                    contentColor = CodeTheme.colors.errorText,
                    onClick = onRemove,
                )
            }
        }
    }
}

/**
 * "Restricted · 6h 12m left", or "Restricted" for a restriction with no end; null once it has
 * lapsed. The Members row and the member sheet both say it this way.
 */
@Composable
internal fun restrictionLabel(silence: SilenceState?, now: Instant): String? = when (silence) {
    null -> null
    SilenceState.Forever -> stringResource(R.string.subtitle_restricted)
    is SilenceState.Until -> timeLeft(silence, now)?.let { left ->
        stringResource(R.string.subtitle_restrictedTimeLeft, timeLeftText(left))
    }
}

@Composable
private fun timeLeftText(left: TimeLeft): String = when (left) {
    is TimeLeft.Days -> if (left.hours > 0) {
        stringResource(R.string.duration_daysHours, left.days, left.hours)
    } else {
        stringResource(R.string.duration_days, left.days)
    }
    is TimeLeft.Hours -> if (left.minutes > 0) {
        stringResource(R.string.duration_hoursMinutes, left.hours, left.minutes)
    } else {
        stringResource(R.string.duration_hours, left.hours)
    }
    is TimeLeft.Minutes -> stringResource(R.string.duration_minutes, left.minutes)
}
