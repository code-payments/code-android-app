package com.flipcash.app.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.bills.components.cards.TipCardFlattened
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.share.groupcard.GroupCardQr
import com.flipcash.app.menu.internal.CardOverlay
import com.flipcash.app.menu.internal.GroupCardViewModel
import com.flipcash.core.R as CoreR
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme

/**
 * A group's scan card, full screen, behind `AppRoute.Menu.GroupCard`: the group's picture and name
 * over a QR of its invite link. Presents and closes exactly like the profile card
 * ([CardOverlay]). The card grows with its content rather than holding the profile card's ratio,
 * and the avatar straddles its top edge.
 */
@Composable
fun GroupCardScreen(route: AppRoute.Menu.GroupCard) {
    val viewModel = hiltViewModel<GroupCardViewModel, GroupCardViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    )
    val matrix = viewModel.matrix

    CardOverlay(
        onDownload = viewModel::download,
        downloadTestTag = "group-card-download",
        closeTestTag = "group-card-close",
    ) { cardModifier ->
        val width = CodeTheme.dimens.screenWidth * CardWidthFraction
        val avatarSize = width * AvatarFraction
        val textSize = with(LocalDensity.current) { (width * TextFraction).toSp() }
        val lineHeight = textSize * LineHeightRatio

        val inviteLink = viewModel.inviteLink
        Column(
            modifier = cardModifier
                .align(Alignment.Center)
                // The avatar's upper half stands outside the card, so the card sits lower by half
                // of it and the whole group stays centred.
                .offset(y = avatarSize / 2)
                .width(width),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(width * CornerFraction))
                    .background(TipCardFlattened)
                    .padding(top = width * ContentTopFraction, bottom = width * ContentBottomFraction),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = width * TextInsetFraction),
                    text = route.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(CoreR.string.label_groupCardTitleFallback),
                    style = CodeTheme.typography.textMedium.copy(
                        fontSize = textSize,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    color = CodeTheme.colors.textMain,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (matrix != null) {
                    GroupCardQr(
                        matrix = matrix,
                        modifier = Modifier
                            .padding(top = width * QrTopFraction)
                            .size(width * QrFraction)
                            .testTag("group-card-qr"),
                    )
                }
                // Frame 1j: an invite-link card says it joins without asking, and until when, so a
                // saved or printed card carries its own expiry.
                if (inviteLink != null) {
                    Text(
                        modifier = Modifier.padding(top = width * QrTopFraction),
                        text = stringResource(CoreR.string.label_groupCardScanToJoin),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                        text = inviteLink.expiresAt
                            ?.let { stringResource(CoreR.string.label_groupCardLinkExpires, cardDate(it)) }
                            ?: stringResource(CoreR.string.label_groupCardLinkNeverExpires),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )
                }
            }

            // A ring in the screen colour keeps the picture from running into the card.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -avatarSize / 2)
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(CodeTheme.colors.background)
                    .padding(width * RingFraction)
                    .clip(CircleShape),
            ) {
                ContactAvatar(
                    image = route.picture,
                    displayName = route.title.orEmpty(),
                    access = BlobAccessContext.ChatProfile(route.chatId),
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
            if (inviteLink != null) {
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x4)
                        .testTag("group-card-invite-footnote"),
                    text = stringResource(CoreR.string.footnote_groupCardInviteLink),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "Nov 3", as the invite sheet writes a link's dates. */
private fun cardDate(at: kotlin.time.Instant): String =
    java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()))

private const val CardWidthFraction = 0.75f

// The profile card's corner and name proportions (TipCard.kt).
private const val CornerFraction = 0.08f
private const val TextFraction = 17f / 269f
private const val LineHeightRatio = 1.25f

private const val AvatarFraction = 0.2f
private const val RingFraction = 0.012f
private const val ContentTopFraction = 0.12f
private const val QrFraction = 0.68f

// The spec leaves the gaps open; these follow the profile card's name rhythm.
private const val QrTopFraction = 0.06f
private const val ContentBottomFraction = 0.06f
private const val TextInsetFraction = 0.06f
