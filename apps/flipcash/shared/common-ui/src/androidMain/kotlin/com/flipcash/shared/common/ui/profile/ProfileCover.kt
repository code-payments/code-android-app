package com.flipcash.shared.common.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.flipcash.app.core.media.LocalMediaUrlResolver
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

/**
 * Downloads [image] into the cache entry a full-width [ProfileCover] will read, so the profile
 * draws the picture on its first frame instead of the plain surface colour.
 *
 * Covers are cached under their blob id, not their URL (see [ProfileAvatar]), so the profile hits
 * this entry however long ago the URL was minted. The width stands in for the cover's longest side,
 * which is how [ProfileCover] picks its rendition on a phone. Nothing is drawn.
 */
@Composable
fun PrefetchProfileCover(image: MediaItem?, access: BlobAccessContext) {
    val context = LocalContext.current
    val resolver = LocalMediaUrlResolver.current
    val targetPx = LocalWindowInfo.current.containerSize.width
    LaunchedEffect(image, access, targetPx, resolver) {
        if (image == null || resolver == null || targetPx <= 0) return@LaunchedEffect
        val cacheKey = image.cacheKeyForSize(targetPx) ?: return@LaunchedEffect
        val url = resolver.urlForSize(image, targetPx, access) ?: return@LaunchedEffect
        // Enqueued rather than executed: the download outlives this composable, so leaving the
        // chat before it lands still leaves the cover cached for the next visit.
        SingletonImageLoader.get(context).enqueue(
            ImageRequest.Builder(context)
                .data(url.toUri())
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                // The rendition is already sized for the screen; decoding it whole keeps the
                // memory entry valid for the cover's own request.
                .size(Size.ORIGINAL)
                .build()
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_ProfileCover_NoPicture() {
    ProfileCover(image = null, access = BlobAccessContext.Owned)
}
