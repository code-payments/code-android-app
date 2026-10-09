package com.flipcash.shared.chat.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.flipcash.app.core.ui.shimmer
import com.flipcash.core.R
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LocalWebLinkPreviewMode
import com.flipcash.shared.chat.models.LocalWebPreviewImageLoader
import com.flipcash.shared.chat.models.WebLinkPreviewMode
import com.getcode.ui.core.addIf
import com.getcode.theme.CodeTheme

internal const val WEB_LINK_CARD_TAG = "web_link_card"
internal const val WEB_LINK_CHIP_TAG = "web_link_chip"
internal const val WEB_LINK_IMAGE_TAG = "web_link_image"
internal const val WEB_LINK_PLACEHOLDER_TAG = "web_link_placeholder"

/** Width over height of the picture, the shape Open Graph images are sized for. */
private const val IMAGE_ASPECT = 1.91f
/** The app is dark only, so the panel and the chip are white at a low alpha over the bubble. */
private val PANEL_FILL = Color.White.copy(alpha = 0.08f)

/** A bare card's outline: the group invite card's stroke, so both bare cards draw the same edge. */
private val BARE_STROKE = GroupInviteCardDefaults.STROKE
private val CHIP_FILL = Color.White.copy(alpha = 0.12f)
private const val TITLE_LINES = 2
private val TITLE_BAR_HEIGHT = 14.dp
private const val TITLE_BAR_SHORT_FRACTION = 0.6f
private const val DESCRIPTION_LINES = 2

/**
 * What a web card draws now: [resolved] once the page has answered, [chipHost] while the viewer is
 * outside the group and has not asked, and neither otherwise. A card still loading, an empty
 * answer, and a lookup that failed all draw nothing under a message's text.
 *
 * [loading] is true while a lookup is actually under way or about to be: automatic mode, or the chip
 * tapped, with no answer yet. A link-only message holds its place with a placeholder for as long as
 * it is, and [host] names the link's own host for it.
 */
@Stable
internal class WebLinkCardState(
    val url: String,
    val resolved: LinkCard.Web.State.Resolved?,
    val chipHost: String?,
    val loading: Boolean,
    val host: String,
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
    val fetching = mode == WebLinkPreviewMode.Automatic || (mode == WebLinkPreviewMode.TapToLoad && asked)
    val live = rememberResolvedCard(
        card = card,
        fetch = fetching,
    ) as? LinkCard.Web ?: card
    val showChip = mode == WebLinkPreviewMode.TapToLoad && !asked &&
        live.state == LinkCard.Web.State.Loading
    return WebLinkCardState(
        url = card.url,
        // Off draws no card at all, even from an answer held from before the flag was switched off.
        resolved = (live.state as? LinkCard.Web.State.Resolved)?.takeIf { mode != WebLinkPreviewMode.Off },
        chipHost = if (showChip) chipHostOf(card.url) else null,
        loading = fetching && live.state == LinkCard.Web.State.Loading,
        host = chipHostOf(card.url),
        ask = { asked = true },
    )
}

/** The host a chip names: lower case, without a leading "www.". */
internal fun chipHostOf(url: String): String =
    Uri.parse(url).host.orEmpty().lowercase().removePrefix("www.")

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
            bareShape = bareShape,
        )

        // Only a bare card holds its place: under text, a card that has not landed draws nothing.
        state.loading && bareShape != null -> WebPlaceholder(
            host = state.host,
            url = state.url,
            modifier = modifier,
            interactive = interactive,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            bareShape = bareShape,
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
 * What a link-only message draws while its card loads: the card's own proportions with the page's
 * parts as shimmering bars, so the real card fills the same slot. Acts as the card does -- a tap
 * opens the link, a long press and a double tap go to the message -- and reads out the link itself,
 * since the text it replaces is not on screen.
 */
@Composable
private fun WebPlaceholder(
    host: String,
    url: String,
    interactive: Boolean,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    bareShape: Shape,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val bar = RoundedCornerShape(CodeTheme.dimens.staticGrid.x1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(CodeTheme.dimens.border, BARE_STROKE, bareShape)
            .clip(bareShape)
            .background(PANEL_FILL)
            .testTag(WEB_LINK_PLACEHOLDER_TAG)
            .semantics {
                contentDescription = url
                linkCardShape = bareShape
            }
            .combinedClickable(
                enabled = interactive,
                onClick = { uriHandler.openUri(url) },
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
                hapticFeedbackEnabled = false,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(IMAGE_ASPECT)
                .shimmer(RectangleShape),
        )
        Column(
            modifier = Modifier.padding(CodeTheme.dimens.staticGrid.x2),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
        ) {
            Text(
                text = host,
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT).shimmer(bar))
            Box(Modifier.fillMaxWidth(TITLE_BAR_SHORT_FRACTION).height(TITLE_BAR_HEIGHT).shimmer(bar))
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
