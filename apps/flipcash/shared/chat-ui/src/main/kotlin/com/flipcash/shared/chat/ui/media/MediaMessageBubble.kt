package com.flipcash.shared.chat.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.max
import androidx.compose.foundation.shape.RoundedCornerShape
import com.flipcash.shared.chat.ui.BubbleDefaults
import com.flipcash.shared.chat.ui.bubbleCorners
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.getcode.theme.CodeTheme
import com.flipcash.shared.chat.ui.BubbleCorners
import com.flipcash.services.chat.BlobOpenFailure
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.media.SentPhotoPreviews
import com.flipcash.shared.chat.media.ChatPhotoUnavailable
import com.flipcash.shared.chat.media.MediaSendProgress
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.ReceiptStatus
import com.flipcash.shared.chat.ui.Bubble
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ChatQuotePanel
import com.getcode.utils.TraceType
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.getcode.utils.trace

/**
 * Progress of the photo messages this device is sending, by pending client id hex. A getter, not a
 * map, so a bubble reads only its own entry and a tick on one upload doesn't recompose the others.
 */
val LocalChatMediaProgress = staticCompositionLocalOf<() -> Map<String, MediaSendProgress>> { { emptyMap() } }

/** The photo this row draws: the message's own, or the one a reply carries. */
/**
 * How a photo row in [chatId] draws and opens, or null when [this] holds no photo. Carries the real
 * sender, own id included: a sealed photo's key roles depend on it.
 */
fun ChatMessage.photoContext(chatId: ChatId, previewing: Boolean): PhotoMessageContext? =
    if (content.none { it.photoBody() != null }) null
    else PhotoMessageContext(
        chatId = chatId,
        senderId = senderId,
        sealed = encryption != null,
        redacted = redacted,
        previewing = previewing,
    )

fun MessageContent.photoBody(): MessageContent.Media? = when (this) {
    is MessageContent.Media -> this
    is MessageContent.Reply -> content.filterIsInstance<MessageContent.Media>().firstOrNull()
    else -> null
}

/** Whether a tap on a photo opens the viewer: only one that is showing, delivered, and loaded. */
internal fun canOpenPhoto(blurhashOnly: Boolean, failed: Boolean, unavailable: Boolean): Boolean =
    !blurhashOnly && !failed && !unavailable

/**
 * Whether [error] means the photo can't be drawn. A key that has not arrived yet can still open
 * later, so it leaves the BlurHash up instead.
 */
internal fun isPhotoUnavailable(error: Throwable): Boolean {
    val unavailable = generateSequence(error) { it.cause }.filterIsInstance<ChatPhotoUnavailable>().firstOrNull()
    return when (unavailable) {
        null -> true // Coil could not decode what it was given.
        is ChatPhotoUnavailable.Open -> unavailable.reason != BlobOpenFailure.KeyPending
        else -> true
    }
}

internal fun MediaSendProgress.toPhase(): ChatPhotoPhase = when (this) {
    MediaSendProgress.Preparing -> ChatPhotoPhase.Preparing
    is MediaSendProgress.Uploading -> ChatPhotoPhase.Uploading(fraction)
    MediaSendProgress.Processing -> ChatPhotoPhase.Processing
    MediaSendProgress.Sending -> ChatPhotoPhase.Sending
    MediaSendProgress.Sent -> ChatPhotoPhase.Sent
    is MediaSendProgress.Failed -> ChatPhotoPhase.Failed
}

/**
 * A photo message in the transcript, with its citation above it when it is a reply and its caption
 * under it. See [ChatPhotoBubble] for the drawing.
 */
