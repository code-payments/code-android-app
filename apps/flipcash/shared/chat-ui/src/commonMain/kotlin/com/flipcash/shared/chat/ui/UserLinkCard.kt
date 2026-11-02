package com.flipcash.shared.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.rememberBlurHashPainter
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.addIf
import kotlin.time.Instant

/**
 * A person's link: a compact row, the avatar beside the name, handle and join date, as wide as its
 * text needs up to the width it is given. Tapped as a whole; a card with no account behind it takes
 * no tap.
 *
 * Built from the person's public profile only, and shares nothing with the tip card. [shape] is
 * the outline of the bubble the card stands in for, as for every link card; the border is the
 * group invite card's, and the rest is in [UserLinkCardDefaults].
 *
 * At accessibility font sizes the avatar moves above the text and every line wraps, where a row
 * would cut each one short.
 *
 * [onLongClick] is the transcript's selection gesture, handed back because the card takes the
 * press for its own tap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UserLinkCard(
    card: LinkCard.User,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: Shape = GroupInviteCardDefaults.SHAPE,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
) {
    val state = card.state

    if (state is LinkCard.User.State.Loading) {
        Box(
            modifier = modifier
                .size(UserLinkCardDefaults.SHIMMER_WIDTH, UserLinkCardDefaults.SHIMMER_HEIGHT)
                .clip(shape)
                .background(CodeTheme.colors.surfaceVariant)
                .shimmer(shape),
        )
        return
    }

    val resolved = state as? LinkCard.User.State.Resolved
    val tap = onClick.takeIf { resolved != null }
    val accessible = LocalDensity.current.fontScale >= UserLinkCardDefaults.ACCESSIBILITY_FONT_SCALE
    // The picture's BlurHash filling the card, never the photo itself. With no picture the tint
    // lies over the chat's own ground.
    val backdrop = rememberBlurHashPainter(resolved?.blurHash)

    Box(
        modifier = modifier
            .clip(shape)
            .background(CodeTheme.colors.background)
            .addIf(backdrop != null) { Modifier.cropBehind(backdrop!!) }
            .background(Color.Black.copy(alpha = UserLinkCardDefaults.TINT_OPACITY))
            .border(CodeTheme.dimens.border, GroupInviteCardDefaults.STROKE, shape)
            .then(
                when {
                    tap != null -> Modifier.combinedClickable(
                        onLongClick = onLongClick,
                        onDoubleClick = onDoubleClick,
                        onClick = tap,
                        // The row's select plays the tick; a second, platform haptic doubles it.
                        hapticFeedbackEnabled = false,
                    )
                    // Nothing to open, but a long press and a double tap still have to reach the
                    // transcript.
                    onLongClick != null || onDoubleClick != null ->
                        Modifier.pointerInput(onLongClick, onDoubleClick) {
                            detectTapGestures(
                                onLongPress = { onLongClick?.invoke() },
                                onDoubleTap = onDoubleClick?.let { double -> { double() } },
                            )
                        }
                    else -> Modifier
                },
            )
            .padding(
                start = UserLinkCardDefaults.LEADING_PADDING,
                end = UserLinkCardDefaults.TRAILING_PADDING,
                top = UserLinkCardDefaults.VERTICAL_PADDING,
                bottom = UserLinkCardDefaults.VERTICAL_PADDING,
            ),
    ) {
        if (accessible) {
            Column(verticalArrangement = Arrangement.spacedBy(UserLinkCardDefaults.AVATAR_GAP)) {
                Avatar(card, resolved)
                Identity(card, resolved, accessible = true)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UserLinkCardDefaults.AVATAR_GAP),
            ) {
                Avatar(card, resolved)
                Identity(card, resolved, accessible = false)
            }
        }
    }
}

/**
 * Draws [painter] behind the content, cropped to fill. Not `Modifier.paint`: with bounded
 * constraints it raises the minimum size to the maximum, and the card would fill the bubble
 * instead of hugging its text.
 */
