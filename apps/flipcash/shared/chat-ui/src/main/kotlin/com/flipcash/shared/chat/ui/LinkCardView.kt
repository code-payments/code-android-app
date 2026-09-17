package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.tipping.TipCardOpaqueFallback
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.core.ui.TokenCard
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.services.models.asHandle
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.services.models.handle
import com.flipcash.services.models.nameOrHandle
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.utils.ConstraintMode

/**
 * The card, in place of the link.
 *
 * A cash link is a voucher: one amount, made once, spent once. It is drawn as one — the token it
 * pays out named along the top, the amount in the middle, and a perforated stub across the bottom
 * carrying what can be done with it. A token link is drawn as that token's bill instead, the card
 * the link opens to. The split is the point: a voucher for $15 and a card about Dollars should not
 * be the same gold rectangle, so the bill's colours stay with the card that stands for the token. A
 * tip card link is that person's tip card, on the same principle.
 *
 * The URL the card came from is cut from the body text, so the card is the link rather than an
 * ornament above it, so [onClick] has to carry the tap — a link-only message would otherwise draw
 * something that opens nothing. The whole card is handed back rather than its URL, because where a
 * tap should land differs by kind and only the caller knows the transcript it is landing in.
 */
@Composable
internal fun LinkCardView(
    card: LinkCard,
    onClick: (LinkCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The voucher's proportions, not its size: a bubble is a good deal narrower than the wallet's
    // card, and scaling the height with the width is what keeps it a card in chat instead of a
    // tall panel.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val height: Dp = maxWidth * LinkCardDefaults.CARD_ASPECT
        when (card) {
            is LinkCard.Cash -> CashLinkCard(
                card = card,
                height = height,
                onClick = { onClick(card) },
            )

            is LinkCard.TokenInfo -> TokenLinkCard(
                card = card,
                height = height,
                onClick = { onClick(card) },
            )

            // Sized from the width rather than handed the shared height: the tip card is the one
            // portrait figure of the three, so it is the width that has to give.
            is LinkCard.TipCard -> TipLinkCard(
                card = card,
                width = maxWidth * LinkCardDefaults.TIP_CARD_WIDTH_FRACTION,
                onClick = { onClick(card) },
            )
        }
    }
}

@Composable
private fun CashLinkCard(
    card: LinkCard.Cash,
    height: Dp,
    onClick: () -> Unit,
) {
    // Unresolved is also the unavailable state: a lookup that failed, timed out or was switched off
    // renders here. The same voucher, with nothing filled in — same size, same chrome — so nothing
    // moves or resizes when the amount lands.
    val state = card.state as? LinkCard.Cash.State.Resolved

    CashVoucher(
        height = height,
        tokenName = state?.token?.let { displayNameOf(it) }
            ?: stringResource(R.string.label_linkCard_cash),
        tokenImage = state?.token?.imageUrl,
        amount = state?.amount,
        // Claimed and expired are both spent: the voucher is dimmed as a whole, so it reads as
        // used before the label under the tear is read at all.
        spent = state?.claim == LinkCard.Cash.Claim.Claimed ||
            state?.claim == LinkCard.Cash.Claim.Expired,
        // Only claimed, because only claimed means someone tore it off. An expired link lapsed
        // where it sat; drawing it torn would say a person acted on it when nobody did.
        torn = state?.claim == LinkCard.Cash.Claim.Claimed,
        onClick = onClick,
    ) {
        state ?: return@CashVoucher
        when (state.claim) {
            LinkCard.Cash.Claim.Claimed ->
                // Off the paper: the stub this used to sit on left with whoever claimed it.
                StubLabel(stringResource(R.string.label_linkCard_claimed), onPaper = false)
            LinkCard.Cash.Claim.Expired -> StubLabel(stringResource(R.string.label_linkCard_expired))
            // The same voucher whoever is reading it. The transcript already says who sent the
            // link -- the bubble sits on the sender's side -- so a card that read differently for
            // the issuer would be saying it twice, and saying it in the one place both people are
            // looking at the same object.
            LinkCard.Cash.Claim.Claimable -> ClaimPill(stringResource(R.string.label_linkCard_claim))
        }
    }
}

