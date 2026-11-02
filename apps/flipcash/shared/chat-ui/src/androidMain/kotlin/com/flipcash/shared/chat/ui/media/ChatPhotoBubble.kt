package com.flipcash.shared.chat.ui.media

// kmp-audit: needs a bubble or link card that renders CodeButton, which lives in :ui:components androidMain (RuntimeShader frosted glass)

import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_chat_media_photo
import com.flipcash.core.resources.label_chat_media_unavailable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalDensity
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.captionRowsAround
import com.flipcash.shared.chat.ui.DetectedMention
import com.flipcash.shared.chat.ui.BareLinkCard
import com.flipcash.shared.chat.ui.CASH_CARD_WIDTH_FRACTION
import com.flipcash.shared.chat.ui.EmojiOnlyText
import com.flipcash.shared.chat.ui.JumboEmoji
import com.flipcash.shared.chat.ui.redactedText
import com.flipcash.shared.chat.ui.redactedTextColor
import com.flipcash.shared.chat.ui.buildMentionedText
import com.flipcash.shared.chat.ui.drawMentionPills
import com.flipcash.shared.chat.ui.mentionSpacingContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.Bubble
import com.flipcash.shared.common.ui.rememberBlurHashPainter
import com.getcode.theme.CodeTheme

internal const val PHOTO_BUBBLE_TAG = "chat_photo_bubble"
internal const val PHOTO_CAPTION_TAG = "chat_photo_caption"

private const val FADE_IN_MILLIS = 250

/**
 * A photo message.
 *
 * The photo fills a box [maxWidth] wide whose height follows [ChatMediaBubbleSizing]; the image is
 * center-cropped. Layers, bottom to top: the BlurHash from [blurHash]; then, in this order of
 * preference, [localModel] (the sender's own copy, shown at once) or [model] (the remote image,
 * fading in over 250ms once it loads). [progress] is drawn on top, for the upload capsule.
 *
 * [blurhashOnly] shows the BlurHash and loads nothing — a redacted photo, or one in a chat the
 * viewer has not joined — and is not clickable. [unavailable] does the same and says so in white
 * over the BlurHash. Otherwise [onClick] fires on a tap.
 *
 * A [caption] is its own text bubble a grid step below, the two sharing a run so the facing corners
 * are squared off. It is drawn as a text message's body is: [mentions] as pills, [redacted] hidden,
 * and a Flipcash [linkCard] drawn as a text message's is: in place of the caption bubble when the
 * caption is [cardAlone] -- only its link -- and otherwise under the caption's text, which has the
 * link removed, with any text after the link in a bubble below the card. A caption of nothing but emoji is drawn large with no bubble, both as in a
 * text message. A caption never draws a web card: senders send the preview as its own message.
 *
 * @param photoModifier applied to the photo box itself, which is what the overlay measures and hides.
 * @param onLoadError reports why the remote [model] failed to load, so the host can decide it is [unavailable].
 * @param model what Coil loads for the remote image: a URL, a file, or an `ImageRequest`.
 * @param localModel what Coil loads for a photo that is on this device already; wins over [model].
 * @param sentPreview a decoded copy of a photo this device sent. It replaces the BlurHash, so the
 * photo is fully drawn from the first frame, and [model] or [localModel] swap in over it unfaded.
 */
