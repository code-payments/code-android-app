package com.flipcash.shared.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCard.Message.Body
import com.flipcash.shared.chat.models.LinkCard.Message.Kind
import com.flipcash.shared.chat.models.LinkCard.Message.State
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.rememberBlurHashPainter
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * A link to one message, drawn as a panel naming the chat it came from (node 11396:118).
 *
 * Every state shares one frame: an identity block (the chat's avatar beside its name over an
 * "Author · time" line), then whatever the viewer may see of the message, then the footer or the
 * button, 10 apart. What fills it is decided upstream, by who is reading and where the message
 * lives, never by the card; see [LinkCard.Message.State]. The layout follows iOS's
 * `LinkMessageCardContent` face for face, including the chevron in place of Figma's "Open".
 *
 * [onOpen] takes the card's tap and its button alike. Both follow the link, and the chat it lands
 * on runs its own gate, so Join, Buy and Request start the flows the group's screen already has
 * rather than a second copy of them here. Null leaves the card inert and drops the chevron.
 *
 * A redacted body is the server's placeholder, not the message. It is drawn blurred and kept out of
 * the semantics tree, so neither a screen reader nor a copy can lift it.
 */
@Composable
internal fun MessageLinkCard(
    card: LinkCard.Message,
    shape: CornerBasedShape,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state = card.state
    val opens = onOpen != null
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MessageLinkCardDefaults.FILL)
            .border(CodeTheme.dimens.border, MessageLinkCardDefaults.STROKE, shape)
            .padding(MessageLinkCardDefaults.PADDING),
    ) {
        val width = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.GAP)) {
            when (state) {
                State.Loading, State.Unresolved -> Skeleton(width, shimmering = state == State.Loading)
                is State.Resolved -> {
                    val body = state.message.body
                    Identity(state.source, byline(state.message), opens)
                    Excerpt(body)
                    Footer(
                        when (body) {
                            // A cash link's footer says where its claim stands, as its own card's stub does.
                            is Body.CashLink -> claimCaption(body.claim)
                            is Body.Cash -> stringResource(R.string.label_messageLink_settled)
                            else -> state.reactionCount.takeIf { it > 0 }?.let {
                                pluralStringResource(R.plurals.label_messageLink_reactions, it.toInt(), it.toString())
                            }
                        }
                    )
                }
                is State.Gated -> {
                    val redacted = state.message.body.withheld
                    Identity(state.source, byline(state.message), opens)
                    Excerpt(state.message.body)
                    if (redacted) Footer(state.requirement?.let { requirementLine(it) })
                    CardButton(
                        text = stringResource(if (redacted) R.string.action_buy else R.string.action_join),
                        onClick = onOpen,
                    )
                }
                is State.LockedGroup -> {
                    val title = state.source.title ?: stringResource(R.string.label_linkCard_untitledGroup)
                    Identity(state.source, stringResource(R.string.label_messageLink_kindPrivateGroup), opens)
                    DimBody(stringResource(R.string.label_messageLink_lockedGroup, title))
                    val members = pluralStringResource(
                        R.plurals.subtitle_chatMemberCount,
                        state.memberCount.toInt(),
                        state.memberCount.toString(),
                    )
                    Footer(state.host?.let { stringResource(R.string.label_messageLink_membersHostedBy, members, it) } ?: members)
                    CardButton(text = stringResource(R.string.action_messageLink_request), onClick = onOpen)
                }
                State.LockedDirect -> {
                    // Only that it is a DM. Who is in it is part of what stays locked, and it never opens.
                    IdentityRow(name = stringResource(R.string.label_messageLink_kindDirect), meta = null, opens = false) {
                        PlaceholderAvatar()
                    }
                    DimBody(stringResource(R.string.label_messageLink_lockedDirect))
                }
                is State.Deleted -> {
                    Identity(state.source, meta = null, opens = opens)
                    DimBody(stringResource(R.string.label_messageLink_deleted))
                }
            }
        }
    }
}

/**
 * Loading and Unresolved: the frame with nothing in it, so nothing moves when the answer lands. The
 * identity bars are 45% and 25% of the card's width and the body bars 100% and 55%, as iOS draws them.
 */