/**
 * A token link is the token's own bill — the card the link opens to, so the colours the creator
 * chose are what the reader recognises before reading a word. The wallet's card carries a balance;
 * this one does not. The link is about the currency, not about the reader's position in it, and a
 * balance printed into a transcript would keep saying what the wallet said at the moment the
 * message was scrolled past.
 */
@Composable
private fun TokenLinkCard(
    card: LinkCard.TokenInfo,
    height: Dp,
    onClick: () -> Unit,
) {
    when (val state = card.state) {
        is LinkCard.TokenInfo.State.Resolved -> TokenCard(
            token = state.token,
            // Empty rather than absent: the header lays the balance out at the end of the row, so
            // an empty string leaves the name alone on the row with nothing to collide with.
            balanceText = "",
            displayName = displayNameOf(state.token),
            height = height,
            onClick = onClick,
        )

        LinkCard.TokenInfo.State.Unresolved -> UnresolvedTokenCard(
            mint = card.mint,
            height = height,
            onClick = onClick,
        )
    }
}

/**
 * A tip card link is that tip card: the same near-black portrait, the person named under their
 * picture. Two things are missing from the figure and both are deliberate.
 *
 * The scannable code is not drawn. Its payload is a round trip the chat does not make, and a code is
 * what a camera is aimed at — useless to a reader holding the phone it is printed on. The picture
 * takes its place, which is the half of the figure that says whose card this is.
 *
 * And the type does not scale with the card, though the real one's does. That card is a fixed
 * geometry rendered for export, so its name holds a proportion; this one is read in a transcript at
 * the size everything around it is read at, and a name shrunk to fit a card in a bubble is a name
 * nobody reads.
 *
 * It keeps its proportions instead of filling the bubble, because the other two stand for an amount
 * and a currency and read as bills, while a portrait card stretched to a bubble's width stops being
 * one. A link-only message drops its bubble entirely, so that is a card centred on the transcript.
 */
