package com.flipcash.shared.chat.ui

// kmp-audit: needs a bubble or link card that renders CodeButton, which lives in :ui:components androidMain (RuntimeShader frosted glass)

import com.flipcash.shared.chat.media.ChatPhoto
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.ui.core.addIf
import com.getcode.theme.CodeTheme

internal const val WEB_LINK_CARD_TAG = "web_link_card"
internal const val WEB_LINK_IMAGE_TAG = "web_link_image"

/** Width over height of the picture, the shape Open Graph images are sized for. */
private const val IMAGE_ASPECT = 1.91f
/** The app is dark only, so the panel and the chip are white at a low alpha over the bubble. */
private val PANEL_FILL = Color.White.copy(alpha = 0.08f)

/** A bare card's outline: the group invite card's stroke, so both bare cards draw the same edge. */
private val BARE_STROKE = GroupInviteCardDefaults.STROKE
private const val TITLE_LINES = 2
private const val DESCRIPTION_LINES = 2

/**
 * What a web card draws: [resolved] when the message carries the preview its sender built, and
 * nothing otherwise. The reader contacts no site, so a link without a preview draws as text only.
 */
@Stable
internal class WebLinkCardState(
    val url: String,
    val resolved: LinkCard.Web.State.Resolved?,
)

/** The state of [card]: its sender-built preview if it came with one. */
@Composable
internal fun rememberWebLinkCard(card: LinkCard.Web): WebLinkCardState =
    WebLinkCardState(url = card.url, resolved = card.state as? LinkCard.Web.State.Resolved)

/**
 * The card under a message's text, or the chip that asks for it. The whole card is one target that
 * opens the page through [LocalUriHandler], so the host's "leaving Flipcash" warning applies as it
 * does to the link text; a long press goes to the message's own.
 *
 * [bareShape] set draws the card as the whole message, with no bubble around it: the panel takes the
 * bubble's corners instead of its own and sits flush, with no gap above it for text that is not
 * there.
 */
@Composable
internal fun WebLinkCard(
    state: WebLinkCardState,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactive: Boolean = true,
    bareShape: Shape? = null,
) {
    val resolved = state.resolved ?: return
    WebPreview(
        preview = resolved,
        url = state.url,
        modifier = modifier,
        interactive = interactive,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        bareShape = bareShape,
    )
}

@Composable
private fun WebPreview(
    preview: LinkCard.Web.State.Resolved,
    url: String,
    interactive: Boolean,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    bareShape: Shape?,
    modifier: Modifier = Modifier,
) {
    // The link that was sent, never one the page named.
    val uriHandler = LocalUriHandler.current
    val shape = bareShape ?: RoundedCornerShape(CodeTheme.dimens.staticGrid.x2)
    Column(
        modifier = modifier
            .addIf(bareShape == null) { Modifier.padding(top = CodeTheme.dimens.staticGrid.x2) }
            .fillMaxWidth()
            // A bare card has no bubble for an edge, and a dark opaque page image can match the
            // chat's own colour; the outline is drawn over the image so the edge always shows.
            .addIf(bareShape != null) { Modifier.border(CodeTheme.dimens.border, BARE_STROKE, shape) }
            .clip(shape)
            .background(PANEL_FILL)
            .testTag(WEB_LINK_CARD_TAG)
            .addIf(bareShape != null) { Modifier.semantics { linkCardShape = bareShape!! } }
            .combinedClickable(
                enabled = interactive,
                onClick = { uriHandler.openUri(url) },
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
                // The row's select plays the tick; a second, platform haptic doubles it.
                hapticFeedbackEnabled = false,
            ),
    ) {
        preview.photo?.let { SenderPreviewImage(it) }
        Column(
            modifier = Modifier.padding(CodeTheme.dimens.staticGrid.x2),
        ) {
            Text(
                text = preview.host,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview.title,
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textMain,
                maxLines = TITLE_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            preview.description?.let { description ->
                Text(
                    text = description,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The image of a preview the sender built: a chat photo, sealed for the chat when the message is,
 * drawn through the app's own loader rather than the web one. A failure closes the slot, so the card closes up around the text rather than leaving a hole.
 */
@Composable
private fun SenderPreviewImage(photo: ChatPhoto) {
    var failed by remember(photo) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(IMAGE_ASPECT)
            .background(CodeTheme.colors.textSecondary.copy(alpha = 0.15f))
            .testTag(WEB_LINK_IMAGE_TAG),
    )
}