private fun Modifier.cropBehind(painter: Painter): Modifier = drawBehind {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isUnspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) {
        with(painter) { draw(size) }
        return@drawBehind
    }
    val scale = ContentScale.Crop.computeScaleFactor(intrinsic, size)
    val drawn = Size(intrinsic.width * scale.scaleX, intrinsic.height * scale.scaleY)
    translate(left = (size.width - drawn.width) / 2f, top = (size.height - drawn.height) / 2f) {
        with(painter) { draw(drawn) }
    }
}

/**
 * The person's picture, or their initials on the avatar gradient. With no account, the generic
 * person glyph: there is no name to take initials from.
 */
@Composable
private fun Avatar(card: LinkCard.User, resolved: LinkCard.User.State.Resolved?) {
    // Size first: a fixed size answers an intrinsic measurement without asking the avatar's
    // BoxWithConstraints, which would throw.
    val modifier = Modifier
        .size(UserLinkCardDefaults.AVATAR)
        .border(CodeTheme.dimens.border, GroupInviteCardDefaults.STROKE, CircleShape)
        .clip(CircleShape)
    if (resolved != null) {
        ContactAvatar(
            image = resolved.profile.profilePicture,
            displayName = resolved.name ?: card.linkedHandle.orEmpty(),
            // The picture belongs to this profile, so the profile is what authorizes re-minting it.
            access = BlobAccessContext.profile(resolved.userId),
            modifier = modifier,
        )
    } else {
        ContactAvatar(contact = null, includeBorder = false, modifier = modifier)
    }
}

