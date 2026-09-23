package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.tokens.brandedName
import com.flipcash.app.core.ui.TokenCard
import com.flipcash.app.core.ui.TokenIcon
import com.flipcash.app.core.ui.rememberShimmerAlpha
import com.flipcash.app.core.ui.shimmer
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LocalLinkCardResolution
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.core.addIf
import com.getcode.ui.utils.ConstraintMode

/**
 * The card, in place of the link.
 *
 * A cash link is a voucher: one amount, made once, spent once. It is drawn as one — the token it
 * pays out named along the top, the amount in the middle, and a perforated stub across the bottom
 * carrying what can be done with it. A token link is drawn as that token's bill instead, the card
 * the link opens to. The split is the point: a voucher for $15 and a card about Dollars should not
 * be the same gold rectangle, so the bill's colours stay with the card that stands for the token.
 *
 * The card sits on a row of its own with the URL left out of the text rows either side, so the card
 * is the link rather than an ornament beside it, and [onClick] has to carry the tap — a link-only
 * message would otherwise draw something that opens nothing. The whole card is handed back rather
 * than its URL, because where a tap should land differs by kind and only the caller knows the
 * transcript it is landing in. Null leaves the card inert, which is what it is with the backdrop up.
 *
 * The card takes the press for its tap, so the transcript's long press has to come through here
 * too as [onLongClick], or pressing a card would select nothing.
 *
 * [card] arrives with its lookup still to do and the card runs it — see [rememberResolvedCard].
 * The transcript hands over what it can read off the message text, which is everything but the
 * amount and the claim, and is not held up by the rest.
 */
@Composable
internal fun LinkCardView(
    card: LinkCard,
    onClick: ((LinkCard) -> Unit)?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val live = rememberResolvedCard(card)
    val shape = CodeTheme.shapes.medium

    if (live is LinkCard.GroupInvite) {
        // Only the button opens a group, so the card takes no tap of its own. The long press
        // still has to reach the transcript, or pressing the card would select nothing.
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .addIf(onLongClick != null) {
                    Modifier.pointerInput(onLongClick) {
                        detectTapGestures(onLongPress = { onLongClick?.invoke() })
                    }
                },
        ) {
            GroupInviteLinkCard(
                card = live,
                minHeight = maxWidth * LinkCardDefaults.CARD_ASPECT,
                onStart = onClick?.let { click -> { click(live) } },
            )
        }
        return
    }

    // The voucher's proportions, not its size: a bubble is a good deal narrower than the wallet's
    // card, and scaling the height with the width is what keeps it a card in chat instead of a
    // tall panel.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // One target for the whole card in every state: an unresolved link is unresolved, not
            // broken, and a spent one still opens to the page that says so.
            .addIf(onClick != null || onLongClick != null) {
                Modifier
                    .clip(shape)
                    .combinedClickable(
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke(live) },
                    )
            },
    ) {
        val height: Dp = maxWidth * LinkCardDefaults.CARD_ASPECT
        when (live) {
            is LinkCard.Cash -> CashLinkCard(card = live, height = height)
            is LinkCard.TokenInfo -> TokenLinkCard(card = live, height = height)
            // Drawn above; unreachable here.
            is LinkCard.GroupInvite -> Unit
        }
    }
}

/**
 * [card] with whatever the lookup has to say about it, asked for here rather than upstream.
 *
 * The transcript used to await this while mapping a page, which meant a chat painted nothing until
 * every link in the first window had come back from the network, and that every link in it was
 * queried whether or not it was ever scrolled to. Asking from the card inverts both: the transcript
 * paints at once, and only a card someone is looking at costs a query.
 *
 * Three pieces, and each is load-bearing.
 *
 * [LinkCardResolution.peek] runs during composition, so a link that has already resolved — scrolled
 * off and back, or drawn a second time — paints resolved on its first frame. Without it the memo is
 * still a suspension away and every such card would shimmer for a hop, which is worse than the wait
 * it stands for because the reader has already seen the answer.
 *
 * The held value is seeded once per card and written over, never reset, so a re-ask keeps the
 * answer on screen while it runs. A voucher re-checked on the claim timer must not blink back to a
 * shimmer to tell the reader nothing changed.
 *
 * [LinkCardResolution.revision] is what a claim reaches a drawn card through. It replaces a re-map
 * of the whole paging window, which is what the transcript needed when it was the one holding the
 * answer.
 */