@Composable
private fun Skeleton(width: Dp, shimmering: Boolean) {
    val bar = Modifier
        .height(MessageLinkCardDefaults.BAR_HEIGHT)
        .let { if (shimmering) it.shimmer(CircleShape) else it.background(MessageLinkCardDefaults.BAR, CircleShape) }
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.AVATAR_GAP),
    ) {
        PlaceholderAvatar()
        Column(verticalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.SKELETON_IDENTITY_GAP)) {
            Box(bar.width(width * MessageLinkCardDefaults.SKELETON_NAME_FRACTION))
            Box(bar.width(width * MessageLinkCardDefaults.SKELETON_META_FRACTION))
        }
    }
    Column(
        modifier = Modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.GAP / 2),
    ) {
        Box(bar.fillMaxWidth())
        Box(bar.fillMaxWidth(MessageLinkCardDefaults.SKELETON_SHORT_FRACTION))
    }
}

/** "Author · time", with the verb a payment adds to the author. */
@Composable
private fun byline(excerpt: LinkCard.Message.Excerpt): String {
    val author = when {
        excerpt.isFromSelf -> stringResource(R.string.label_messageLink_you)
        else -> excerpt.author ?: stringResource(R.string.label_messageLink_someone)
    }
    val who = when (val body = excerpt.body) {
        is Body.Cash -> stringResource(
            if (body.tipped) R.string.label_messageLink_tipped else R.string.label_messageLink_sent,
            author,
        )
        is Body.CashLink -> stringResource(R.string.label_messageLink_sentCashLink, author)
        else -> author
    }
    return stringResource(R.string.label_messageLink_byline, who, messageCardTime(excerpt.sentAt))
}

/** The identity block for a known chat: its picture, its name, and one meta line. */
@Composable
private fun Identity(source: LinkCard.Message.Source, meta: String?, opens: Boolean) {
    val title = source.title ?: stringResource(R.string.label_linkCard_untitledGroup)
    IdentityRow(name = title, meta = meta, opens = opens) {
        ContactAvatar(
            image = source.picture,
            displayName = title,
            access = source.pictureAccess,
            modifier = Modifier
                .size(MessageLinkCardDefaults.AVATAR)
                .clip(CircleShape),
        )
    }
}

/** The avatar beside the name over the meta line, with a chevron when the card opens. */
@Composable
private fun IdentityRow(name: String, meta: String?, opens: Boolean, avatar: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.AVATAR_GAP),
    ) {
        avatar()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.IDENTITY_GAP),
        ) {
            Text(
                text = name,
                style = MessageLinkCardDefaults.nameStyle,
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            meta?.let {
                Text(
                    text = it,
                    style = MessageLinkCardDefaults.metaStyle,
                    color = MessageLinkCardDefaults.SECONDARY,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (opens) {
            Icon(
                painter = painterResource(com.getcode.util.resources.R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MessageLinkCardDefaults.CHEVRON,
                modifier = Modifier.size(MessageLinkCardDefaults.CHEVRON_WIDTH, MessageLinkCardDefaults.CHEVRON_HEIGHT),
            )
        }
    }
}

@Composable
private fun PlaceholderAvatar() {
    Box(
        Modifier
            .size(MessageLinkCardDefaults.AVATAR)
            .background(MessageLinkCardDefaults.BAR, CircleShape),
    )
}

@Composable
private fun Excerpt(body: Body) {
    when (body) {
        is Body.Text -> ExcerptText(body)
        is Body.Cash -> CashAmount(body.amount)
        is Body.CashLink -> CashAmount(body.amount)
        // The thumbnail trails its caption, top-aligned; with no caption it stands alone at the start.
        is Body.Photo -> {
            val thumbnail = Modifier
                .size(MessageLinkCardDefaults.THUMBNAIL)
                .clip(RoundedCornerShape(MessageLinkCardDefaults.THUMBNAIL_RADIUS))
            val caption = body.caption?.takeIf { it.text.isNotBlank() }
            if (caption == null) {
                ChatPhotoThumbnail(photo = body.photo, blurHash = body.blurHash, modifier = thumbnail)
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.AVATAR_GAP),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.weight(1f)) { ExcerptText(caption) }
                    ChatPhotoThumbnail(photo = body.photo, blurHash = body.blurHash, modifier = thumbnail)
                }
            }
        }
        Body.Other -> DimBody(stringResource(R.string.label_messageLink_attachment))
    }
}

