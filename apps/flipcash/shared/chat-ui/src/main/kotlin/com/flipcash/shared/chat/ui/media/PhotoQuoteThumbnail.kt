package com.flipcash.shared.chat.ui.media

import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import androidx.compose.runtime.remember

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.getcode.theme.CodeTheme
import coil3.compose.AsyncImage
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.common.ui.rememberBlurHashPainter

internal const val PHOTO_QUOTE_THUMBNAIL_TAG = "photo_quote_thumbnail"

/**
 * The square beside a quoted photo's snippet, in the reply strip and inside a quoting bubble.
 * BlurHash underneath, [model] over it once it loads. A [redacted] photo draws nothing at all:
 * the snippet stands alone, so the quote does not leak what the photo shows.
 */
@Composable
fun PhotoQuoteThumbnail(
    model: Any?,
    blurHash: String?,
    redacted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (redacted) return
    val blur = rememberBlurHashPainter(blurHash)
    val size = CodeTheme.dimens.staticGrid.x8
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(CodeTheme.dimens.staticGrid.x1))
            .testTag(PHOTO_QUOTE_THUMBNAIL_TAG),
    ) {
        if (blur != null) {
            Image(
                painter = blur,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** [PhotoQuoteThumbnail] for a quoted photo, drawn from what the snippet carries. */
@Composable
fun QuotedPhotoThumbnail(snippet: ChatQuoteSnippet.Photo, modifier: Modifier = Modifier) {
    val rendition = snippet.rendition
    val chatId = snippet.chatId
    val model = remember(snippet) {
        if (rendition == null || chatId == null || snippet.redacted) null
        else ChatPhoto(chatId, rendition, snippet.senderId, snippet.sealed, snippet.redacted)
    }
    PhotoQuoteThumbnail(
        model = model,
        blurHash = rendition?.blob?.image?.blurhash,
        redacted = snippet.redacted,
        modifier = modifier,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_PhotoQuoteThumbnail() {
    PhotoQuoteThumbnail(model = null, blurHash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj", redacted = false)
}
