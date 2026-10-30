package com.flipcash.app.messenger.internal.invites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.flipcash.app.messenger.internal.screens.LinkPillFrame
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge
import com.getcode.theme.White08
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeToggleSwitch

internal const val InviteHostMoreTag = "invite-host-more"
internal const val InviteHostApproveTag = "invite-host-approve"
internal const val InviteHostShareTag = "invite-host-share"

/**
 * Frames 1m to 1m″ (spec surface 1): the link card, Approve new members and Manage Invites, with
 * no navigation or view model so previews and tests can draw it.
 */
@Composable
internal fun InviteHostSheetContent(
    groupName: String,
    state: InviteHostSheetViewModel.State,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onShowCard: () -> Unit,
    onRevoke: () -> Unit,
    onApproveChanged: (Boolean) -> Unit,
    onManageInvites: () -> Unit,
    onDismiss: () -> Unit,
    initiallyExpanded: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        AppBarWithTitle(
            title = stringResource(R.string.title_inviteToGroup, groupName),
            titleAlignment = Alignment.CenterHorizontally,
            endContent = { AppBarDefaults.Close(onClick = onDismiss) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            LinkCard(
                state = state,
                onShare = onShare,
                onCopy = onCopy,
                onShowCard = onShowCard,
                onRevoke = onRevoke,
                initiallyExpanded = initiallyExpanded,
            )
            SheetRow(
                modifier = Modifier.testTag(InviteHostApproveTag),
                title = stringResource(R.string.label_approveNewMembers),
                subtitle = stringResource(
                    if (state.approve) R.string.subtitle_approveNewMembersOn else R.string.subtitle_approveNewMembersOff
                ),
                onClick = { onApproveChanged(!state.approve) },
            ) {
                CodeToggleSwitch(checked = state.approve, onCheckedChange = onApproveChanged)
            }
            SheetRow(
                title = stringResource(R.string.action_manageInvites),
                subtitle = stringResource(R.string.subtitle_manageInvitesRow),
                onClick = onManageInvites,
            ) {
                Icon(imageVector = Icons.Outlined.Menu, contentDescription = null, tint = CodeTheme.colors.textMain)
            }
        }
    }
}

// Figma 1m's pill sits darker than the card around it; no theme token is white at 0.06.
private val LinkPillColor = Color(0x0FFFFFFF)

/** The pill with ⋯, Share Link, and the caption: how many joined and when it ends, or that people ask. */
@Composable
private fun LinkCard(
    state: InviteHostSheetViewModel.State,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onShowCard: () -> Unit,
    onRevoke: () -> Unit,
    initiallyExpanded: Boolean,
) {
    val invite = state.invite
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.medium)
            .background(White08)
            .padding(CodeTheme.dimens.grid.x3),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinkPillFrame(color = LinkPillColor) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                when {
                    state.approve -> state.plainUrl?.let { PillText(plainLinkText(it)) }
                    invite is InviteLinkState.Ready -> PillText(elideInviteUrl(invite.url))
                    invite == InviteLinkState.Creating -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    ) {
                        CodeCircularProgressIndicator(modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6 / 2))
                        Text(
                            text = stringResource(R.string.label_creatingInviteLink),
                            style = CodeTheme.typography.textMedium,
                            color = White50,
                        )
                    }
                    invite == InviteLinkState.Loading ->
                        CodeCircularProgressIndicator(modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6 / 2))
                    // No link yet, or the last one revoked: the next tap makes one.
                    else -> Unit
                }
            }
            MoreMenu(
                canRevoke = state.canRevoke,
                enabled = !state.creating,
                onCopy = onCopy,
                onShowCard = onShowCard,
                onRevoke = onRevoke,
                initiallyExpanded = initiallyExpanded,
            )
        }
        CodeButton(
            modifier = Modifier.fillMaxWidth().testTag(InviteHostShareTag),
            text = stringResource(R.string.action_shareLink),
            buttonState = ButtonState.Filled,
            isLoading = state.creating,
            enabled = !state.creating,
            onClick = onShare,
        )
        val caption = when {
            state.approve -> stringResource(R.string.label_joinLinkAsksToJoin)
            invite is InviteLinkState.Ready -> stringResource(
                R.string.label_inviteJoinedThenEnds,
                JoinedCount(invite.useCount, invite.maxUses).label(),
                (invite.expiresAt?.let(LinkStatus::Expires) ?: LinkStatus.NeverExpires).label(),
            )
            else -> null
        }
        if (caption != null) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = caption,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Frame 1m″: the platform's own menu off the pill's ⋯. */
@Composable
private fun MoreMenu(
    canRevoke: Boolean,
    enabled: Boolean,
    onCopy: () -> Unit,
    onShowCard: () -> Unit,
    onRevoke: () -> Unit,
    initiallyExpanded: Boolean,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Box {
        IconButton(
            modifier = Modifier.testTag(InviteHostMoreTag),
            enabled = enabled,
            onClick = { expanded = true },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = stringResource(R.string.action_moreLinkOptions),
                tint = CodeTheme.colors.textMain,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // The profile's ⋯ menu surface.
            containerColor = CodeTheme.colors.brandLight,
            shape = CodeTheme.shapes.extraLarge,
        ) {
            MenuItem(stringResource(R.string.action_copyLink)) { expanded = false; onCopy() }
            MenuItem(stringResource(R.string.action_showGroupCard)) { expanded = false; onShowCard() }
            if (canRevoke) {
                HorizontalDivider(color = CodeTheme.colors.divider)
                MenuItem(
                    text = stringResource(R.string.action_revokeAndMakeNewLink),
                    color = CodeTheme.colors.errorText,
                ) { expanded = false; onRevoke() }
            }
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    color: Color = CodeTheme.colors.textMain,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text = text, style = CodeTheme.typography.textSmall, color = color) },
        onClick = onClick,
    )
}

@Composable
private fun PillText(text: String) {
    Text(
        text = text,
        style = CodeTheme.typography.textMedium,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
        // No wrap: a link breaks at its slashes and hyphens, and maxLines would clip what follows.
        // Still too wide for the pill, it loses its middle rather than its end.
        softWrap = false,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

/** A card row: title and subtitle, and [trailing] at the end; the whole row is the target. */
@Composable
private fun SheetRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.medium)
            .background(White08)
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)) {
            Text(text = title, style = CodeTheme.typography.textMedium, color = CodeTheme.colors.textMain)
            Text(text = subtitle, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
        }
        trailing()
    }
}
