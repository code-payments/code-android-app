package com.flipcash.shared.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.theme.CodeTheme

/**
 * The card, above the sender's text.
 *
 * Additive by construction: the URL stays a tappable span in the text below, the card is another
 * way in to the same link, and a message renders correctly with the card removed. Tapping it does
 * what tapping the text does — [onClick] hands the URL to the same handler the link span uses.
 */
@Composable
internal fun LinkCardView(
    card: LinkCard,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is LinkCard.Cash -> CashLinkCard(
            card = card,
            onClick = { onClick(card.url) },
            modifier = modifier,
        )
    }
}

@Composable
private fun CashLinkCard(
    card: LinkCard.Cash,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(LinkCardDefaults.shape)
            .background(Color.White.copy(alpha = LinkCardDefaults.groundAlpha))
            .clickable(onClick = onClick)
            .padding(
                horizontal = LinkCardDefaults.horizontalPadding,
                vertical = LinkCardDefaults.verticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(LinkCardDefaults.rowGap),
    ) {
        // Unresolved is also the unavailable state: a lookup that failed, timed out or was
        // switched off renders here. Branded, no amount, no error — the link underneath still
        // works, and a card that shouted about a failed read would be worse than one that waits.
        val state = card.state as? LinkCard.Cash.State.Resolved

        TokenIconWithName(
            tokenName = state?.tokenSymbol?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.label_linkCard_cash),
            tokenImage = state?.iconUrl,
            imageSize = CodeTheme.dimens.staticGrid.x4,
            spacing = CodeTheme.dimens.grid.x1,
            textStyle = CodeTheme.typography.caption,
            textColor = CodeTheme.colors.textSecondary,
        )

        if (state != null) {
            Text(
                text = state.amount,
                style = CodeTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
                color = CodeTheme.colors.textMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The card's measurements, taken from [ChatQuotePanel] rather than invented: the two are siblings —
 * a panel nested inside a filled bubble — and a second set of numbers for the same relationship is
 * what makes one of them look wrong against the bubble's edge.
 */
private object LinkCardDefaults {
    /** Concentric with the bubble, floored at its flattened corner, exactly as the citation is. */
    val shape: Shape
        @Composable get() = RoundedCornerShape(
            max(
                BubbleDefaults.cornerLarge - BubbleDefaults.surroundInset,
                BubbleDefaults.cornerSmall,
            )
        )

    val horizontalPadding: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2
    val verticalPadding: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2
    val rowGap: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x1

    /**
     * Neutral rather than the citation's author tint: a link card has no author colour to take,
     * and the ground only has to separate the card from the bubble it sits on.
     */
    const val groundAlpha = 0.10f
}
