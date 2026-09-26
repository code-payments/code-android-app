package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.common.ui.BlurHash
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

/**
 * A group invite, drawn from the group's header card at the top of its own transcript (node
 * 10125:19157): the same avatar, title, count and rule, in the same order, so the card previews the
 * screen it opens. Nodes 10127:118280 and 10127:116723 are the card itself.
 *
 * Only the button takes a tap. The card is built from the group's public record, and a tap anywhere
 * on it opening the group would be indistinguishable from a tap meant for the message around it;
 * the button says what happens. [onStart] is null with the backdrop up, which leaves it inert.
 *
 * [minHeight] is the voucher's proportions at this width, as a floor rather than a size: the column
 * grows past it with the font scale instead of clipping. Measured at the card's own width, which in
 * Compose is simply the width it is laid out at -- the requirement line wraps there and the row
 * grows with it.
 *
 * Nothing from the roster reaches here, so there are no member avatars and no member names, even as
 * a fallback title; an untitled group is named generically.
 *
 * Drawn in two places that want different things from the button, so [ctaLabel] and [onStart] come
 * from the caller: a DM transcript's link card says "View" and opens the group, and the group's own
 * empty state says "Invite People" and opens the invite sheet. [shape] is the transcript bubble's
 * outline there, and the card's own radius where it stands alone.
 */