@Composable
private fun rememberResolvedCard(card: LinkCard): LinkCard {
    val resolution = LocalLinkCardResolution.current
    val revision by resolution.revision.collectAsState()
    var live by remember(card) { mutableStateOf(resolution.peek(card) ?: card) }

    // Keyed on the card, so a row recomposed onto a different message drops the previous link's
    // query instead of finishing it into the new card.
    LaunchedEffect(card, revision) {
        live = resolution.resolve(card)
    }

    return live
}

@Composable
private fun CashLinkCard(
    card: LinkCard.Cash,
    height: Dp,
) {
    // Unresolved is also the unavailable state: a lookup that failed, timed out or was switched off
    // renders here. The same voucher, with nothing filled in — same size, same chrome — so nothing
    // moves or resizes when the amount lands.
    val state = card.state as? LinkCard.Cash.State.Resolved

    // Loading is that same voucher with the amount standing in place and pulsing. It is not a third
    // rendering: the ticket, its brand, its proportions and the fact that it opens are all known
    // without the network and are drawn the moment the message is, so the amount is the only thing
    // left to say is still coming. A failure stops the pulse and the slot goes quiet and empty,
    // which is the unresolved card, which is the one a reader can still tap.
    val loading = card.state is LinkCard.Cash.State.Loading

    CashVoucher(
        height = height,
        loading = loading,
        tokenName = state?.token?.brandedName()
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
    ) {
        if (state == null) {
            // The tap is live before the lookup is — the whole voucher is clickable in every state,
            // and opening the link is where claiming happens — so the stub carries the claim
            // straight away rather than holding an empty slot over a control that already works.
            //
            // Claimable is the assumption until the lookup says otherwise, which means a voucher
            // that turns out to be spent shows the offer for as long as the lookup takes and then
            // withdraws it. That is the trade: an offer that is occasionally retracted, against a
            // claim that is always late. Claimed and Expired both land on a dimmed card with the
            // label under the tear, so the withdrawal is at least unmistakable when it happens.
            StubPill(stringResource(R.string.label_linkCard_claim))
            return@CashVoucher
        }
        when (state.claim) {
            LinkCard.Cash.Claim.Claimed ->
                // Off the paper: the stub this used to sit on left with whoever claimed it.
                StubLabel(stringResource(R.string.label_linkCard_claimed), onPaper = false)
            LinkCard.Cash.Claim.Expired -> StubLabel(stringResource(R.string.label_linkCard_expired))
            // The same voucher whoever is reading it. The transcript already says who sent the
            // link -- the bubble sits on the sender's side -- so a card that read differently for
            // the issuer would be saying it twice, and saying it in the one place both people are
            // looking at the same object.
            LinkCard.Cash.Claim.Claimable -> StubPill(stringResource(R.string.label_linkCard_claim))
        }
    }
}

/**
 * The voucher's top line: the token's icon and name, or just the name until there is a token.
 *
 * Not [TokenIconWithName], because that draws [TokenIcon] unconditionally and [TokenIcon] falls
 * back to a generic user avatar for a null image — so an unresolved voucher was showing a person's
 * silhouette beside the word "Cash", which is both the wrong glyph for money and a claim that an
 * icon arrived when none had. There is no honest icon for a token nobody has fetched, so nothing is
 * drawn in its place.
 *
 * The row is held at the icon's height whether or not an icon is in it, so the name does not move
 * when one lands.
 */
