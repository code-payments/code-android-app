package com.flipcash.app.messenger.internal.invites

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.messenger.internal.screens.LinkPillFrame
import com.flipcash.features.messenger.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White08
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeToggleSwitch

internal const val InviteHostApproveTag = "invite-host-approve"
internal const val InviteHostShareTag = "invite-host-share"

/**
 * Frame 1m (spec surface 1): the link card, Approve New Members and Manage Invites, with no
 * navigation or view model so previews and tests can draw it. Copy Link and Show QR Code are on
 * the Invite People sheet that Share Link opens; revoking is in Manage Invites.
 */
@Composable
internal fun InviteHostSheetContent(
    groupName: String,
    state: InviteHostSheetViewModel.State,
    onShare: () -> Unit,
    onApproveChanged: (Boolean) -> Unit,
    onManageInvites: () -> Unit,
    onDismiss: () -> Unit,
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
                .padding(top = HeaderToCardGap, bottom = SheetBottomPadding),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            LinkCard(state = state, onShare = onShare)
            SheetRow(
                modifier = Modifier.testTag(InviteHostApproveTag),
                title = stringResource(R.string.label_approveNewMembers),
                subtitle = {
                    ApproveCrossfade(approve = state.approve) { on ->
                        Text(
                            text = stringResource(
                                if (on) R.string.subtitle_approveNewMembersOn else R.string.subtitle_approveNewMembersOff
                            ),
                            style = cardNoteStyle(),
                            color = CardNoteColor,
                        )
                    }
                },
                onClick = { onApproveChanged(!state.approve) },
            ) {
                CodeToggleSwitch(checked = state.approve, onCheckedChange = onApproveChanged)
            }
            SheetRow(
                title = stringResource(R.string.action_manageInvites),
                subtitle = { SubtitleText(stringResource(R.string.subtitle_manageInvitesRow)) },
                onClick = onManageInvites,
            ) {
                Icon(imageVector = Icons.Outlined.Menu, contentDescription = null, tint = CodeTheme.colors.textMain)
            }
        }
    }
}

// iOS #1175's host sheet: the cards 12 apart, 20 from the header to the link card (8 under the
// header plus the 12 between items) and 20 under the last card.
private val CardGap = 12.dp
private val HeaderToCardGap = 20.dp
private val SheetBottomPadding = 20.dp

// Figma 1m's pill sits darker than the card around it; no theme token is white at 0.06.
private val LinkPillColor = Color(0x0FFFFFFF)

// iOS #1175's link card, which is the reference: content 20 in and 12 apart, and a 48-high capsule
// pill with the link 16 in from its ends. Share Link stays the standard button.
private val LinkCardPadding = 20.dp
private val LinkCardSpacing = 12.dp
private val LinkPillHeight = 48.dp
private val LinkPillInset = 16.dp

private const val ApproveFadeMillis = 220

// iOS's host card, which is the reference: the link is Medium 14, and the caption and the switch's
// subtitles Medium 13 at white 0.68. The theme's Avenir draws Medium as Demi, so these start from
// the styles on the true Medium face.
@Composable
private fun pillLinkStyle() = CodeTheme.typography.textMessage.copy(fontSize = 14.sp, lineHeight = 18.sp)

@Composable
private fun cardNoteStyle() = CodeTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 18.sp)

private val CardNoteColor = White.copy(alpha = 0.68f)

/**
 * Crossfades what Approve New Members changes, and animates any change in height. [content] gets
 * the switch state it draws, so the outgoing text keeps the old one while it fades.
 */
@Composable
private fun ApproveCrossfade(
    approve: Boolean,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (approve: Boolean) -> Unit,
) {
    AnimatedContent(
        targetState = approve,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            fadeIn(tween(ApproveFadeMillis)) togetherWith fadeOut(tween(ApproveFadeMillis)) using
                SizeTransform(clip = false) { _, _ -> tween(ApproveFadeMillis) }
        },
        label = "approve-new-members",
    ) { content(it) }
}

/** The pill, Share Link, and the caption: how many joined and when it ends, or that people ask. */
@Composable
private fun LinkCard(
    state: InviteHostSheetViewModel.State,
    onShare: () -> Unit,
) {
    val invite = state.invite
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.medium)
            .background(White08)
            .padding(LinkCardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinkPillFrame(
            modifier = Modifier.height(LinkPillHeight),
            color = LinkPillColor,
            shape = CircleShape,
            horizontalPadding = LinkPillInset,
            verticalPadding = 0.dp,
        ) {
            ApproveCrossfade(
                approve = state.approve,
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) { approve ->
                when {
                    approve -> state.plainUrl?.let { PillText(plainLinkText(it)) }
                    // The whole link, left to PillText to cut in the middle so the token's end shows.
                    invite is InviteLinkState.Ready -> PillText(plainLinkText(invite.url))
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
        }
        Spacer(Modifier.height(LinkCardSpacing))
        CodeButton(
            modifier = Modifier.fillMaxWidth().testTag(InviteHostShareTag),
            text = stringResource(R.string.action_shareLink),
            buttonState = ButtonState.Filled,
            isLoading = state.creating,
            enabled = !state.creating,
            onClick = onShare,
        )
        // The spacing above the caption goes with it, so a card with no caption ends at the button.
        ApproveCrossfade(approve = state.approve, modifier = Modifier.fillMaxWidth()) { approve ->
            val caption = when {
                approve -> stringResource(R.string.label_joinLinkAsksToJoin)
                invite is InviteLinkState.Ready -> stringResource(
                    R.string.label_inviteJoinedThenEnds,
                    JoinedCount(invite.useCount, invite.maxUses).label(),
                    (invite.expiresAt?.let(LinkStatus::Expires) ?: LinkStatus.NeverExpires).label(),
                )
                else -> null
            }
            if (caption != null) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(top = LinkCardSpacing),
                    text = caption,
                    style = cardNoteStyle(),
                    color = CardNoteColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PillText(text: String) {
    Text(
        text = text,
        style = pillLinkStyle(),
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
    subtitle: @Composable () -> Unit,
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
            subtitle()
        }
        trailing()
    }
}

@Composable
private fun SubtitleText(text: String) {
    Text(text = text, style = CodeTheme.typography.textSmall, color = CodeTheme.colors.textSecondary)
}