@Composable
fun GroupInviteLinkCard(
    card: LinkCard.GroupInvite,
    minHeight: Dp,
    ctaLabel: String,
    onStart: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = GroupInviteCardDefaults.SHAPE,
) {
    val state = card.state

    if (state is LinkCard.GroupInvite.State.Loading) {
        // Nothing about a group is known before the lookup -- not even whether it is one -- so the
        // loading card is a plain shimmer at the floor, and the row grows when the content lands.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(minHeight)
                .clip(shape)
                .background(CodeTheme.colors.surfaceVariant)
                .shimmer(shape),
        )
        return
    }

    val resolved = state as? LinkCard.GroupInvite.State.Resolved
    val placeholderTop = CodeTheme.colors.contactAvatar.colors.first()
    val tint = remember(resolved?.picture, placeholderTop) {
        BlurHash.averageColor(resolved?.picture?.blurhash())
            ?.let { Color(0xFF000000.toInt() or it) }
            ?: placeholderTop
    }

    val title = resolved?.title ?: stringResource(R.string.label_linkCard_untitledGroup)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The content's height at this width, or the floor if that is taller. Intrinsic, so the
            // spacer's weight below only spends what the floor leaves over and never grows the card
            // to whatever height its parent happens to offer.
            .heightIn(min = minHeight)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .border(
                width = CodeTheme.dimens.border,
                color = GroupInviteCardDefaults.STROKE,
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GroupInviteCardDefaults.BAND_HEIGHT)
                .background(tint.copy(alpha = GroupInviteCardDefaults.BAND_OPACITY)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = CodeTheme.dimens.inset,
                    end = CodeTheme.dimens.inset,
                    // The avatar's centre sits on the band's bottom edge.
                    top = GroupInviteCardDefaults.BAND_HEIGHT - GroupInviteCardDefaults.AVATAR / 2,
                    bottom = CodeTheme.dimens.inset,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (resolved != null) {
                ContactAvatar(
                    image = resolved.picture,
                    displayName = title,
                    access = BlobAccessContext.ChatProfile(card.chatId),
                    // Size first: the card is measured intrinsically, and a fixed size answers that
                    // without asking the avatar's BoxWithConstraints, which would throw.
                    modifier = Modifier
                        .size(GroupInviteCardDefaults.AVATAR)
                        .border(CodeTheme.dimens.border, GroupInviteCardDefaults.STROKE, CircleShape)
                        .clip(CircleShape),
                )

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                    text = title,
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    modifier = Modifier.padding(top = GroupInviteCardDefaults.MEMBER_COUNT_GAP),
                    text = pluralStringResource(
                        R.plurals.subtitle_chatMemberCount,
                        resolved.memberCount.toInt(),
                        resolved.memberCount.toString(),
                    ),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
                resolved.requirement?.let { RequirementLines(it) }
            }
            // Unavailable draws nothing between the band and the button: there is no group to name.

            // The least gap, then whatever the floor leaves over, so the button sits at the bottom
            // of a card taller than its content. Two spacers because a weight's height is exact: a
            // minimum on the weighted one would be clamped to nothing when the content is taller.
            Spacer(Modifier.height(GroupInviteCardDefaults.BUTTON_GAP))
            Spacer(Modifier.weight(1f))

            if (resolved != null) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(GroupInviteCardDefaults.BUTTON_HEIGHT),
                    text = ctaLabel,
                    buttonState = ButtonState.Filled,
                    overrideContentPadding = true,
                    contentPadding = GroupInviteCardDefaults.NO_PADDING,
                    enabled = onStart != null,
                    onClick = { onStart?.invoke() },
                )
            } else {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(GroupInviteCardDefaults.BUTTON_HEIGHT),
                    text = stringResource(R.string.label_linkCard_groupUnavailable),
                    buttonState = ButtonState.Filled20,
                    overrideContentPadding = true,
                    contentPadding = GroupInviteCardDefaults.NO_PADDING,
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

/**
 * The rule as the chat's header card states it (`ChatInfoCard`): the balance line, then the staff
 * line, each on its own line and centred, since both can be set on one chat.
 */
@Composable
private fun RequirementLines(requirement: LinkCard.GroupInvite.Requirement) {
    requirement.amount?.let { amount ->
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            text = if (requirement.currencyName != null) {
                stringResource(R.string.label_chat_balanceRequirement, amount, requirement.currencyName)
            } else {
                stringResource(R.string.label_chat_balanceRequirement_anyToken, amount)
            },
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    if (requirement.staffOnly) {
        Text(
            modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
            text = stringResource(R.string.label_chat_staffRequirement),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

object GroupInviteCardDefaults {
    /** The group header card's radius. */
    val SHAPE = RoundedCornerShape(12.dp)

    /** The group header card's stroke: white at 10%. Also the avatar's ring. */
    internal val STROKE = Color.White.copy(alpha = 0.1f)

    internal val BAND_HEIGHT = 46.dp

    /** Provisional, pending design sign-off. */
    internal const val BAND_OPACITY = 0.28f

    internal val AVATAR = 64.dp

    /** Title to member count. */
    internal val MEMBER_COUNT_GAP = 2.dp

    /** The least gap between the text and the button. */
    internal val BUTTON_GAP = 16.dp

    /** iOS's compact button height. Android has no compact style, so the standard one is sized. */
    internal val BUTTON_HEIGHT = 44.dp

    internal val NO_PADDING = PaddingValues(0.dp)

    /**
     * 224dp of card across 328dp of usable width, the wallet deck's proportions. The transcript
     * measures an invite card at this; the group's empty state uses it so the two agree.
     */
    const val ASPECT = 224f / 328f
}

// region Previews

private const val PREVIEW_URL = "https://app.flipcash.com/chat/6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162"

/** A rendition carrying only a blurhash: enough for the tint and the avatar's placeholder. */
private fun previewPicture(blurhash: String) = MediaItem(
    renditions = listOf(
        MediaItemRendition(
            role = MediaItemRendition.Role.THUMBNAIL,
            blobId = BlobId(ByteArray(16)),
            blob = BlobMetadata(
                mimeType = "image/jpeg",
                sizeBytes = 0,
                downloadUrl = "",
                image = ImageMetadata(width = 64, height = 64, blurhash = blurhash),
            ),
        ),
    ),
)

internal fun previewGroupCard(state: LinkCard.GroupInvite.State) = LinkCard.GroupInvite(
    url = PREVIEW_URL,
    start = 0,
    end = PREVIEW_URL.length,
    chatId = ChatId(ByteArray(16) { it.toByte() }),
    state = state,
)

internal val PreviewGroupResolved = LinkCard.GroupInvite.State.Resolved(
    title = "Bad Boys",
    picture = previewPicture("LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
    memberCount = 412,
    requirement = LinkCard.GroupInvite.Requirement(
        amount = "$100",
        currencyName = "Bad Boys",
        staffOnly = false,
    ),
)

@Composable
private fun PreviewCard(state: LinkCard.GroupInvite.State) {
    Box(Modifier.width(280.dp).padding(8.dp)) {
        GroupInviteLinkCard(
            card = previewGroupCard(state),
            minHeight = 280.dp * 224f / 328f,
            ctaLabel = "View",
            onStart = {},
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupInvite_Resolved() {
    PreviewCard(PreviewGroupResolved)
}

/** No picture: the avatar's initials on its gradient, and the band tinted from that gradient. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupInvite_NoPicture() {
    PreviewCard(PreviewGroupResolved.copy(picture = null, title = null, requirement = null))
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupInvite_Unavailable() {
    PreviewCard(LinkCard.GroupInvite.State.Unavailable)
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupInvite_Loading() {
    PreviewCard(LinkCard.GroupInvite.State.Loading)
}

/** Twice the font scale: the card grows past its floor rather than clipping. */
@Preview(fontScale = 2f)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_GroupInvite_LargeFont() {
    PreviewCard(
        PreviewGroupResolved.copy(
            requirement = PreviewGroupResolved.requirement?.copy(staffOnly = true),
        ),
    )
}

// endregion
