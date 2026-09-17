package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.TokenCard
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme

/**
 * The card, in place of the link.
 *
 * A resolved cash link is the same bill the wallet and the token screen draw — [TokenCard] with
 * the link's own mint, so a link to a token is recognisably that token before it is opened. The
 * URL it came from is cut from the body text, so the card is the link rather than an ornament
 * above it; [onClick] hands the URL to the handler the link span used to go through.
 */
@Composable
internal fun LinkCardView(
    card: LinkCard,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The bill's proportions, not its size: the wallet draws a 224dp card across the full screen
    // width less the inset, and a bubble is a good deal narrower than that. Scaling the height with
    // the width is what keeps it a bill in chat instead of a tall, square panel.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val height: Dp = maxWidth * LinkCardDefaults.BILL_ASPECT
        when (card) {
            is LinkCard.Cash -> CashLinkCard(
                card = card,
                height = height,
                onClick = { onClick(card.url) },
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
    // Unresolved is also the unavailable state: a lookup that failed, timed out or was switched
    // off renders here. No token, so no bill — the mint's name and colours are the bill, and
    // guessing them would brand the card as a token the link may not pay out. A neutral panel of
    // the same size instead, which is also what stops the card resizing when the amount lands.
    val state = card.state as? LinkCard.Cash.State.Resolved
    if (state == null) {
        UnresolvedCashCard(height = height, onClick = onClick)
        return
    }

    TokenCard(
        token = state.token,
        balanceText = state.amount,
        // The reserve is branded Dollars everywhere the user meets it; `token.name` off the wire
        // is "USDF", which is the mint, not the thing they hold.
        displayName = when (state.token.address) {
            Mint.usdf -> stringResource(R.string.displayName_dollars)
            else -> state.token.name
        },
        height = height,
        onClick = onClick,
        footer = {
            Text(
                text = stringResource(
                    when (state.claim) {
                        LinkCard.Cash.Claim.Claimed -> R.string.label_linkCard_claimed
                        LinkCard.Cash.Claim.Expired -> R.string.label_linkCard_expired
                        LinkCard.Cash.Claim.Claimable ->
                            // The issuer gets told it is theirs rather than invited to claim it:
                            // `validateClaimEligibility` refuses a self-claim, so "Tap to claim"
                            // here would be an invitation the claim path declines.
                            if (state.issuedByViewer) {
                                R.string.label_linkCard_sentByYou
                            } else {
                                R.string.label_linkCard_claim
                            }
                    },
                ),
                style = CodeTheme.typography.textSmall,
                color = Color.White,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun UnresolvedCashCard(
    height: Dp,
    onClick: () -> Unit,
) {
    val shape = CodeTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color.White.copy(alpha = LinkCardDefaults.GROUND_ALPHA))
            .border(CodeTheme.dimens.border, CodeTheme.colors.surfaceVariant, shape)
            // Tappable in this state too: the link is what is unresolved, not broken.
            .clickable(onClick = onClick)
            .padding(CodeTheme.dimens.inset),
    ) {
        TokenIconWithName(
            modifier = Modifier.align(Alignment.TopStart),
            tokenName = stringResource(R.string.label_linkCard_cash),
            tokenImage = null,
            imageSize = 24.dp,
            spacing = CodeTheme.dimens.grid.x1,
            textStyle = CodeTheme.typography.textSmall,
            textColor = CodeTheme.colors.textMain,
        )
    }
}

private object LinkCardDefaults {
    /**
     * 224dp of card across 328dp of usable width — the wallet deck's own numbers on a 360dp phone
     * (`TokenCard`'s default height, full width less two screen insets).
     */
    const val BILL_ASPECT = 224f / 328f

    /**
     * The unresolved panel's ground: neutral, because there is no token colour to take yet, and
     * only dark enough to separate the card from the bubble it sits on.
     */
    const val GROUND_ALPHA = 0.10f
}