/** Up to four lines, with line breaks folded to spaces so blank lines don't spend them. */
@Composable
private fun ExcerptText(body: Body.Text) {
    Text(
        modifier = if (body.redacted) Modifier.redactedText() else Modifier,
        text = flattenExcerpt(body.text),
        style = MessageLinkCardDefaults.bodyStyle,
        color = if (body.redacted) redactedTextColor(MessageLinkCardDefaults.EXCERPT) else MessageLinkCardDefaults.EXCERPT,
        maxLines = MessageLinkCardDefaults.BODY_LINES,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Each line break, and the whitespace around it, folded to one space. */
internal fun flattenExcerpt(text: String): String = text.replace(LINE_BREAK, " ")

private val LINE_BREAK = Regex("""\s*\n\s*""")

@Composable
private fun CashAmount(amount: String) {
    Text(
        text = amount,
        style = CodeTheme.typography.displayExtraSmall,
        color = CodeTheme.colors.textMain,
        maxLines = 1,
    )
}

/** The panel's quieter body: locked and deleted copy, and a message with nothing to excerpt. */
@Composable
private fun DimBody(text: String) {
    Text(
        text = text,
        style = MessageLinkCardDefaults.bodyStyle,
        color = MessageLinkCardDefaults.SECONDARY,
    )
}

/** BlurHash underneath, the photo over it once it loads; a null [photo] leaves only the BlurHash. */
@Composable
private fun ChatPhotoThumbnail(photo: ChatPhoto?, blurHash: String?, modifier: Modifier = Modifier) {
    val blur = rememberBlurHashPainter(blurHash)
    Box(modifier.background(MessageLinkCardDefaults.BAR)) {
        if (blur != null) {
            Image(
                painter = blur,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun requirementLine(requirement: LinkCard.GroupInvite.Requirement): String? {
    val amount = requirement.amount ?: return null
    return if (requirement.currencyName != null) {
        stringResource(R.string.label_chat_balanceRequirement, amount, requirement.currencyName)
    } else {
        stringResource(R.string.label_chat_balanceRequirement_anyToken, amount)
    }
}

@Composable
private fun claimCaption(claim: LinkCard.Cash.Claim): String = stringResource(
    when (claim) {
        LinkCard.Cash.Claim.Claimable -> R.string.label_linkCard_claim
        LinkCard.Cash.Claim.Claiming -> R.string.label_linkCard_claiming
        LinkCard.Cash.Claim.Claimed -> R.string.label_linkCard_claimed
        LinkCard.Cash.Claim.Expired -> R.string.label_linkCard_expired
    }
)

/** One quiet line under the body; nothing at all when there is nothing to say. */
@Composable
private fun Footer(text: String?) {
    if (text.isNullOrEmpty()) return
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MessageLinkCardDefaults.metaStyle,
        color = MessageLinkCardDefaults.SECONDARY,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardButton(text: String, onClick: (() -> Unit)?) {
    CodeButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(MessageLinkCardDefaults.BUTTON_HEIGHT),
        text = text,
        buttonState = ButtonState.Filled,
        overrideContentPadding = true,
        contentPadding = GroupInviteCardDefaults.NO_PADDING,
        enabled = onClick != null,
        onClick = { onClick?.invoke() },
    )
}

/** Node 11493:201, as iOS's `LinkMessageCardContent.Layout` carries it. */
internal object MessageLinkCardDefaults {
    /** Panel fill: white at 8%. */
    val FILL = Color.White.copy(alpha = 0.08f)

    /** Panel stroke: white at 10%, the group card's. */
    val STROKE = Color.White.copy(alpha = 0.1f)

    /** Skeleton bars, the placeholder avatar and the thumbnail's ground. */
    val BAR = Color.White.copy(alpha = 0.12f)

    /** The excerpt: white at 88%. */
    val EXCERPT = Color.White.copy(alpha = 0.88f)

    /** Meta, footer and dim body lines: white at 60%. */
    val SECONDARY = Color.White.copy(alpha = 0.6f)

    val CHEVRON = Color.White.copy(alpha = 0.42f)

    val SHAPE = RoundedCornerShape(12.dp)
    val PADDING = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 13.dp)

    /** Between the card's groups: identity, body, footer, button. */
    val GAP = 10.dp
    val AVATAR = 32.dp
    val AVATAR_GAP = 10.dp

    /** Between the identity block's name and meta lines. */
    val IDENTITY_GAP = 2.dp
    val SKELETON_IDENTITY_GAP = 6.dp
    const val SKELETON_NAME_FRACTION = 0.45f
    const val SKELETON_META_FRACTION = 0.25f
    const val SKELETON_SHORT_FRACTION = 0.55f
    val THUMBNAIL = 56.dp
    val THUMBNAIL_RADIUS = 8.dp
    val BAR_HEIGHT = 10.dp
    val BUTTON_HEIGHT = 44.dp
    /** SF's chevron.right at 13 semibold is about 7 by 12. */
    val CHEVRON_WIDTH = 7.dp
    val CHEVRON_HEIGHT = 12.dp
    const val BODY_LINES = 4

    val nameStyle: TextStyle
        @Composable get() = CodeTheme.typography.textSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

    val metaStyle: TextStyle
        @Composable get() = CodeTheme.typography.textSmall.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)

    /** 15 medium; iOS adds 3 of line spacing to the font's own leading. */
    val bodyStyle: TextStyle
        @Composable get() = CodeTheme.typography.textSmall.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
}

// region Previews

private const val PREVIEW_MESSAGE_URL = "https://app.flipcash.com/chat/8cec60c8-afbb-88a8-96fe-64cc825f5322/m/42"

private val previewSource = LinkCard.Message.Source(
    title = "Moony",
    kind = Kind.PublicGroup,
    picture = null,
    pictureAccess = BlobAccessContext.ChatProfile(ChatId(ByteArray(16))),
)

private fun previewExcerpt(body: Body) = LinkCard.Message.Excerpt(
    author = "Vincent",
    isFromSelf = false,
    sentAt = Clock.System.now() - 2.hours,
    body = body,
)

private fun previewMessageCard(state: State) = LinkCard.Message(
    url = PREVIEW_MESSAGE_URL,
    start = 0,
    end = PREVIEW_MESSAGE_URL.length,
    chatId = ChatId(ByteArray(16)),
    messageId = 42,
    isGroup = true,
    state = state,
)

@Composable
private fun PreviewMessageCard(state: State) {
    Box(Modifier.width(312.dp).padding(8.dp)) {
        MessageLinkCard(
            card = previewMessageCard(state),
            shape = MessageLinkCardDefaults.SHAPE,
            onOpen = {},
        )
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_Resolved() = PreviewMessageCard(
    State.Resolved(
        source = previewSource,
        message = previewExcerpt(Body.Text("Curve just crossed \$1M market cap. Thanks everyone who held through the dip.")),
        reactionCount = 212,
    )
)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_GatedRedacted() = PreviewMessageCard(
    State.Gated(
        source = previewSource,
        message = previewExcerpt(Body.Text("Placeholder text standing in for the message", redacted = true)),
        requirement = LinkCard.GroupInvite.Requirement(amount = "\$10", currencyName = "MOONY", staffOnly = false),
    )
)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_LockedGroup() = PreviewMessageCard(
    State.LockedGroup(
        source = previewSource.copy(title = "Ballers", kind = Kind.PrivateGroup),
        memberCount = 48,
        host = "Chloe",
    )
)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_Cash() = PreviewMessageCard(
    State.Resolved(
        source = previewSource,
        message = previewExcerpt(Body.Cash(amount = "\$20.00", tipped = true)),
        reactionCount = 0,
    )
)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_LockedDirect() = PreviewMessageCard(State.LockedDirect)

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_Deleted() = PreviewMessageCard(State.Deleted(previewSource))

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_MessageLink_Unresolved() = PreviewMessageCard(State.Unresolved)

// endregion