@Composable
fun ChatPhotoBubble(
    isFromSelf: Boolean,
    maxWidth: Dp,
    imageWidth: Int?,
    imageHeight: Int?,
    blurHash: String?,
    modifier: Modifier = Modifier,
    model: Any? = null,
    localModel: Any? = null,
    sentPreview: ImageBitmap? = null,
    caption: String? = null,
    mentions: List<DetectedMention> = emptyList(),
    onMentionClick: ((String) -> Unit)? = null,
    linkCard: LinkCard? = null,
    cardAlone: Boolean = false,
    onCardLongClick: (() -> Unit)? = null,
    onCardDoubleClick: (() -> Unit)? = null,
    interactive: Boolean = true,
    redacted: Boolean = false,
    position: BubblePosition = BubblePosition.Solo,
    blurhashOnly: Boolean = false,
    unavailable: Boolean = false,
    progress: (@Composable BoxScope.() -> Unit)? = null,
    photoModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onLoadError: ((Throwable) -> Unit)? = null,
) {
    val size = ChatMediaBubbleSizing.size(imageWidth, imageHeight, maxWidth.value)
    val hasCaption = !caption.isNullOrEmpty()
    // An emoji-only caption is drawn as a text message's is: large, with no bubble. Only a bubble
    // is squared off against the photo.
    val jumbo = hasCaption && EmojiOnlyText.clusterCountOrNull(caption.orEmpty()) != null
    val captionBubbled = hasCaption && !jumbo
    val tappable = !blurhashOnly && !unavailable && onClick != null

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
        horizontalAlignment = if (isFromSelf) Alignment.End else Alignment.Start,
    ) {
        Bubble(
            isFromSelf = isFromSelf,
            position = if (captionBubbled) position.withCaptionBelow() else position,
            maxWidth = maxWidth,
            horizontalPadding = CodeTheme.dimens.none,
            verticalPadding = CodeTheme.dimens.none,
            bare = true,
            onClick = if (tappable) onClick else null,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
        ) {
            val photoDescription = stringResource(Res.string.label_chat_media_photo)
            Box(
                modifier = Modifier
                    .size(size.width.dp, size.height.dp)
                    .then(photoModifier)
                    .testTag(PHOTO_BUBBLE_TAG)
                    .semantics { contentDescription = photoDescription },
            ) {
                PhotoLayers(
                    blurHash = blurHash,
                    model = model,
                    localModel = localModel,
                    sentPreview = sentPreview,
                    blurhashOnly = blurhashOnly || unavailable,
                    onLoadError = onLoadError,
                )
                if (unavailable) {
                    Text(
                        text = stringResource(Res.string.label_chat_media_unavailable),
                        style = CodeTheme.typography.textSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = CodeTheme.dimens.staticGrid.x3),
                    )
                } else if (progress != null) {
                    progress()
                }
            }
        }

        if (jumbo) {
            JumboEmoji(
                text = caption.orEmpty(),
                isFromSelf = isFromSelf,
                position = position,
                maxWidth = maxWidth,
            )
        } else if (hasCaption) {
            if (cardAlone && linkCard != null) {
                // A caption that is only its link is the card alone, as a text message is: no
                // caption row and no link text.
                BareLinkCard(
                    card = linkCard,
                    isFromSelf = isFromSelf,
                    position = position.withPhotoAbove(),
                    maxWidth = if (linkCard is LinkCard.Message) {
                        maxWidth * CASH_CARD_WIDTH_FRACTION
                    } else {
                        maxWidth
                    },
                    onLongClick = onCardLongClick,
                    onDoubleClick = onCardDoubleClick,
                    interactive = interactive,
                )
                return@Column
            }
            val rows = remember(linkCard, caption, mentions) {
                linkCard?.captionRowsAround(caption.orEmpty(), mentions)
            }
            if (rows == null || linkCard == null) {
                CaptionBubble(
                    text = caption.orEmpty(),
                    mentions = mentions,
                    onMentionClick = onMentionClick,
                    isFromSelf = isFromSelf,
                    position = position.withPhotoAbove(),
                    maxWidth = maxWidth,
                    redacted = redacted,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                )
                return@Column
            }
            // Words around a card: the rows a text message splits into -- the text before the
            // link, the card, the text after -- stacked under the photo. The first row joins the
            // photo's run; the rest sit as a split text message's rows do.
            val leading = rows.leading
            val trailing = rows.trailing
            if (leading != null) {
                CaptionBubble(
                    text = leading.first,
                    mentions = leading.second,
                    onMentionClick = onMentionClick,
                    isFromSelf = isFromSelf,
                    position = position.withPhotoAbove(),
                    maxWidth = maxWidth,
                    redacted = redacted,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                )
            }
            BareLinkCard(
                card = linkCard,
                isFromSelf = isFromSelf,
                position = if (leading == null) position.withPhotoAbove() else position,
                maxWidth = if (linkCard is LinkCard.Message) maxWidth * CASH_CARD_WIDTH_FRACTION else maxWidth,
                onLongClick = onCardLongClick,
                onDoubleClick = onCardDoubleClick,
                interactive = interactive,
            )
            if (trailing != null) {
                CaptionBubble(
                    text = trailing.first,
                    mentions = trailing.second,
                    onMentionClick = onMentionClick,
                    isFromSelf = isFromSelf,
                    position = position,
                    maxWidth = maxWidth,
                    redacted = redacted,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                )
            }
        }
    }
}

