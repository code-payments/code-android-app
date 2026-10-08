package com.flipcash.shared.chat.ui

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.flipcash.core.R
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LocalWebLinkPreviewMode
import com.flipcash.shared.chat.models.LocalWebPreviewImageLoader
import com.flipcash.shared.chat.models.WebLinkPreviewMode
import com.getcode.theme.CodeTheme

internal const val WEB_LINK_CARD_TAG = "web_link_card"
internal const val WEB_LINK_CHIP_TAG = "web_link_chip"
internal const val WEB_LINK_IMAGE_TAG = "web_link_image"

/** Width over height of the picture, the shape Open Graph images are sized for. */
private const val IMAGE_ASPECT = 1.91f
/** The app is dark only, so the panel and the chip are white at a low alpha over the bubble. */
private val PANEL_FILL = Color.White.copy(alpha = 0.08f)
private val CHIP_FILL = Color.White.copy(alpha = 0.12f)
private const val TITLE_LINES = 2
private const val DESCRIPTION_LINES = 2

/**
 * What a web card draws now: [resolved] once the page has answered, [chipHost] while the viewer is
 * outside the group and has not asked, and neither otherwise. A card still loading in automatic
 * mode, an empty answer, and a fetch that failed after the chip was tapped all draw nothing.
 */
@Stable
internal class WebLinkCardState(
    val url: String,
    val resolved: LinkCard.Web.State.Resolved?,
    val chipHost: String?,
    val ask: () -> Unit,
)

/**
 * The state of [card] for the viewer: asks for its page on its own, or only after the chip is
 * tapped when the viewer is outside the group (see [LocalWebLinkPreviewMode]).
 *
 * The tap is remembered across scrolling, so a message drawn again after a failed fetch asks again
 * with no chip.
 */
@Composable
internal fun rememberWebLinkCard(card: LinkCard.Web): WebLinkCardState {
    val mode = LocalWebLinkPreviewMode.current
    var asked by rememberSaveable(card.url) { mutableStateOf(false) }
    val live = rememberResolvedCard(
        card = card,
        fetch = mode == WebLinkPreviewMode.Automatic || (mode == WebLinkPreviewMode.TapToLoad && asked),
    ) as? LinkCard.Web ?: card
    val showChip = mode == WebLinkPreviewMode.TapToLoad && !asked &&
        live.state == LinkCard.Web.State.Loading
    return WebLinkCardState(
        url = card.url,
        // Off draws no card at all, even from an answer held from before the flag was switched off.
        resolved = (live.state as? LinkCard.Web.State.Resolved)?.takeIf { mode != WebLinkPreviewMode.Off },
        chipHost = if (showChip) chipHostOf(card.url) else null,
        ask = { asked = true },
    )
}

/** The host a chip names: lower case, without a leading "www.". */
private fun chipHostOf(url: String): String =
    Uri.parse(url).host.orEmpty().lowercase().removePrefix("www.")

/**
 * The card under a message's text, or the chip that asks for it. The whole card is one target that
 * opens the page through [LocalUriHandler], so the host's "leaving Flipcash" warning applies as it
 * does to the link text; a long press goes to the message's own.
 */
@Composable
internal fun WebLinkCard(
    state: WebLinkCardState,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactive: Boolean = true,
) {
    val resolved = state.resolved
    val chipHost = state.chipHost
    when {
        resolved != null -> WebPreview(
            preview = resolved,
            url = state.url,
            modifier = modifier,
            interactive = interactive,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
        )

        chipHost != null -> ShowPreviewChip(host = chipHost, onClick = state.ask, modifier = modifier)
    }
}

@Composable
private fun WebPreview(
    preview: LinkCard.Web.State.Resolved,
    url: String,
    interactive: Boolean,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // The link that was sent, never one the page named.
    val uriHandler = LocalUriHandler.current
    val shape = RoundedCornerShape(CodeTheme.dimens.staticGrid.x2)
    Column(
        modifier = modifier
            .padding(top = CodeTheme.dimens.staticGrid.x2)
            .fillMaxWidth()
            .clip(shape)
            .background(PANEL_FILL)
            .testTag(WEB_LINK_CARD_TAG)
            .combinedClickable(
                enabled = interactive,
                onClick = { uriHandler.openUri(url) },
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
                // The row's select plays the tick; a second, platform haptic doubles it.
                hapticFeedbackEnabled = false,
            ),
    ) {
        PreviewImage(preview.imageUrl)
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
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    maxLines = DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The page's picture, through the preview loader only. With no loader, no picture: the app's own
 * loader keeps cookies and follows any redirect. A picture that fails to load takes its slot with
 * it, so the card closes up around the text rather than leaving a hole.
 */
@Composable
private fun PreviewImage(imageUrl: String?) {
    val loader = LocalWebPreviewImageLoader.current ?: return
    // The loader handles file and content addresses too, and the address came from a page.
    if (imageUrl == null || !imageUrl.startsWith("https://")) return
    var failed by remember(imageUrl) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = imageUrl,
        imageLoader = loader,
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

// Chip copy is pending UX review, with the iOS string.
@Composable
private fun ShowPreviewChip(host: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = stringResource(R.string.action_webLinkCard_showPreview, host),
        style = CodeTheme.typography.caption,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(top = CodeTheme.dimens.staticGrid.x2)
            .clip(shape)
            .background(CHIP_FILL)
            .combinedClickable(onClick = onClick, hapticFeedbackEnabled = false)
            .padding(
                horizontal = CodeTheme.dimens.staticGrid.x2,
                vertical = CodeTheme.dimens.staticGrid.x1,
            )
            .testTag(WEB_LINK_CHIP_TAG),
    )
}
