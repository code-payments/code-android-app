package com.flipcash.app.messenger.internal.screens.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.features.messenger.R
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/** One person on the details sheet: who they are, and their profile when it has resolved. */
internal data class GroupSendPerson(val name: String, val profile: UserProfile?)

/**
 * A group send's details, node 11293:6918 frame 10: the currency it was sent in, who sent it and
 * who it paid. Names only — each part's amount is in the bubble, and only for the viewer's own.
 */
@Composable
internal fun GroupSendDetailsSheet(
    tokenName: String,
    tokenImageUrl: String,
    sender: GroupSendPerson,
    recipients: List<GroupSendPerson>,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x4, bottom = CodeTheme.dimens.grid.x3),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
        ) {
            DetailRow(label = stringResource(R.string.label_cashDetails_sent)) {
                TokenIconWithName(
                    tokenName = tokenName,
                    tokenImage = tokenImageUrl,
                    imageSize = CodeTheme.dimens.staticGrid.x6,
                    spacing = CodeTheme.dimens.grid.x3,
                    textStyle = CodeTheme.typography.textMedium,
                )
            }
            DetailRow(label = stringResource(R.string.label_cashDetails_from)) {
                PersonCell(sender)
            }
            recipients.forEachIndexed { index, person ->
                DetailRow(label = if (index == 0) stringResource(R.string.label_cashDetails_to) else "") {
                    PersonCell(person)
                }
            }
        }
        CodeButton(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.action_groupSendDetailsClose),
            buttonState = ButtonState.Filled,
            onClick = onClose,
        )
    }
}

@Composable
private fun DetailRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.width(64.dp),
            text = label,
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
        content()
    }
}

@Composable
private fun PersonCell(person: GroupSendPerson) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        val avatar = Modifier
            .requiredSize(CodeTheme.dimens.staticGrid.x6)
            .clip(CircleShape)
        if (person.profile != null) {
            ContactAvatar(userProfile = person.profile, modifier = avatar)
        } else {
            ContactAvatar(photoUri = null, displayName = person.name, modifier = avatar)
        }
        Text(
            text = person.name,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