@Composable
private fun Identity(
    card: LinkCard.User,
    resolved: LinkCard.User.State.Resolved?,
    accessible: Boolean,
) {
    // Unlimited at accessibility sizes, where the stacked layout wraps rather than cutting a line.
    fun lines(count: Int) = if (accessible) Int.MAX_VALUE else count

    // The account's name, or with no account the handle the link names: nothing for an id link.
    val name = if (resolved != null) resolved.name else card.linkedHandle
    // When the account joined, or that there is no account.
    val detail = if (resolved != null) {
        resolved.joined
    } else {
        stringResource(R.string.error_title_usernameNotFound)
    }

    Column(verticalArrangement = Arrangement.spacedBy(UserLinkCardDefaults.LINE_GAP)) {
        name?.let {
            Text(
                text = it,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = lines(UserLinkCardDefaults.NAME_LINES),
                overflow = TextOverflow.Ellipsis,
            )
        }
        resolved?.handle?.let {
            Text(
                text = it,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain.copy(alpha = UserLinkCardDefaults.HANDLE_OPACITY),
                maxLines = lines(1),
                overflow = TextOverflow.Ellipsis,
            )
        }
        detail?.let {
            Text(
                text = it,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textMain.copy(alpha = UserLinkCardDefaults.DETAIL_OPACITY),
                maxLines = lines(1),
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * This card's own values, one for one with iOS's `LinkUserCardContent.Layout`. In a transcript the
 * outline is the bubble's; outside one it is [GroupInviteCardDefaults.SHAPE]. The border is
 * [GroupInviteCardDefaults.STROKE].
 */
internal object UserLinkCardDefaults {
    /** The black over the decoded BlurHash backdrop. Provisional, pending design sign-off. */
    const val TINT_OPACITY = 0.6f

    val AVATAR = 44.dp

    /** Between the avatar and the text beside it, or above it at accessibility sizes. */
    val AVATAR_GAP = 12.dp

    /** Above and below the content. */
    val VERTICAL_PADDING = 10.dp

    /**
     * Before the avatar. Less than [TRAILING_PADDING] because the avatar's circle already leaves
     * room at its edge.
     */
    val LEADING_PADDING = 10.dp

    /** After the text. */
    val TRAILING_PADDING = 16.dp

    /** Between the lines of text. */
    val LINE_GAP = 2.dp

    /**
     * The name wraps to this many lines before it ends in an ellipsis. Display names run to 64
     * characters; the handle and the joined line always fit on one.
     */
    const val NAME_LINES = 2

    /** The handle, set in `caption` under the `textMedium` name. */
    const val HANDLE_OPACITY = 0.5f

    /** The joined line or the not-found line, set in `caption`. */
    const val DETAIL_OPACITY = 0.45f

    /** The shimmer's size while the lookup is out, near a typical resolved card's. */
    val SHIMMER_WIDTH = 200.dp
    val SHIMMER_HEIGHT = 64.dp

    /**
     * The font scale at which the row stacks and stops truncating: Android's stand-in for iOS's
     * accessibility text sizes, which have no direct equivalent here.
     */
    const val ACCESSIBILITY_FONT_SCALE = 1.5f
}

// region Previews

private val PreviewUserId = List<Byte>(16) { it.toByte() }

internal fun previewUserCard(
    state: LinkCard.User.State,
    identity: LinkCard.User.Identity = LinkCard.User.Identity.ByUsername("satoshi"),
    url: String = "https://flipcash.com/satoshi",
) = LinkCard.User(url = url, start = 0, end = url.length, identity = identity, state = state)

internal fun previewUserResolved(
    name: String? = "Satoshi Nakamoto",
    handle: String? = "@satoshi",
    joined: String? = "Joined March 2024",
    blurHash: String? = "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
    isOwn: Boolean = false,
) = LinkCard.User.State.Resolved(
    userId = PreviewUserId,
    isOwn = isOwn,
    profile = UserProfile(
        displayName = name.orEmpty(),
        socialAccounts = emptyList(),
        phoneNumber = null,
        email = null,
        profilePicture = blurHash?.let(::previewPicture),
        joinedAt = Instant.fromEpochSeconds(1_710_000_000),
        userId = PreviewUserId,
        username = handle?.removePrefix("@"),
    ),
    name = name,
    handle = handle,
    joined = joined,
)

@Composable
private fun PreviewCard(card: LinkCard.User) {
    Box(Modifier.width(300.dp).padding(8.dp)) {
        UserLinkCard(card = card, onClick = {})
    }
}

/** Someone else: the blurred picture behind the tint, the handle and the join date. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_Someone() {
    PreviewCard(previewUserCard(previewUserResolved()))
}

/** An id link to someone with no handle: name and join date only. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_NoHandle() {
    PreviewCard(
        previewUserCard(
            state = previewUserResolved(handle = null),
            identity = LinkCard.User.Identity.ById(PreviewUserId),
            url = "https://flipcash.com/00010203-0405-0607-0809-0a0b0c0d0e0f",
        ),
    )
}

/** No picture: initials on the avatar gradient, and the tint over the chat's own background. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_NoPicture() {
    PreviewCard(previewUserCard(previewUserResolved(blurHash = null)))
}

/** No join date on the profile: the detail line is left out. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_NoJoinDate() {
    PreviewCard(previewUserCard(previewUserResolved(joined = null)))
}

/** The viewer's own link. Drawn the same; only the tap differs (their own tip card). */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_Own() {
    PreviewCard(previewUserCard(previewUserResolved(isOwn = true)))
}

/** An unclaimed handle: the placeholder avatar, the handle from the link, and no tap. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_NotFound() {
    PreviewCard(previewUserCard(LinkCard.User.State.NotFound))
}

/** A 64-character display name: two lines, then an ellipsis, the card at its widest. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_LongName() {
    PreviewCard(
        previewUserCard(
            previewUserResolved(name = "Satoshi Nakamoto the Pseudonymous Creator of a Currency Called B"),
        ),
    )
}

/** Accessibility font size: the avatar above the text, every line wrapping in full. */
@Preview(fontScale = 2f)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_LargeFont() {
    PreviewCard(
        previewUserCard(
            previewUserResolved(name = "Satoshi Nakamoto the Pseudonymous Creator of a Currency Called B"),
        ),
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UserLink_Loading() {
    PreviewCard(previewUserCard(LinkCard.User.State.Loading))
}

// endregion