@Composable
private fun TipLinkCard(
    card: LinkCard.TipCard,
    width: Dp,
    onClick: () -> Unit,
) {
    val profile = (card.state as? LinkCard.TipCard.State.Resolved)?.profile

    // Who the card can say it belongs to. A resolved profile names the person; an unresolved
    // by-handle link still has the handle, which came out of the URL and cost nothing; an unresolved
    // by-id link has a UUID, which names nobody, so that card names the object instead.
    val person: String? = profile?.let { nameOrHandle(it.displayName, it.handle) }
        ?: (card.owner as? TipCardOwner.ByUsername)?.username?.asHandle()

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(width)
                .height(width * LinkCardDefaults.TIP_CARD_ASPECT)
                .clip(RoundedCornerShape(width * LinkCardDefaults.TIP_CARD_CORNER))
                .background(TipCardOpaqueFallback)
                .clickable(onClick = onClick)
                .padding(horizontal = CodeTheme.dimens.inset),
            verticalArrangement = Arrangement.spacedBy(
                CodeTheme.dimens.grid.x2,
                Alignment.CenterVertically,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ContactAvatar(
                image = profile?.profilePicture,
                // Empty rather than a stand-in label: the avatar initials whatever it is given, and
                // initialling "Tip Card" would put someone's monogram on a card belonging to nobody.
                displayName = person.orEmpty(),
                // The id the link carried beats the profile's own, which is null until a by-handle
                // lookup fills it in -- and it is what authorizes re-minting an expired picture URL.
                access = BlobAccessContext.profile(
                    profile?.userId ?: (card.owner as? TipCardOwner.ById)?.userId,
                ),
                modifier = Modifier
                    .size(width * LinkCardDefaults.TIP_CARD_AVATAR)
                    .clip(CircleShape),
            )

            Text(
                text = person ?: stringResource(R.string.label_linkCard_tipCard),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Under the name, as the real card draws it, and only when it is not already the name:
            // an account with no display name is named by its handle, which would print twice.
            profile?.handle?.takeIf { it != person }?.let {
                Text(
                    text = it,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textMain.copy(
                        alpha = LinkCardDefaults.TIP_CARD_HANDLE_ALPHA,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The same card with nothing known in it. There is no honest bill for a mint whose name and colours
 * have not arrived — and `AppRouter` does not check that a token path holds a real mint, so this is
 * also what `app.flipcash.com/token/junk` renders as, permanently.
 *
 * So it names the mint. The URL has already been cut from the message text, and a blank rectangle
 * where readable text used to be would leave the reader with less than the raw link gave them; the
 * address at least says which token was meant and can be read back against the link.
 */
@Composable
private fun UnresolvedTokenCard(
    mint: Mint,
    height: Dp,
    onClick: () -> Unit,
) {
    val shape = CodeTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(CodeTheme.colors.surfaceVariant)
            .border(CodeTheme.dimens.border, CodeTheme.colors.surfaceVariant, shape)
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.inset),
    ) {
        Text(
            modifier = Modifier.align(Alignment.TopStart),
            text = mint.abbreviated(),
            style = CodeTheme.typography.textSmall,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

/** Head and tail of the address, the way every explorer shows one. */
private fun Mint.abbreviated(): String = description.let { address ->
    if (address.length <= ABBREVIATED_MINT_CHARS * 2) {
        address
    } else {
        "${address.take(ABBREVIATED_MINT_CHARS)}…${address.takeLast(ABBREVIATED_MINT_CHARS)}"
    }
}

private const val ABBREVIATED_MINT_CHARS = 4

/**
 * The reserve is branded Dollars everywhere the user meets it; `token.name` off the wire is "USDF",
 * which is the mint, not the thing they hold.
 */
@Composable
private fun displayNameOf(token: Token): String = when (token.address) {
    Mint.usdf -> stringResource(R.string.displayName_dollars)
    else -> token.name
}

/**
 * A ticket: the token along the top, the amount under it, and a perforated stub holding [stub].
 *
 * Drawn as two pieces rather than one card with a line across it, because [torn] has to be able to
 * take one away. Intact, they meet edge to edge and the notches each cuts at the seam combine into
 * one hole, so the tear reads as a perforation. Torn, the stub is simply not drawn: what is left is
 * the half of each notch that stayed behind, which is the evidence that a stub was there and was
 * pulled off.
 *
 * The cuts are cleared out of the pieces rather than painted over them: each composites offscreen,
 * so the notches show whatever the card is sitting on instead of a colour guessed at here.
 *
 * Tearing costs no height. The stub's band is held open whether or not the stub is drawn in it,
 * because the claim state arrives from the lookup after the row is first drawn — a card that
 * changed size on resolve would shove the transcript around under the reader.
 */
@Composable
private fun CashVoucher(
    height: Dp,
    tokenName: String,
    tokenImage: Any?,
    amount: String?,
    spent: Boolean,
    torn: Boolean,
    onClick: () -> Unit,
    stub: @Composable () -> Unit,
) {
    val corner = CodeTheme.shapes.medium.topStart
    val square = CornerSize(0.dp)
    val inset = CodeTheme.dimens.inset
    val stubHeight = height * LinkCardDefaults.STUB_FRACTION

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // Tappable in every state: an unresolved link is unresolved, not broken, and a spent
            // one still opens to the page that says so.
            .clickable(onClick = onClick),
    ) {
        VoucherPiece(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(corner, corner, square, square),
            spent = spent,
            notchAtBottom = true,
            // Nothing left to tear along once it has been torn.
            scored = !torn,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = inset),
                verticalArrangement = Arrangement.spacedBy(
                    CodeTheme.dimens.grid.x2,
                    Alignment.CenterVertically,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TokenIconWithName(
                    tokenName = tokenName,
                    tokenImage = tokenImage,
                    imageSize = 20.dp,
                    spacing = CodeTheme.dimens.grid.x1,
                    textStyle = CodeTheme.typography.textSmall,
                    textColor = LinkCardDefaults.INK.copy(alpha = LinkCardDefaults.INK_MUTED),
                )
                amount?.let {
                    AnimatedNumberText(
                        value = it,
                        style = CodeTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = LinkCardDefaults.INK,
                        constraintMode = ConstraintMode.AutoSize(
                            minimum = CodeTheme.typography.textMedium,
                        ),
                    )
                }
            }
        }

        if (torn) {
            // The band is kept, the paper in it is not: an outline of the stub that left, with
            // [stub] standing inside it.
            GhostStub(
                modifier = Modifier.height(stubHeight),
                corner = corner,
                content = stub,
            )
        } else {
            VoucherPiece(
                modifier = Modifier.height(stubHeight),
                shape = RoundedCornerShape(square, square, corner, corner),
                spent = spent,
                notchAtBottom = false,
                scored = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = inset),
                    contentAlignment = Alignment.Center,
                ) {
                    stub()
                }
            }
        }
    }
}

/**
 * Where the stub was. The silhouette it would have had, dashed, with its own half-notches traced
 * around rather than struck through — an outline across a notch would close the bite that is the
 * whole evidence of the tear.
 *
 * Drawn rather than left empty because the band is held open in every state, and an empty one reads
 * as a gap in the layout instead of as something taken.
 */
@Composable
private fun GhostStub(
    modifier: Modifier,
    corner: CornerSize,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val notchRadius = with(density) { LinkCardDefaults.NOTCH_RADIUS.toPx() }
    val hairline = with(density) { CodeTheme.dimens.border.toPx() }
    val dash = with(density) {
        PathEffect.dashPathEffect(
            floatArrayOf(LinkCardDefaults.DASH.toPx(), LinkCardDefaults.DASH.toPx()),
        )
    }
    val ghost = LinkCardDefaults.PAPER.copy(alpha = LinkCardDefaults.GHOST_ALPHA)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val r = notchRadius
                val c = corner.toPx(size, this)
                val w = size.width
                val h = size.height
                val outline = Path().apply {
                    moveTo(0f, r)
                    // The bite the left notch took out of the stub's top corner.
                    arcTo(Rect(Offset(-r, -r), Size(r * 2, r * 2)), 90f, -90f, false)
                    lineTo(w - r, 0f)
                    arcTo(Rect(Offset(w - r, -r), Size(r * 2, r * 2)), 180f, -90f, false)
                    lineTo(w, h - c)
                    arcTo(Rect(Offset(w - c * 2, h - c * 2), Size(c * 2, c * 2)), 0f, 90f, false)
                    lineTo(c, h)
                    arcTo(Rect(Offset(0f, h - c * 2), Size(c * 2, c * 2)), 90f, 90f, false)
                    close()
                }
                drawPath(
                    path = outline,
                    color = ghost,
                    style = Stroke(width = hairline, pathEffect = dash),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * One side of the tear: paper, dimmed if [spent], with half a notch cut from each end of the torn
 * edge — the bottom edge when [notchAtBottom], the top edge otherwise. [scored] draws the
 * perforation the piece would be torn along, which only the upper piece of an intact voucher has.
 */
@Composable
private fun VoucherPiece(
    modifier: Modifier,
    shape: RoundedCornerShape,
    spent: Boolean,
    notchAtBottom: Boolean,
    scored: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val notchRadius = with(density) { LinkCardDefaults.NOTCH_RADIUS.toPx() }
    val hairline = with(density) { CodeTheme.dimens.border.toPx() }
    val dash = with(density) {
        PathEffect.dashPathEffect(
            floatArrayOf(LinkCardDefaults.DASH.toPx(), LinkCardDefaults.DASH.toPx()),
        )
    }
    val ink = LinkCardDefaults.INK

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Everything from here down draws into one offscreen layer, which is what lets the
            // notches be cleared out of the ground rather than painted over it. There is no border:
            // an outline drawn straight across a notch would fill in the bite it takes out, and the
            // cut edge is what makes the card read as torn.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(LinkCardDefaults.PAPER)
            .drawWithContent {
                drawContent()
                if (spent) {
                    drawRect(Color.Black, alpha = LinkCardDefaults.SPENT_DIM)
                }
                val edge = if (notchAtBottom) size.height else 0f
                if (scored) {
                    // Pulled inside the edge by half its width so the stroke is not itself clipped.
                    val y = edge - hairline / 2f
                    drawLine(
                        color = ink.copy(alpha = LinkCardDefaults.TEAR_ALPHA),
                        start = Offset(notchRadius, y),
                        end = Offset(size.width - notchRadius, y),
                        strokeWidth = hairline,
                        pathEffect = dash,
                    )
                }
                // Cleared, not filled: the hole shows the transcript, so the notch is right on the
                // background and on a bubble both.
                drawCircle(
                    color = Color.Transparent,
                    radius = notchRadius,
                    center = Offset(0f, edge),
                    blendMode = BlendMode.Clear,
                )
                drawCircle(
                    color = Color.Transparent,
                    radius = notchRadius,
                    center = Offset(size.width, edge),
                    blendMode = BlendMode.Clear,
                )
            },
        content = content,
    )
}

/**
 * What is left to say about a voucher nothing can be done with. [onPaper] is what it is printed on:
 * a stub that is still attached takes ink, one that has been torn off leaves the label standing on
 * the transcript, where ink would be unreadable.
 */
@Composable
private fun StubLabel(text: String, onPaper: Boolean = true) {
    Text(
        text = text,
        style = CodeTheme.typography.textSmall,
        color = if (onPaper) {
            LinkCardDefaults.INK.copy(alpha = LinkCardDefaults.INK_MUTED)
        } else {
            CodeTheme.colors.textSecondary
        },
        maxLines = 1,
    )
}

/**
 * The claimable stub. A filled pill rather than a line of text, because this is the one state where
 * the card is an offer — tapping it opens the link, which is where the claim happens.
 */
@Composable
private fun ClaimPill(text: String) {
    Text(
        modifier = Modifier
            .background(LinkCardDefaults.INK, RoundedCornerShape(percent = 50))
            .padding(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x1,
            ),
        text = text,
        style = CodeTheme.typography.textSmall,
        color = LinkCardDefaults.PAPER,
        maxLines = 1,
    )
}

private object LinkCardDefaults {
    /**
     * 224dp of card across 328dp of usable width — the wallet deck's own numbers on a 360dp phone
     * (`TokenCard`'s default height, full width less two screen insets).
     */
    const val CARD_ASPECT = 224f / 328f

    /** How much of the card the stub under the tear takes. */
    const val STUB_FRACTION = 0.28f

    /** The score line between the notches — a crease, not a border. */
    const val TEAR_ALPHA = 0.45f

    /** How far a claimed or expired voucher is pushed back. */
    const val SPENT_DIM = 0.35f

    /**
     * The voucher is paper. The transcript's payment cards are dark panels on a dark ground and a
     * token's bill is the token's own colour; a light card is neither, which is the point -- a cash
     * link is the one thing in a transcript someone can still pick up.
     */
    val PAPER = Color(0xFFF2F0EA)

    /** What is printed on the paper. */
    val INK = Color(0xFF14121F)

    /** The outline left where a claimed voucher's stub was. */
    const val GHOST_ALPHA = 0.25f

    /** Secondary ink: the token's name and a spent voucher's label. */
    const val INK_MUTED = 0.55f

    val NOTCH_RADIUS = 9.dp
    val DASH = 4.dp

    /** `TipCard`'s own proportion, 269 x 333 (node 9277:121417) — the card this one stands in for. */
    const val TIP_CARD_ASPECT = 333f / 269f

    /**
     * How much of the bubble the portrait card takes. Its height lands a little over [CARD_ASPECT],
     * so a transcript mixing card kinds keeps one rhythm without the tip card being squashed into
     * the other two's landscape frame.
     */
    const val TIP_CARD_WIDTH_FRACTION = 0.62f

    /** Corner and picture as fractions of the card's own width, the way `TipCard` derives its own. */
    const val TIP_CARD_CORNER = 0.08f
    const val TIP_CARD_AVATAR = 0.44f

    /** What separates the handle from the name above it, as on the real card. */
    const val TIP_CARD_HANDLE_ALPHA = 0.5f
}
