package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.shared.common.ui.ProfileAvatar
import com.getcode.theme.CodeTheme

internal val ProfileCoverHeight = 214.dp

/**
 * Width over height of the cover as it sits on a profile (a 375dp screen by [ProfileCoverHeight]).
 * Anything previewing a cover at another size keeps this ratio so it crops the way the profile will.
 */
const val ProfileCoverAspectRatio = 375f / 214f

/**
 * The full-bleed picture at the top of a profile. It is drawn under the status bar, so the caller
 * lays it out edge to edge and this does not pad for insets.
 *
 * The [image] is scaled to fill and clipped, with its BlurHash as the placeholder while the
 * rendition downloads; with no picture the cover is the plain surface colour. Loading is
 * [ProfileAvatar]'s — the same rendition, re-mint and cache path every avatar uses — and [access]
 * names the surface the picture is read from, which is what authorizes re-minting it.
 *
 * [height] is the profile's own by default. Pass [Dp.Unspecified] to size the cover from [modifier]
 * instead, e.g. with `aspectRatio(ProfileCoverAspectRatio)`.
 */
@Composable
fun ProfileCover(
    image: MediaItem?,
    access: BlobAccessContext,
    modifier: Modifier = Modifier,
    height: Dp = ProfileCoverHeight,
) {
    ProfileAvatar(
        image = image,
        access = access,
        modifier = modifier
            .fillMaxWidth()
            .then(if (height.isSpecified) Modifier.height(height) else Modifier),
        background = SolidColor(CodeTheme.colors.surfaceVariant),
        fallback = { },
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileCover_NoPicture() {
    ProfileCover(image = null, access = BlobAccessContext.Owned)
}
