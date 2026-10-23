package com.flipcash.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.flipcash.core.R

/**
 * A coin face the [ConversionGraphic] can show on either side of the arrow.
 */
enum class ConversionCoin {
    /** The gold "Dollars" coin — Flipcash's own reserve currency. */
    Dollars,

    /** The USDC coin with the Solana badge on its bottom-right corner. */
    UsdcOnSolana,
}

/**
 * The "converted 1:1" education art (Figma node 9216:19798): two coins separated by an arrow.
 *
 * Composed here rather than shipped as one flattened asset so the same art serves both directions —
 * [ConversionCoin.Dollars] to [ConversionCoin.UsdcOnSolana] when withdrawing, and the inverse when
 * adding money from another wallet.
 */
@Composable
fun ConversionGraphic(
    from: ConversionCoin,
    to: ConversionCoin,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        // The badged coin's art is 111x112 — a 100 dp coin face plus the Solana badge overhanging
        // its bottom-right corner. Top-aligning keeps both 100 dp faces on the same line; centering
        // the boxes instead would push the badged one 6 dp out of line with its partner.
        verticalAlignment = Alignment.Top,
        // 14 dp between each pair, matching the 278x124 Figma frame's coin/arrow gaps.
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Coin(from)
        Image(
            // Nudged onto the coin faces' centre line, which the top alignment above leaves at
            // 50 dp: (100 - 28) / 2.
            modifier = Modifier.padding(top = 36.dp).size(28.dp),
            painter = painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
        )
        Coin(to)
    }
}

@Composable
private fun Coin(coin: ConversionCoin) {
    when (coin) {
        ConversionCoin.Dollars -> Image(
            modifier = Modifier.size(100.dp),
            painter = painterResource(R.drawable.ic_coin_dollars),
            contentDescription = null,
        )

        // Natural size (111x112): the 100 dp coin plus the badge overhanging its corner.
        ConversionCoin.UsdcOnSolana -> Image(
            painter = painterResource(R.drawable.ic_usdc_on_solana),
            contentDescription = null,
        )
    }
}
