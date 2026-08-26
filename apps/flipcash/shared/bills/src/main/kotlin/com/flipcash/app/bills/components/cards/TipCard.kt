package com.flipcash.app.bills.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.bills.components.ScannableCode
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.handle
import com.flipcash.shared.bills.R
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme


/** Alpha applied to the card fill so its translucency is identical at every call site. */
private const val TipCardAlpha = 0.82f
/**
 * Base color the tip card is tinted with. Callers may override it, but the alpha is applied
 * uniformly by [TipCard] itself (see [TipCardAlpha]) so the card reads identically everywhere —
 * the differing per-usage opacities were a bug.
 */
val LocalTipCardBaseAlpha = staticCompositionLocalOf { 1f }

/**
 * Base tint color for the card. Defaults to the opaque [TipCardOpaqueFallback]; callers (e.g. the
 * tip-card screen) may override it. The tip card is always rendered as a solid card.
 */
val LocalTipCardColor = staticCompositionLocalOf { TipCardOpaqueFallback }

/**
 * Opaque stand-in for the frosted-glass tone, used on devices where the live blurred-camera
 * backdrop is disabled (pre-API-31 / low-RAM). Rendered at full opacity so the card stays cheap and
 * never shows the stutter-prone live feed through a translucent fill. Tune to taste to match the
 * average frosted output.
 */
val TipCardOpaqueFallback = Color(0xFF1A1A1C)

/**
 * The card's height-to-width proportion, from Figma (269 x 333 dp).
 */
private const val TipCardAspectRatio = 333f / 269f

/**
 * Fraction of the available canvas width the card occupies when no explicit width is pinned (i.e.
 * on the scanner/camera). Capped at [TipCardMaxWidth]. Mirrors iOS `BillCanvas.tipcardSize`.
 */
private const val TipCardCanvasWidthFraction = 0.82f
// Sized so a phone-width canvas gets the full 0.82 fraction (node 9277:121417 puts the full-screen
// card at 302 on a 402 frame); the cap is really there to stop a tablet blowing the card up.
private val TipCardMaxWidth: Dp = 305.dp

// The card derives its inner metrics from its width, matching iOS `TipcardView`.
private const val TipCardCodeFraction = 0.68f   // scannable code (square)
private const val TipCardAvatarFraction = 0.09f // name-row avatar
private const val TipCardCornerFraction = 0.08f // corner radius
private const val TipCardNameTopFraction = 0.06f // name-row top padding (of card height)
// The name is part of that same proportional figure: Figma draws it at 17 on the 269-wide card and
// scales it with the card, so the 302-wide full-screen card gets 19.1 (node 9277:121421) and the
// 242-wide You-page card 15.3 (node 9276:4645). A fixed size instead left the name looking oversized
// on the small card and undersized on the big one.
private const val TipCardNameFraction = 17f / 269f
// Line height as a multiple of the font size, carried over from `textMedium` (20 on 16) so a name
// that wraps to a second line keeps the same rhythm it had at the fixed size.
private const val TipCardNameLineHeightRatio = 1.25f
// The claimed handle sits directly under the name and at the same size — node 9443:7991 draws both
// at 15 on the 241.6-wide card — so it scales with the rest of the figure. Medium rather than Demi,
// at half opacity, is what separates it from the name; a second type size would not survive the
// card being drawn at three different widths.
private const val TipCardHandleGapFraction = 4f / 241.636f
private const val TipCardHandleAlpha = 0.5f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TipCard(
    payloadData: List<Byte>,
    user: UserProfile,
    modifier: Modifier = Modifier,
    // Explicit card width. When null the card sizes itself to a fraction of the available canvas
    // (the scanner/camera); callers that want a fixed, device-independent card — the "My Tip Card"
    // screen — pin a width here. Matches how iOS computes `TipcardView.size` per surface.
    cardWidth: Dp? = null,
    contentAlignment: Alignment = Alignment.Center,
    includePhoto: Boolean = false,
) {
    val fillColor = LocalTipCardColor.current
        .takeOrElse { CodeTheme.colors.tipCardColor }
        .copy(alpha = LocalTipCardBaseAlpha.current)

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
        contentAlignment = contentAlignment
    ) {
        val width = cardWidth ?: minOf(maxWidth * TipCardCanvasWidthFraction, TipCardMaxWidth)
        val height = width * TipCardAspectRatio
        val codeSize = width * TipCardCodeFraction
        val avatarSize = width * TipCardAvatarFraction
        val cornerRadius = width * TipCardCornerFraction
        // Converted through the density rather than read as sp: the card is a fixed-geometry figure
        // (and is rendered for export), so the name has to keep its proportion whatever the user's
        // font scale is.
        val nameFontSize = with(LocalDensity.current) { (width * TipCardNameFraction).toSp() }

        Box(
            modifier = Modifier
                .size(width, height)
                .clip(RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.matchParentSize().background(color = fillColor))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (payloadData.isNotEmpty()) {
                    ScannableCode(
                        modifier = Modifier.size(codeSize),
                        data = payloadData,
                        icon = null,
                    )
                }

                Column(
                    modifier = Modifier.padding(top = height * TipCardNameTopFraction),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(width * TipCardHandleGapFraction),
                ) {
                    Text(
                        text = stringResource(R.string.label_tipUser, user.displayName),
                        style = CodeTheme.typography.textMedium.copy(
                            fontSize = nameFontSize,
                            lineHeight = nameFontSize * TipCardNameLineHeightRatio,
                        ),
                        color = CodeTheme.colors.textMain,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Only for an account that has claimed one — the line is absent rather than
                    // blank, so a card without a handle is the figure it always was.
                    user.handle?.let { handle ->
                        Text(
                            text = handle,
                            style = CodeTheme.typography.caption.copy(
                                fontSize = nameFontSize,
                                lineHeight = nameFontSize * TipCardNameLineHeightRatio,
                            ),
                            color = CodeTheme.colors.textMain.copy(alpha = TipCardHandleAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
