package com.flipcash.shared.transactionhistory

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme

/**
 * Each coin of a convert's overlapping pair, as a fraction of the avatar it sits in. Fixed as a
 * ratio rather than a dp so a details-screen avatar's pair scales with it; 0.625 of the 40dp row
 * avatar is the 25dp the row has always drawn.
 */
private const val SwapCoinRatio = 0.625f

/**
 * A [TransactionAvatar] drawn as an image: the avatar centred in a box wide enough for the token
 * badge to overhang its bottom-right corner without pushing whatever sits beside it (Figma
 * 9717:14138). Every caller reserves the full slot, badge or not, so a list of them lines up.
 *
 * Shared between the activity row and the details screen it opens, at different sizes, so the
 * screen opens on exactly the avatar that was tapped rather than a second rendering of it.
 *
 * The defaults are the row's: sized off the static 5pt grid rather than the design's raw pixels,
 * because the avatar and badge land on it exactly (x8 = 40dp, x4 = 20dp) and the slot takes
 * x10 = 50dp, one grid step of overhang on each side. That is 2dp wider than the 48dp Figma draws —
 * the cost of staying on-grid, and it moves the title by the same 2dp on every row rather than
 * unevenly.
 *
 * [iconOverride] is the token images' escape hatch for previews and screenshot tests, where the
 * remote URL never resolves; it matches [com.flipcash.app.core.ui.TokenIcon]'s own parameter.
 */
@Composable
fun TransactionAvatarImage(
    avatar: TransactionAvatar,
    modifier: Modifier = Modifier,
    size: Dp = CodeTheme.dimens.staticGrid.x8,
    slotSize: Dp = CodeTheme.dimens.staticGrid.x10,
    badgeSize: Dp = CodeTheme.dimens.staticGrid.x4,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
) {
    Box(
        modifier = modifier.requiredSize(slotSize),
        contentAlignment = Alignment.Center,
    ) {
        val avatarModifier = Modifier
            .requiredSize(size)
            .clip(CircleShape)

        when (avatar) {
            // Their picture, falling back to their initials — NOT the anonymous silhouette. The
            // silhouette answers "we don't know who this is", which is [Generic]'s job; a resolved
            // profile always has a name to draw from even when it has no photo.
            is TransactionAvatar.Profile ->
                ContactAvatar(
                    image = avatar.profile.profilePicture,
                    displayName = avatar.profile.displayName,
                    // The picture belongs to this profile, so the profile is what authorizes
                    // re-minting its download URL once the original expires.
                    access = BlobAccessContext.profile(avatar.profile.userId),
                    modifier = avatarModifier,
                )
            is TransactionAvatar.TokenIcon ->
                TokenIcon(token = avatar.token, modifier = avatarModifier, iconOverride = iconOverride)
            is TransactionAvatar.SwapTokens ->
                SwapAvatar(
                    avatar = avatar,
                    modifier = Modifier.requiredSize(size),
                    coinSize = size * SwapCoinRatio,
                    iconOverride = iconOverride,
                )
            is TransactionAvatar.Generic ->
                ContactAvatar(userProfile = UserProfile.Empty, modifier = avatarModifier)
        }

        // Ringed in the page background so the coin reads as sitting over the avatar rather than
        // being part of it — the same treatment [SwapAvatar] gives its overlapping pair.
        avatar.badgeToken?.let { token ->
            TokenIcon(
                token = token,
                iconOverride = iconOverride,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .requiredSize(badgeSize)
                    .border(CodeTheme.dimens.thickBorder, CodeTheme.colors.background, CircleShape),
            )
        }
    }
}

/**
 * A convert's two tokens as overlapping coins — the destination sits over the source, ringed in the
 * page background so the overlap reads as depth. Mirrors iOS's `swapAvatar` in `ActivityRow.swift`.
 */
@Composable
private fun SwapAvatar(
    avatar: TransactionAvatar.SwapTokens,
    coinSize: Dp,
    modifier: Modifier = Modifier,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
) {
    Box(modifier = modifier) {
        TokenCoin(
            token = avatar.from,
            iconOverride = iconOverride,
            modifier = Modifier
                .align(Alignment.TopStart)
                .requiredSize(coinSize),
        )
        TokenCoin(
            token = avatar.to,
            iconOverride = iconOverride,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .requiredSize(coinSize)
                .border(CodeTheme.dimens.thickBorder, CodeTheme.colors.background, CircleShape),
        )
    }
}

/** One coin of a [SwapAvatar]; an unresolved side draws the shared placeholder. */
@Composable
private fun TokenCoin(
    token: Token?,
    modifier: Modifier,
    iconOverride: @Composable ((Any?) -> Any?) = { it },
) {
    val shaped = modifier.clip(CircleShape)
    if (token != null) {
        TokenIcon(token = token, modifier = shaped, iconOverride = iconOverride)
    } else {
        TokenIcon(image = null, modifier = shaped)
    }
}