@Composable
internal fun MediaMessageBubble(
    item: ChatListItem.ContentBubble,
    body: MessageContent.Media,
    position: BubblePosition,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    quote: ChatQuote? = null,
    onQuoteClick: (() -> Unit)? = null,
    interactive: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
) {
    val actionHandler = LocalChatActionHandler.current
    val rendition = body.items.firstOrNull()?.let { it.rendition(MediaItemRendition.Role.ORIGINAL) ?: it.renditions.firstOrNull() }
    val image = rendition?.blob?.image
    val info = item.photo
    // The optimistic row has no blob id yet and the staged file as its URL.
    val localUri = rendition?.takeIf { it.blobId.bytes.isEmpty() }?.blob?.downloadUrl?.takeIf { it.isNotEmpty() }
    // A photo sent from this process: its decoded copy is drawn from the first frame, through the
    // swap from the staged file to the stored blob, instead of the BlurHash.
    val sentPreview = remember(item.pendingClientIdHex) {
        item.pendingClientIdHex?.let(SentPhotoPreviews::forMessage)?.asImageBitmap()
    }
    val model = remember(rendition, info) {
        if (rendition == null || info == null || localUri != null) null
        else ChatPhoto(info.chatId, rendition, info.senderId, info.sealed, info.redacted)
    }

    val progressOf = LocalChatMediaProgress.current
    val progress by remember(item.pendingClientIdHex) {
        derivedStateOf { item.pendingClientIdHex?.let { progressOf()[it] } }
    }
    val failed = item.receiptStatus == ReceiptStatus.FAILED || progress is MediaSendProgress.Failed

    var loadError by remember(model) { mutableStateOf<Throwable?>(null) }
    val unavailable = loadError?.let(::isPhotoUnavailable) == true
    val blurhashOnly = info?.blurhashOnly == true

    val onClick: (() -> Unit)? = when {
        !interactive -> null
        failed -> ({ actionHandler(ChatAction.RetryMessage(item)) })
        canOpenPhoto(blurhashOnly, failed = false, unavailable = unavailable) && localUri == null ->
            ({ actionHandler(ChatAction.OpenPhoto(item)) })
        else -> null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x1),
        horizontalAlignment = if (item.isFromSelf) Alignment.End else Alignment.Start,
    ) {
        Box {
        ChatPhotoBubble(
            isFromSelf = item.isFromSelf,
            maxWidth = maxWidth,
            imageWidth = image?.width,
            imageHeight = image?.height,
            blurHash = image?.blurhash?.takeIf { it.isNotEmpty() },
            model = model,
            localModel = localUri,
            sentPreview = sentPreview,
            caption = body.caption?.text,
            position = position,
            blurhashOnly = blurhashOnly,
            unavailable = unavailable,
            progress = progress?.let { p ->
                { ChatPhotoProgressOverlay(p.toPhase()) }
            },
            photoModifier = Modifier.chatPhotoSource(
                // A photo this device is still sending has no model and cannot open.
                messageId = item.messageId.takeIf { onClick != null && !failed },
                model = model,
                imageWidth = image?.width,
                imageHeight = image?.height,
                corners = bubblePhotoCorners(
                    position = position
                        .let { if (body.caption?.text.isNullOrEmpty()) it else it.withCaptionBelow() },
                    isFromSelf = item.isFromSelf,
                ),
            ),
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
            onLoadError = {
                trace(tag = "MediaMessageBubble", message = "Photo failed to load: ${it.message}", type = TraceType.Log)
                loadError = it
            },
        )
            // The citation rides on the photo rather than in a bubble of its own above it, so the
            // reply reads as one message. Matched to the photo's box, not the row, so a long quote
            // wraps inside the photo instead of measuring against the row's full width.
            if (quote != null) {
                Box(Modifier.matchParentSize()) {
                    // Tighter than a text reply's surround: at the bubble's 12dp corner an 8dp gap
                    // leaves the chip a 4dp corner, which reads as square rather than parallel.
                    val inset = CodeTheme.dimens.staticGrid.x1
                    val chip = photoQuoteCorners(
                        photoTopStart = bubbleCorners(position, item.isFromSelf).topStart,
                        large = BubbleDefaults.cornerLarge,
                        small = BubbleDefaults.cornerSmall,
                        inset = inset,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(PhotoQuoteDefaults.maxWidthFraction)
                            .padding(inset),
                    ) {
                        ChatQuotePanel(
                            modifier = Modifier.testTag(PHOTO_QUOTE_TAG),
                            quote = quote,
                            shape = RoundedCornerShape(chip.topStart, chip.topEnd, chip.bottomEnd, chip.bottomStart),
                            // Near-opaque so the snippet reads on any photo, light or dark.
                            ground = CodeTheme.colors.background.copy(alpha = PhotoQuoteDefaults.groundAlpha),
                            onClick = onQuoteClick,
                            onLongClick = onLongClick,
                            onDoubleClick = onDoubleClick,
                        )
                    }
                }
            }
        }
    }
}

/** The quote a photo reply carries, drawn over the photo's top corner. */
internal const val PHOTO_QUOTE_TAG = "chat_photo_quote"

private object PhotoQuoteDefaults {
    /** The share of the photo's width a citation may take, so some of the photo always shows beside it. */
    const val maxWidthFraction = 0.8f
    const val groundAlpha = 0.85f
}

/**
 * The chip's corners, concentric with the photo it sits on: the corner nested in the photo's
 * top-start follows that corner less the inset, so the gap holds round the turn whether the photo
 * is rounded or flattened by its group. The other three are free-standing and take the radius a
 * rounded corner would give, so a solo photo's chip is uniform. Floored at the flattened radius,
 * as the text reply's panel is.
 */
internal fun photoQuoteCorners(photoTopStart: Dp, large: Dp, small: Dp, inset: Dp): BubbleCorners {
    val free = max(large - inset, small)
    return BubbleCorners(max(photoTopStart - inset, small), free, free, free)
}
