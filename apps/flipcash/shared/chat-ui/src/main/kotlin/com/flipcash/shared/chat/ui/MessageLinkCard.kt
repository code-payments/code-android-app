package com.flipcash.shared.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import android.os.Build
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.flipcash.shared.chat.media.ChatPhoto
import com.flipcash.shared.common.ui.rememberBlurHashPainter
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.shimmer
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCard.Message.Body
import com.flipcash.shared.chat.models.LinkCard.Message.Kind
import com.flipcash.shared.chat.models.LinkCard.Message.State
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * A link to one message, drawn as a panel naming the chat it came from (node 11396:118).
 *
 * Every state shares one frame: the chat's header, then whatever the viewer may see of the message,
 * then the footer or the button. What fills it is decided upstream, by who is reading and where the
 * message lives, never by the card; see [LinkCard.Message.State].
 *
 * [onOpen] takes the card's tap and its button alike. Both follow the link, and the chat it lands
 * on runs its own gate, so Join, Buy and Request start the flows the group's screen already has
 * rather than a second copy of them here. Null leaves the card inert.
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MessageLinkCardDefaults.FILL)
            .border(CodeTheme.dimens.border, MessageLinkCardDefaults.STROKE, shape)
            .padding(MessageLinkCardDefaults.PADDING),
        verticalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.GAP),
    ) {
        when (state) {
            State.Loading, State.Unresolved -> Skeleton(card, shimmering = state == State.Loading)
            is State.Resolved -> {
                Header(state.source)
                Excerpt(state.message)
                Footer(
                    // A cash link's footer says where its claim stands, as its own card's stub does.
                    leading = (state.message.body as? Body.CashLink)?.let { claimCaption(it.claim) }
                        ?: state.reactionCount.takeIf { it > 0 }?.let {
                            pluralStringResource(R.plurals.label_messageLink_reactions, it.toInt(), it.toString())
                        },
                    onOpen = onOpen,
                )
            }
            is State.Gated -> {
                Header(state.source)
                Excerpt(state.message)
                val redacted = state.message.body.withheld
                if (redacted) state.requirement?.let { RequirementLine(it) }
                CardButton(
                    text = stringResource(if (redacted) R.string.action_buy else R.string.action_join),
                    onClick = onOpen,
                )
            }
            is State.LockedGroup -> {
                Header(state.source)
                Text(
                    text = stringResource(
                        R.string.label_messageLink_lockedGroup,
                        state.source.title ?: stringResource(R.string.label_linkCard_untitledGroup),
                    ),
                    style = CodeTheme.typography.textMedium,
                    color = MessageLinkCardDefaults.SECONDARY,
                )
                val members = pluralStringResource(
                    R.plurals.subtitle_chatMemberCount,
                    state.memberCount.toInt(),
                    state.memberCount.toString(),
                )
                Text(
                    text = state.host
                        ?.let { stringResource(R.string.label_messageLink_membersHostedBy, members, it) }
                        ?: members,
                    style = CodeTheme.typography.textSmall,
                    color = MessageLinkCardDefaults.SECONDARY,
                )
                CardButton(text = stringResource(R.string.action_messageLink_request), onClick = onOpen)
            }
            State.LockedDirect -> {
                // Only that it is a DM. Who is in it is part of what stays locked.
                HeaderRow(title = stringResource(R.string.label_messageLink_kindDirect), kind = null) {
                    PlaceholderAvatar()
                }
                Text(
                    text = stringResource(R.string.label_messageLink_lockedDirect),
                    style = CodeTheme.typography.textMedium,
                    color = MessageLinkCardDefaults.SECONDARY,
                )
            }
        }
    }
}

/**
 * Loading and Unresolved: the frame with nothing in it, so nothing moves when the answer lands. The
 * id's form says group or DM before any lookup, so the header can say which kind it is.
 */
@Composable
private fun Skeleton(card: LinkCard.Message, shimmering: Boolean) {
    HeaderRow(
        title = stringResource(R.string.label_messageLink_unresolvedTitle),
        kind = stringResource(R.string.label_messageLink_message),
    ) { PlaceholderAvatar() }
    val bar = Modifier
        .height(MessageLinkCardDefaults.BAR_HEIGHT)
        .let { if (shimmering) it.shimmer(CircleShape) else it.background(MessageLinkCardDefaults.BAR, CircleShape) }
    Spacer(Modifier.height(2.dp))
    Box(bar.fillMaxWidth())
    Box(bar.fillMaxWidth(0.6f))
}