/** A caption's text in the bubble a text message draws its body in. */
@Composable
private fun CaptionBubble(
    text: String,
    mentions: List<DetectedMention>,
    onMentionClick: ((String) -> Unit)?,
    isFromSelf: Boolean,
    position: BubblePosition,
    maxWidth: Dp,
    redacted: Boolean,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
) {
    Bubble(
        isFromSelf = isFromSelf,
        position = position.withPhotoAbove(),
        maxWidth = maxWidth,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
    ) {
        // The same body a text bubble builds, so a handle here is a pill and a tap on it
        // opens the person, as it does in text.
        val linkStyle = SpanStyle(
            color = CodeTheme.colors.textMain,
            textDecoration = TextDecoration.Underline,
        )
        val mentionStyle = SpanStyle(
            color = CodeTheme.colors.textMain,
            textDecoration = TextDecoration.None,
        )
        val currentOnMentionClick by rememberUpdatedState(onMentionClick)
        val mentionsTappable = onMentionClick != null
        val mentioned = remember(text, mentions, linkStyle, mentionStyle, mentionsTappable) {
            buildMentionedText(
                text = text,
                mentions = mentions,
                linkStyle = linkStyle,
                mentionStyle = mentionStyle,
                onMentionClick = if (mentionsTappable) {
                    { username -> currentOnMentionClick?.invoke(username) }
                } else {
                    null
                },
            )
        }
        val handles = mentioned.handles
        val density = LocalDensity.current
        val inlineContent = remember(handles.isNotEmpty(), density) {
            if (handles.isEmpty()) {
                emptyMap()
            } else {
                val (slot, spacing) = mentionSpacingContent(density)
                mapOf(slot to spacing)
            }
        }
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = mentioned.text,
            inlineContent = inlineContent,
            style = CodeTheme.typography.textMessage,
            color = if (redacted) {
                redactedTextColor(CodeTheme.colors.textMain)
            } else {
                CodeTheme.colors.textMain
            },
            onTextLayout = { layout = it },
            modifier = Modifier
                .testTag(PHOTO_CAPTION_TAG)
                .then(if (redacted) Modifier.redactedText() else Modifier)
                .then(
                    if (handles.isEmpty()) {
                        Modifier
                    } else {
                        Modifier.drawBehind { layout?.let { drawMentionPills(it, handles) } }
                    },
                ),
        )
    }
}

/** Whether [this] caption is drawn in a bubble of its own: any caption but an emoji-only one. */
internal fun String?.drawsCaptionBubble(): Boolean =
    !isNullOrEmpty() && EmojiOnlyText.clusterCountOrNull(this) == null

/** The photo's place in a run once a caption follows it: the caption now closes the run. */
internal fun BubblePosition.withCaptionBelow(): BubblePosition = when (this) {
    BubblePosition.Solo -> BubblePosition.First
    BubblePosition.Last -> BubblePosition.Middle
    else -> this
}

/** The caption's place in a run: the photo above it is part of the run. */
internal fun BubblePosition.withPhotoAbove(): BubblePosition = when (this) {
    BubblePosition.Solo -> BubblePosition.Last
    BubblePosition.First -> BubblePosition.Middle
    else -> this
}

@Composable
private fun PhotoLayers(
    blurHash: String?,
    model: Any?,
    localModel: Any?,
    sentPreview: ImageBitmap?,
    blurhashOnly: Boolean,
    onLoadError: ((Throwable) -> Unit)?,
) {
    val blur = rememberBlurHashPainter(blurHash)
    val reduceMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize().background(CodeTheme.colors.surfaceVariant)) {
        if (sentPreview != null && !blurhashOnly) {
            Image(
                bitmap = sentPreview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (blur != null && (blurhashOnly || localModel == null)) {
            Image(
                painter = blur,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (blurhashOnly) return@Box

        when {
            localModel != null -> AsyncImage(
                model = localModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            model != null -> {
                var loaded by remember(model) { mutableStateOf(false) }
                // Our own photo replaces the same picture, so it is swapped in, not faded.
                val alpha by animateFloatAsState(
                    targetValue = if (loaded) 1f else 0f,
                    animationSpec = if (reduceMotion || sentPreview != null) snap() else tween(FADE_IN_MILLIS),
                    label = "photoFadeIn",
                )
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = {
                        loaded = it is AsyncImagePainter.State.Success
                        if (it is AsyncImagePainter.State.Error) onLoadError?.invoke(it.result.throwable)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha },
                )
            }
        }
    }
}

// A valid 4x3 BlurHash used only by previews.
private const val PREVIEW_HASH = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_PhotoBubble_Placeholder() {
    ChatPhotoBubble(
        isFromSelf = true,
        maxWidth = CodeTheme.dimens.staticGrid.x20 * 2.5f,
        imageWidth = 4032,
        imageHeight = 3024,
        blurHash = PREVIEW_HASH,
        progress = { ChatPhotoProgressOverlay(ChatPhotoPhase.Uploading(0.4f)) },
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_PhotoBubble_Caption() {
    ChatPhotoBubble(
        isFromSelf = false,
        maxWidth = CodeTheme.dimens.staticGrid.x20 * 2.5f,
        imageWidth = 3024,
        imageHeight = 4032,
        blurHash = PREVIEW_HASH,
        caption = "from today",
        blurhashOnly = true,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_PhotoBubble_Unavailable() {
    ChatPhotoBubble(
        isFromSelf = false,
        maxWidth = CodeTheme.dimens.staticGrid.x20 * 2.5f,
        imageWidth = 1000,
        imageHeight = 1000,
        blurHash = PREVIEW_HASH,
        unavailable = true,
    )
}
