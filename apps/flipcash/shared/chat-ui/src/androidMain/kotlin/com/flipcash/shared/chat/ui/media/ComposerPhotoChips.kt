package com.flipcash.shared.chat.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.chat.ChatInputDefaults

/** Where a staged photo is on its way to being sendable. */
sealed interface ComposerPhotoChipState {
    data object Preparing : ComposerPhotoChipState
    data object Uploading : ComposerPhotoChipState
    data object Uploaded : ComposerPhotoChipState

    /** The upload failed. [retryable] says whether trying again can help. */
    data class Failed(val retryable: Boolean) : ComposerPhotoChipState
}

/** A staged photo. [model] is whatever Coil loads for the thumbnail, typically a local `Uri`. */
data class ComposerPhotoChip(
    val id: String,
    val model: Any?,
    val state: ComposerPhotoChipState,
)

/** What a chip wears over its thumbnail. */
enum class ComposerPhotoBadge { None, Retry, Error }

/** No badge while the photo is preparing, uploading or uploaded; otherwise by [Failed.retryable]. */
fun composerPhotoBadge(state: ComposerPhotoChipState): ComposerPhotoBadge = when (state) {
    ComposerPhotoChipState.Preparing,
    ComposerPhotoChipState.Uploading,
    ComposerPhotoChipState.Uploaded -> ComposerPhotoBadge.None

    is ComposerPhotoChipState.Failed ->
        if (state.retryable) ComposerPhotoBadge.Retry else ComposerPhotoBadge.Error
}

internal const val CHIP_TAG_PREFIX = "composer_photo_chip_"

/** The staged-photo chip's measurements, from the grid. The attach surface lands a capture on a chip, so it reads them too. */
internal object PhotoChipDefaults {
    val Size: Dp @Composable get() = CodeTheme.dimens.staticGrid.x11
    val Corner: Dp @Composable get() = ChatInputDefaults.HeaderChipCorner
    val Spacing: Dp @Composable get() = CodeTheme.dimens.staticGrid.x2

    /**
     * How far the first and last chips sit in from the strip's edges, and the width of the fade there
     * (iOS `ComposerChipStrip.edgeInset`, passed as `leadingInset` = 14). The same number for both, so
     * chips at rest sit just clear of the fade and only scrolled-off ones are faded.
     */
    val EdgeInset: Dp @Composable get() = CodeTheme.dimens.staticGrid.x3
}

/** Which edges of the strip fade: only one with chips scrolled past it. */
internal data class ChipEdgeFades(val start: Boolean, val end: Boolean)

internal fun chipEdgeFades(canScrollBackward: Boolean, canScrollForward: Boolean) =
    ChipEdgeFades(start = canScrollBackward, end = canScrollForward)

/**
 * The staged photos above or inside the composer's field: a horizontally scrolling row of
 * thumbnails fading out at both edges. Each has a remove button at its top-end; a failed one is
 * scrimmed and shows either a retry arrow (tap retries) or a warning that it can't be sent. A tap
 * on a thumbnail itself does nothing.
 */
@Composable
fun ComposerPhotoChips(
    chips: List<ComposerPhotoChip>,
    onRemove: (id: String) -> Unit,
    onRetry: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    newestChipModifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val edgeInset = PhotoChipDefaults.EdgeInset
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            // The mask needs its own layer to cut through just this row's pixels.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fades = chipEdgeFades(listState.canScrollBackward, listState.canScrollForward)
                val edge = edgeInset.toPx().coerceAtMost(size.width / 2f)
                if (fades.start) {
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startX = 0f,
                            endX = edge,
                        ),
                        size = Size(edge, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (fades.end) {
                    drawRect(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = size.width - edge,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - edge, 0f),
                        size = Size(edge, size.height),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        contentPadding = PaddingValues(horizontal = edgeInset),
        horizontalArrangement = Arrangement.spacedBy(PhotoChipDefaults.Spacing),
    ) {
        items(chips, key = { it.id }) { chip ->
            PhotoChip(
                chip = chip,
                onRemove = { onRemove(chip.id) },
                onRetry = { onRetry(chip.id) },
                modifier = if (chip.id == chips.lastOrNull()?.id) newestChipModifier else Modifier,
            )
        }
    }
}

@Composable
private fun PhotoChip(
    chip: ComposerPhotoChip,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PhotoChipDefaults.Corner)
    val badge = composerPhotoBadge(chip.state)
    val chipSize = PhotoChipDefaults.Size
    val grid = CodeTheme.dimens.staticGrid
    // The remove button sits inside the thumbnail's corner, as on iOS, so the chip is the chip-sized square.
    Box(Modifier.testTag(CHIP_TAG_PREFIX + chip.id)) {
        Box(
            modifier = modifier
                .size(chipSize)
                .clip(shape)
                .background(CodeTheme.colors.surfaceVariant),
        ) {
            AsyncImage(
                model = chip.model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(chipSize),
            )
            if (badge != ComposerPhotoBadge.None) {
                Box(
                    Modifier
                        .size(chipSize)
                        .background(Color.Black.copy(alpha = 0.5f)),
                )
            }
            when (badge) {
                ComposerPhotoBadge.Retry -> {
                    val label = stringResource(R.string.description_chatPhotoRetry)
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = label,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(chipSize)
                            .clickable(role = Role.Button, onClick = onRetry)
                            .padding(grid.x3),
                    )
                }

                ComposerPhotoBadge.Error -> Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = stringResource(R.string.description_chatPhotoFailed),
                    tint = CodeTheme.colors.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(grid.x6),
                )

                ComposerPhotoBadge.None -> Unit
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(grid.x1)
                .size(grid.x4)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.description_chatPhotoRemove),
                tint = Color.White,
                modifier = Modifier.size(grid.x2),
            )
        }
    }
}

@Preview(widthDp = 320)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_Chips() {
    ComposerPhotoChips(
        chips = listOf(
            ComposerPhotoChip("a", null, ComposerPhotoChipState.Uploaded),
            ComposerPhotoChip("b", null, ComposerPhotoChipState.Uploading),
            ComposerPhotoChip("c", null, ComposerPhotoChipState.Failed(retryable = true)),
            ComposerPhotoChip("d", null, ComposerPhotoChipState.Failed(retryable = false)),
        ),
        onRemove = {},
        onRetry = {},
    )
}
