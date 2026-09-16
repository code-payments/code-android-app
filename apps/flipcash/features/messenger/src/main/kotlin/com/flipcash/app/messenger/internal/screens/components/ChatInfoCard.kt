package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.core.android.IntentUtils
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.messenger.internal.ChatSubject
import com.flipcash.app.messenger.internal.balanceRequirement
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.messenger.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf

/**
 * The identity card at the head of a transcript.
 *
 * Was `ContactInfoContainer`, which opened with `participant as? ChatParticipant.Contact` and
 * rendered a bare avatar and name for anything else. Driving it from [ChatSubject] makes each arm
 * say what it shows: the contact arm keeps its phone line and add-to-contacts pill, the tip arm
 * keeps its handle, and the group arm gets the rules line from node 10125:19201.
 */
@Composable
internal fun ChatInfoCard(
    subject: ChatSubject?,
    modifier: Modifier = Modifier,
    includeBorder: Boolean = true,
    onOpenProfile: (() -> Unit)? = null,
    onRefreshContact: () -> Unit = {},
    /**
     * The symbol of the token a group's balance rule names, once the token cache has it.
     *
     * Resolved in the view model rather than read here: `observeTokenCache()` starts empty and
     * fills in, so a snapshot read would render the rule with no token and never correct itself.
     */
    ticker: String? = null,
) {
    val participant = subject?.asParticipant()
    // Phone number and the add-to-contacts pill only apply to a device contact; a tip DM's
    // counterparty (a server profile) has neither.
    val contact = (subject as? ChatSubject.Contact)?.participant?.contact
    Column(
        modifier = modifier
            .addIf(includeBorder) {
                Modifier.border(
                    color = CodeTheme.colors.divider,
                    width = CodeTheme.dimens.border,
                    shape = CodeTheme.shapes.medium,
                )
            }
            .addIf(onOpenProfile != null) {
                Modifier.clickable { onOpenProfile?.invoke() }
            }
            .addIf(includeBorder) {
                Modifier.padding(CodeTheme.dimens.grid.x6)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ChatSubjectAvatar(
            subject = subject,
            modifier = Modifier
                .size(CodeTheme.dimens.staticGrid.x17)
                .clip(CircleShape),
        )

        Row(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        ) {
            Text(
                modifier = if (onOpenProfile != null) Modifier.weight(1f, fill = false) else Modifier,
                text = subject?.title.orEmpty(),
                autoSize = TextAutoSize.StepBased(
                    minFontSize = CodeTheme.typography.textSmall.fontSize,
                    maxFontSize = CodeTheme.typography.textLarge.fontSize,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            if (onOpenProfile != null) {
                Icon(
                    modifier = Modifier.scale(0.8f),
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CodeTheme.colors.textSecondary,
                )
            }
        }

        // The line under the name says how this person is addressed: a tip DM by their public
        // handle (node 9443:8928), a contact DM by the number the chat is keyed on. Never both —
        // only one of the two identity sources backs any given conversation. Dropped when the name
        // above already *is* the handle, so a name-less account doesn't show it twice.
        val handle = participant?.handle?.takeIf { it != participant.name }
        if (handle != null) {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = handle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        } else if (contact != null && !contact.isUnknown) {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                text = contact.displayNumber,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }

        // Node 10125:19201. Rendered for a member as well as a non-member: it is the chat's
        // standing requirement, not a gate message, and it is the one thing the card can say
        // about a group that it cannot say about a person.
        val requirement = (subject as? ChatSubject.Group)?.rules.balanceRequirement()
        if (requirement != null) {
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                text = if (ticker != null) {
                    stringResource(
                        R.string.label_chat_balanceRequirement,
                        requirement.amount.formatted(),
                        ticker,
                    )
                } else {
                    stringResource(
                        R.string.label_chat_balanceRequirement_anyToken,
                        requirement.amount.formatted(),
                    )
                },
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
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

        else -> Unit
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
private fun Preview_ChatInfoCard() {
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
        profile = UserProfile.Empty.copy(
            displayName = "Grace Hopper",
            username = "grace_hopper",
        ),
    )

    // A tip DM's counterparty who never set a name: the handle is their whole identity, so it
    // takes the name line and the line beneath it is dropped.
    val handleOnlyUser = ChatParticipant.TipUser(
        userId = listOf(2.toByte()),
        profile = UserProfile.Empty.copy(username = "sally_streamer"),
    )

    // The group arm: title, member count, and the balance rule from node 10125:19201.
    val group = ChatSubject.Group(
        chatId = ChatId(byteArrayOf(1)),
        groupTitle = "Bad Boys",
        picture = null,
        memberCount = 412L,
        rules = ChatRules(
            listener = listOf(
                ChatRuleRequirement.MinimumBalance(
                    amount = Fiat(100.0),
                    mints = listOf(Mint(List(32) { 7.toByte() })),
                ),
            ),
            speaker = emptyList(),
        ),
        isMember = false,
    )

    // Fixed width so every state renders at the same size regardless of name/number length.
    val cardWidth = Modifier.width(300.dp)
    Column(
        modifier = Modifier.padding(CodeTheme.dimens.grid.x4),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
    ) {
        ChatInfoCard(subject = ChatSubject.Contact(knownContact), modifier = cardWidth)
        ChatInfoCard(subject = ChatSubject.Contact(unknownContact), modifier = cardWidth)
        ChatInfoCard(subject = ChatSubject.TipUser(tipUser), modifier = cardWidth)
        ChatInfoCard(subject = ChatSubject.TipUser(handleOnlyUser), modifier = cardWidth)
        ChatInfoCard(subject = group, modifier = cardWidth, ticker = "BADBOYS")
    }
}

// endregion