@Composable
private fun Header(source: LinkCard.Message.Source) {
    val title = source.title ?: stringResource(R.string.label_linkCard_untitledGroup)
    HeaderRow(title = title, kind = stringResource(source.kind.label())) {
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

@Composable
private fun HeaderRow(title: String, kind: String?, avatar: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.HEADER_GAP),
    ) {
        avatar()
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = title,
            style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CodeTheme.colors.textMain,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        kind?.let {
            Text(
                text = it,
                style = CodeTheme.typography.textSmall,
                color = MessageLinkCardDefaults.SECONDARY,
                maxLines = 1,
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
private fun Excerpt(excerpt: LinkCard.Message.Excerpt) {
    val author = when {
        excerpt.isFromSelf -> stringResource(R.string.label_messageLink_you)
        else -> excerpt.author ?: stringResource(R.string.label_messageLink_someone)
    }
    val body = excerpt.body
    val byline = when {
        body is Body.Cash -> stringResource(
            if (body.tipped) R.string.label_messageLink_tipped else R.string.label_messageLink_sent,
            author,
        )
        body is Body.CashLink -> stringResource(R.string.label_messageLink_sentCashLink, author)
        else -> author
    }
    val time = formatLastActivity(excerpt.sentAt)
    Text(
        text = stringResource(R.string.label_messageLink_byline, byline, time),
        style = CodeTheme.typography.textSmall,
        color = MessageLinkCardDefaults.SECONDARY,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    ExcerptBody(body)
}

@Composable
private fun ExcerptBody(body: Body) {
    when (body) {
        is Body.Text -> Text(
            modifier = Modifier.then(
                when {
                    !body.redacted -> Modifier
                    canBlur -> Modifier
                        .blur(MessageLinkCardDefaults.REDACTED_BLUR, BlurredEdgeTreatment.Unbounded)
                        .clearAndSetSemantics {}
                    // `blur` draws nothing below Android 12; cover the text with a solid block instead.
                    else -> Modifier
                        .background(CodeTheme.colors.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .clearAndSetSemantics {}
                }
            ),
            text = body.text,
            style = CodeTheme.typography.textMedium,
            color = if (body.redacted && !canBlur) Color.Transparent else CodeTheme.colors.textMain,
            maxLines = MessageLinkCardDefaults.BODY_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        is Body.Cash -> Text(
            text = body.amount,
            style = CodeTheme.typography.displayExtraSmall,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
        )
        is Body.CashLink -> Text(
            text = body.amount,
            style = CodeTheme.typography.displayExtraSmall,
            color = CodeTheme.colors.textMain,
            maxLines = 1,
        )
        // The thumbnail trails its caption; with no caption it stands alone, unlabelled.
        is Body.Photo -> Row(
            horizontalArrangement = Arrangement.spacedBy(MessageLinkCardDefaults.GAP),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.weight(1f)) { body.caption?.let { ExcerptBody(it) } }
            ChatPhotoThumbnail(
                photo = body.photo,
                blurHash = body.blurHash,
                modifier = Modifier
                    .size(MessageLinkCardDefaults.THUMBNAIL)
                    .clip(RoundedCornerShape(MessageLinkCardDefaults.THUMBNAIL_RADIUS)),
            )
        }
        Body.Other -> Text(
            text = stringResource(R.string.label_messageLink_attachment),
            style = CodeTheme.typography.textMedium,
            color = MessageLinkCardDefaults.SECONDARY,
        )
    }
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
private fun RequirementLine(requirement: LinkCard.GroupInvite.Requirement) {
    val amount = requirement.amount ?: return
    Text(
        text = if (requirement.currencyName != null) {
            stringResource(R.string.label_chat_balanceRequirement, amount, requirement.currencyName)
        } else {
            stringResource(R.string.label_chat_balanceRequirement_anyToken, amount)
        },
        style = CodeTheme.typography.textSmall,
        color = MessageLinkCardDefaults.SECONDARY,
    )
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

@Composable
private fun Footer(leading: String?, onOpen: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = leading.orEmpty(),
            style = CodeTheme.typography.textSmall,
            color = MessageLinkCardDefaults.SECONDARY,
            maxLines = 1,
        )
        if (onOpen != null) {
            Text(
                text = stringResource(R.string.action_messageLink_open),
                style = CodeTheme.typography.textSmall.copy(fontWeight = FontWeight.SemiBold),
                color = CodeTheme.colors.textMain,
            )
        }
    }
}

@Composable
private fun CardButton(text: String, onClick: (() -> Unit)?) {
    CodeButton(
        modifier = Modifier
            .padding(top = MessageLinkCardDefaults.GAP)
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

private fun Kind.label(): Int = when (this) {
    Kind.DirectMessage -> R.string.label_messageLink_kindDirect
    Kind.PublicGroup -> R.string.label_messageLink_kindPublicGroup
    Kind.PrivateGroup -> R.string.label_messageLink_kindPrivateGroup
}

/** Node 11493:201, named after iOS's tokens there. */
internal object MessageLinkCardDefaults {
    /** Panel fill: white at 8%. */
    val FILL = Color.White.copy(alpha = 0.08f)

    /** Panel stroke: white at 10%, the group card's. */
    val STROKE = Color.White.copy(alpha = 0.1f)

    /** Skeleton bars and the placeholder avatar. */
    val BAR = Color.White.copy(alpha = 0.12f)

    /** Kind, byline, footer and dim lines: white at 60%, as iOS draws them. */
    val SECONDARY = Color.White.copy(alpha = 0.6f)

    val SHAPE = RoundedCornerShape(12.dp)
    val PADDING = 12.dp
    val GAP = 6.dp
    val HEADER_GAP = 6.dp
    val AVATAR = 24.dp
    val THUMBNAIL = 56.dp
    val THUMBNAIL_RADIUS = 8.dp
    val BAR_HEIGHT = 10.dp
    val BUTTON_HEIGHT = 44.dp
    val REDACTED_BLUR = 6.dp
    const val BODY_LINES = 4
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
private fun Preview_MessageLink_Unresolved() = PreviewMessageCard(State.Unresolved)

// endregion

/** `Modifier.blur` needs RenderEffect, which arrived in Android 12. */
private val canBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
