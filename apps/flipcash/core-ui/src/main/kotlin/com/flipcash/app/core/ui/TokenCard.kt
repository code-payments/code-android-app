package com.flipcash.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.money.formattedAppreciation
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenWithLocalizedBalance
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.theme.tiny
import com.getcode.ui.components.text.AnimatedNumberText
import com.getcode.ui.core.addIf
import com.getcode.ui.utils.ConstraintMode
import com.getcode.ui.utils.hexToColor

/**
 * A reusable bill-style card for a single token (Figma frame 8966:99811).
 *
 * The background is painted from the token's **bill-customization colors**
 * ([Token.billCustomizations] → [BillBackground]) as a horizontal gradient — the same palette users
 * pick in the currency creator — falling back to the bill's dark-green when a token has no
 * customization. Header shows the token icon + name (top-left) and the balance + optional
 * appreciation pill (top-right).
 *
 * These are designed to stack: render several in a Column with a negative `verticalArrangement`
 * spacing (~ -160.dp) so only each card's ~64dp header shows, like the Figma frame.
 */
@Composable
fun TokenCard(
    tokenWithBalance: TokenWithLocalizedBalance,
    modifier: Modifier = Modifier,
    height: Dp = 224.dp,
    onClick: (() -> Unit)? = null,
) {
    val (token, balance, appreciation, displayName) = tokenWithBalance
    TokenCard(
        token = token,
        balanceText = balance.nativeAmount.formatted(),
        modifier = modifier,
        displayName = displayName,
        appreciationText = appreciation
            .takeIf { it != LocalFiat.MIN_VALUE }?.nativeAmount
            ?.formattedAppreciation(),
        height = height,
        onClick = onClick,
    )
}

@Composable
fun TokenCard(
    token: Token,
    balanceText: String,
    modifier: Modifier = Modifier,
    displayName: String = token.name,
    appreciationText: String? = null,
    height: Dp = 224.dp,
    onClick: (() -> Unit)? = null,
) {
    val isUsdf = token.address == Mint.usdf
    val shape = CodeTheme.shapes.medium
    val brush = remember(token.billCustomizations, isUsdf) {
        if (isUsdf) UsdfBrush else billCardBrush(token.billCustomizations)
    }

    // USDF's oversized "$" watermark (Figma 9120:15335): a bold white glyph bleeding off the
    // top-right, drawn with mix-blend "overlay" at 30% so it reads as an emboss on the gold, not a
    // solid mark. Pre-measured here; positioned per-frame in the draw layer once the card size is
    // known. Font size is expressed in dp (via toSp) so the watermark ignores the user font scale.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    // Same inset the card content uses, so the "$" hangs off the top-right by one grid unit. Read in
    // composition (it's theme-scoped) and captured for the draw layer; flips to 16dp on the 4pt grid.
    val watermarkInset = CodeTheme.dimens.inset
    val watermark = remember(isUsdf, height, density) {
        if (!isUsdf) null else textMeasurer.measure(
            text = "$",
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { (height * WatermarkFontScale).toSp() },
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(brush)
            // Sits above the gradient but below the header; clipped to the card's rounded shape
            // (applied before this in the chain) and ignores the content padding (applied after).
            .addIf(watermark != null) {
                Modifier.drawWithContent {
                    watermark?.let { glyph ->
                        drawText(
                            textLayoutResult = glyph,
                            topLeft = Offset(
                                x = size.width - glyph.size.width - watermarkInset.toPx(),
                                y = -watermarkInset.toPx(),
                            ),
                            alpha = WatermarkAlpha,
                            blendMode = BlendMode.Overlay,
                        )
                    }
                    drawContent()
                }
            }
            .border(CodeTheme.dimens.border, CodeTheme.colors.surfaceVariant, shape)
            .addIf(onClick != null) { Modifier.clickable { onClick?.invoke() } }
            .padding(CodeTheme.dimens.inset),
    ) {
        // Header: token icon + name (left) and appreciation pill + balance (right). Name and the
        // right cluster split the row via weights so a long balance autosizes down within its half
        // instead of colliding with the name.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenIconWithName(
                tokenName = displayName,
                tokenImage = token.imageUrl,
                imageSize = 24.dp,
                textStyle = CodeTheme.typography.textSmall,
                textColor = CodeTheme.colors.textMain,
                spacing = CodeTheme.dimens.grid.x1,
            )

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                appreciationText?.let {
                    Text(
                        modifier = Modifier
                            .border(CodeTheme.dimens.border, Color.White, CodeTheme.shapes.tiny)
                            .padding(horizontal = CodeTheme.dimens.grid.x1, vertical = CodeTheme.dimens.grid.x1 - 1.dp),
                        text = it,
                        style = CodeTheme.typography.caption,
                        color = Color.White,
                    )
                }
                AnimatedNumberText(
                    value = balanceText,
                    modifier = Modifier.weight(1f, fill = false),
                    style = CodeTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    constraintMode = ConstraintMode.AutoSize(minimum = CodeTheme.typography.textMedium),
                )
            }
        }
    }
}

/**
 * USDF's fixed gold branding (Figma) — used instead of a user-chosen bill color. Derived from the
 * shared [BillBackground.Usdf] gradient so the card matches the full-screen bill exactly.
 */
private val UsdfBrush = Brush.horizontalGradient(BillBackground.Usdf.colors.map { hexToColor(it) })

// USDF "$" watermark tuning (Figma 9120:15335: ~213px glyph on a ~214px-tall card, top -17 / right
// +17, 30% opacity). The glyph size is proportional to the card height so it scales with `height`;
// the top/right bleed uses CodeTheme.dimens.inset (read in composition, see above).
private const val WatermarkFontScale = 0.95f
private const val WatermarkAlpha = 0.30f

/** Horizontal gradient brush from a token's bill-customization colors (matches the Figma cards). */
private fun billCardBrush(customizations: TokenBillCustomizations?): Brush {
    val fallback = Color(0xFF06450F) // matches CashBill's no-customization fallback
    return when (val background = customizations?.background) {
        null -> Brush.horizontalGradient(listOf(fallback, fallback))
        is BillBackground.Solid -> {
            val color = hexToColor(background.colorHex)
            Brush.horizontalGradient(listOf(color, color))
        }
        is BillBackground.Gradient -> {
            val colors = background.colors.map { hexToColor(it) }
            val lastIndex = (colors.size - 1).coerceAtLeast(1)
            val colorStops = colors.mapIndexed { index, color ->
                (index.toFloat() / lastIndex) to color
            }.toTypedArray()

             Brush.horizontalGradient(colorStops = colorStops,)
        }
    }
}