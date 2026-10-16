package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.flipcash.app.messenger.internal.link.ComposerLinkPreviewState
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeCircularProgressIndicator

internal const val COMPOSER_LINK_CARD_TAG = "composer_link_card"

/**
 * The draft link's preview as the recipient will see it (iOS `ComposerLinkChip`): the page's image,
 * host, description and title, a spinner while the page loads or its image uploads, and a frosted
 * remove button inside the card's top-end corner, like a photo chip's.
 */
@Composable
internal fun ComposerLinkCard(
    state: ComposerLinkPreviewState,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = CodeTheme.dimens.staticGrid
    val label = when (state) {
        is ComposerLinkPreviewState.Loading ->
            stringResource(R.string.description_chatLinkPreviewLoading, state.host)
        is ComposerLinkPreviewState.Ready ->
            stringResource(R.string.description_chatLinkPreview, state.host, state.title)
    }
    // The card is the haze's source, so the remove button frosts whatever sits under it.
    val hazeState = rememberHazeState()
    Box(modifier.fillMaxWidth()) {
        Box(Modifier.testTag(COMPOSER_LINK_CARD_TAG)) {
            Row(
                modifier = Modifier
                    .widthIn(max = MaxCardWidth)
                    .clip(RoundedCornerShape(CardCorner))
                    .hazeSource(hazeState)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(grid.x3)
                    // Keeps the text clear of the remove button in the top-end corner.
                    .padding(end = grid.x4)
                    .semantics { contentDescription = label },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(state)
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    when (state) {
                        is ComposerLinkPreviewState.Loading -> HostText(state.host)
                        is ComposerLinkPreviewState.Ready -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Different sizes, so they share a baseline rather than a top edge.
                                HostText(state.host, Modifier.alignByBaseline())
                                state.description?.let {
                                    Text(
                                        text = it,
                                        modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                                        style = CodeTheme.typography.caption,
                                        color = CodeTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = state.title,
                                style = CodeTheme.typography.caption,
                                color = CodeTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            RemoveButton(onRemove, hazeState, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun HostText(host: String, modifier: Modifier = Modifier) {
    Text(
        text = host,
        modifier = modifier,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Thumbnail(state: ComposerLinkPreviewState) {
    val grid = CodeTheme.dimens.staticGrid
    Box(
        modifier = Modifier
            .size(ImageSize)
            .clip(RoundedCornerShape(ImageCorner))
            .background(CodeTheme.colors.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state is ComposerLinkPreviewState.Loading -> Spinner()
            state is ComposerLinkPreviewState.Ready && state.image != null -> {
                AsyncImage(
                    model = state.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(ImageSize),
                )
                if (state.imageUploading) {
                    Box(Modifier.size(ImageSize).background(Color.Black.copy(alpha = 0.35f)))
                    Spinner()
                }
            }
            else -> Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(grid.x5),
            )
        }
    }
}

@Composable
private fun Spinner() {
    CodeCircularProgressIndicator(
        strokeWidth = CodeTheme.dimens.thickBorder,
        color = Color.White,
        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
    )
}

@Composable
private fun RemoveButton(onRemove: () -> Unit, hazeState: HazeState, modifier: Modifier) {
    val grid = CodeTheme.dimens.staticGrid
    // Inside the card's top-end corner, the size of a photo chip's remove button.
    Box(
        modifier = modifier
            .padding(grid.x1)
            .size(grid.x4)
            .clip(CircleShape)
            .hazeBlur(HazeInput.Sources(hazeState), HazeMaterials.ultraThin(containerColor = Color.Black))
            .clickable(role = Role.Button, onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.description_chatLinkPreviewRemove),
            tint = Color.White,
            modifier = Modifier.size(grid.x2),
        )
    }
}

private val ImageSize = 44.dp
private val ImageCorner = 8.dp
private val CardCorner = 14.dp
private val MaxCardWidth = 280.dp