@Composable
private fun TokenRow(tokenName: String, tokenImage: Any?) {
    Row(
        modifier = Modifier.height(LinkCardDefaults.TOKEN_ICON_SIZE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
    ) {
        if (tokenImage != null) {
            TokenIcon(
                image = tokenImage,
                modifier = Modifier.size(LinkCardDefaults.TOKEN_ICON_SIZE),
            )
        }
        Text(
            text = tokenName,
            style = CodeTheme.typography.textSmall,
            color = LinkCardDefaults.INK.copy(alpha = LinkCardDefaults.INK_MUTED),
        )
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
) {
    when (val state = card.state) {
        is LinkCard.TokenInfo.State.Resolved -> TokenCard(
            token = state.token,
            // Empty rather than absent: the header lays the balance out at the end of the row, so
            // an empty string leaves the name alone on the row with nothing to collide with.
            balanceText = "",
            displayName = state.token.brandedName(),
            height = height,
        )

        // The address is the one thing known about the mint before the network answers, and it is
        // what the raw link text showed. So the loading card is the unresolved card, shimmering —
        // not a blank panel, which would leave the reader with less while they waited.
        LinkCard.TokenInfo.State.Loading -> UnresolvedTokenCard(
            mint = card.mint,
            height = height,
            loading = true,
        )

        LinkCard.TokenInfo.State.Unresolved -> UnresolvedTokenCard(
            mint = card.mint,
            height = height,
            loading = false,
        )
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
    loading: Boolean,
) {
    val shape = CodeTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(CodeTheme.colors.surfaceVariant)
            // Behind the address rather than over it: the shimmer says the bill is still coming,
            // and the one thing already known about the token stays readable while it does.
            .then(if (loading) Modifier.shimmer(shape) else Modifier)
            .border(CodeTheme.dimens.border, CodeTheme.colors.surfaceVariant, shape)
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
    loading: Boolean,
    stub: @Composable () -> Unit,
) {
    val corner = CodeTheme.shapes.medium.topStart
    val square = CornerSize(0.dp)
    val inset = CodeTheme.dimens.inset
    val stubHeight = height * LinkCardDefaults.STUB_FRACTION

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
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
                TokenRow(tokenName = tokenName, tokenImage = tokenImage)
                // One slot, one height, whichever of the three things is in it: the amount once
                // it lands, a placeholder while the lookup is out, nothing at all if it failed.
                // The height is held because the column centres what it holds -- without it the
                // empty unresolved card centres the token name on its own and a failed lookup
                // slides the name down the card, which is the one thing a failure must not do.
                // A minimum rather than a fixed height, so a large system font scale grows the
                // slot instead of clipping the amount.
                Box(
                    modifier = Modifier.heightIn(min = LinkCardDefaults.AMOUNT_SLOT_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    if (amount != null) {
                        AnimatedNumberText(
                            value = amount,
                            style = CodeTheme.typography.displaySmall
                                .copy(fontWeight = FontWeight.Bold),
                            color = LinkCardDefaults.INK,
                            constraintMode = ConstraintMode.AutoSize(
                                minimum = CodeTheme.typography.textMedium,
                            ),
                        )
                    } else if (loading) {
                        InkPlaceholder(
                            width = LinkCardDefaults.AMOUNT_PLACEHOLDER_WIDTH,
                            height = LinkCardDefaults.AMOUNT_PLACEHOLDER_HEIGHT,
                        )
                    }
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
 * A slot on the paper with nothing in it yet, pulsing.
 *
 * Ink rather than the white of `Modifier.shimmer`: the voucher is the one light surface in the
 * transcript, and a white shimmer on near-white paper is invisible. The animation is shared with
 * every other skeleton in the app — only what it is painted in differs.
 */
@Composable
private fun InkPlaceholder(
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(percent = 50),
) {
    val alpha = rememberShimmerAlpha()
    Box(
        modifier = Modifier
            .size(width, height)
            .background(LinkCardDefaults.INK.copy(alpha = alpha), shape),
    )
}

/**
 * The stub of a voucher that can still be acted on. A filled pill rather than a line of text,
 * because these are the states where the card is an offer — tapping it opens the link, which is
 * where the claim happens. [StubLabel] is the other half of the pair: flat ink for a voucher that
 * is only reporting what became of it.
 */
@Composable
private fun StubPill(text: String) {
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

    /** The token's icon on the voucher, and the height its row keeps when there is no icon. */
    val TOKEN_ICON_SIZE = 20.dp

    /**
     * The amount's slot, held at the same height in every state so neither resolving nor failing
     * moves the token name above it. `displaySmall`'s own line height, which is what the amount
     * occupies once it arrives.
     */
    val AMOUNT_SLOT_HEIGHT = 36.dp

    /** The placeholder inside that slot — roughly what "$15.00" occupies at `displaySmall`. */
    val AMOUNT_PLACEHOLDER_WIDTH = 104.dp
    val AMOUNT_PLACEHOLDER_HEIGHT = 28.dp

    val NOTCH_RADIUS = 9.dp
    val DASH = 4.dp
}
