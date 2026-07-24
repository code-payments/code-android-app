package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.messenger.internal.ChatParticipant
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.flipcash.services.models.UserProfile
import com.getcode.theme.CodeTheme

@Composable
internal fun ContactInfoContainer(
    participant: ChatParticipant?,
    modifier: Modifier = Modifier,
    onRefreshContact: () -> Unit = {},
) {
    // Phone number and the add-to-contacts pill only apply to a device contact; a tip DM's
    // counterparty (a server profile) has neither.
    val contact = (participant as? ChatParticipant.Contact)?.contact
    Column(
        modifier = modifier
            .border(
                color = CodeTheme.colors.divider,
                width = CodeTheme.dimens.border,
                shape = CodeTheme.shapes.medium,
            )
            .padding(CodeTheme.dimens.grid.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ParticipantAvatar(
            participant = participant,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x17)
                .clip(CircleShape),
        )
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            text = participant?.displayName.orEmpty(),
            autoSize = TextAutoSize.StepBased(
                minFontSize = CodeTheme.typography.textSmall.fontSize,
                maxFontSize = CodeTheme.typography.textLarge.fontSize,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = CodeTheme.typography.textLarge,
            color = CodeTheme.colors.textMain,
        )

        if (contact != null && !contact.isUnknown) {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = contact.displayNumber,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { onRefreshContact() }

        Indicator(
            participant = participant,
            modifier = Modifier.padding(top = CodeTheme.dimens.inset),
        ) {
            when (participant) {
                is ChatParticipant.Contact -> {
                    val intent = IntentUtils.openContact(participant.contact).apply {
                        // Remove NEW_TASK so the result callback fires when the user returns,
                        // not immediately.
                        flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
                    }
                    launcher.launch(intent)
                }
                is ChatParticipant.TipUser -> Unit
                null -> Unit
            }
        }
    }
}

@Composable
private fun Indicator(
    participant: ChatParticipant?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    when (participant) {
        is ChatParticipant.Contact -> ContactPill(
            participant.contact,
            modifier = modifier,
            onClick = onClick
        )

        is ChatParticipant.TipUser -> TipPill(modifier = modifier)
        null -> Spacer(modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun ContactPill(
    contact: DeviceContact,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedContent(contact) { c ->
        val backgroundColor by animateColorAsState(
            if (!c.isUnknown) CodeTheme.colors.surfaceVariant else CodeTheme.colors.warning.copy(
                alpha = 0.10f
            )
        )

        val contentColor by animateColorAsState(
            if (!c.isUnknown) CodeTheme.colors.textSecondary else CodeTheme.colors.warning
        )

        Row(
            modifier = modifier
                .background(color = backgroundColor, shape = CircleShape)
                .clip(CircleShape)
                .clickable { onClick() }
                .padding(
                    horizontal = CodeTheme.dimens.grid.x2,
                    vertical = CodeTheme.dimens.grid.x1
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
        ) {
            Icon(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                painter = painterResource(
                    if (!c.isUnknown) {
                        R.drawable.ic_existing_contact
                    } else {
                        R.drawable.ic_unknown_contact
                    }
                ),
                contentDescription = null,
                tint = contentColor,
            )

            Text(
                text = if (!c.isUnknown) stringResource(R.string.label_fromYourContacts) else stringResource(
                    R.string.label_addContact
                ),
                color = contentColor,
                style = CodeTheme.typography.textSmall,
            )
        }
    }
}

@Composable
private fun TipPill(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(color = CodeTheme.colors.surfaceVariant, shape = CircleShape)
            .clip(CircleShape)
            .padding(horizontal = CodeTheme.dimens.grid.x2, vertical = CodeTheme.dimens.grid.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
    ) {
        Icon(
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
            painter = painterResource(R.drawable.ic_tipcode),
            contentDescription = null,
            tint = CodeTheme.colors.textSecondary,
        )

        Text(
            text = stringResource(R.string.label_viaTipCard),
            color = CodeTheme.colors.textSecondary,
            style = CodeTheme.typography.textSmall,
        )
    }
}

// region Previews

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_AllStates() {
    // A saved device contact: name + number + the "From your contacts" pill.
    val knownContact = ChatParticipant.Contact(
        DeviceContact(
            e164 = "+15551234567",
            androidContactId = 1L,
            displayName = "Ada Lovelace",
            photoUri = null,
            displayNumber = "(555) 123-4567",
        )
    )

    // An unknown number: falls back to the number as the name and shows the warning
    // "Add contact" pill.
    val unknownContact = ChatParticipant.Contact(
        DeviceContact.unknownContact(
            e164 = "+15559876543",
            displayNumber = "(555) 987-6543",
        )
    )

    // A tip DM's counterparty: identity from a server profile — no phone number, no pill.
    val tipUser = ChatParticipant.TipUser(
        userId = listOf(1.toByte()),
        profile = UserProfile.Empty.copy(displayName = "Grace Hopper"),
    )

    // Fixed width so every state renders at the same size regardless of name/number length.
    val cardWidth = Modifier.width(300.dp)
    Column(
        modifier = Modifier.padding(CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
    ) {
        ContactInfoContainer(participant = knownContact, modifier = cardWidth)
        ContactInfoContainer(participant = unknownContact, modifier = cardWidth)
        ContactInfoContainer(participant = tipUser, modifier = cardWidth)
        // Null participant: loading / unknown fallback avatar, name empty, no pill.
        ContactInfoContainer(participant = null, modifier = cardWidth)
    }
}

// endregion