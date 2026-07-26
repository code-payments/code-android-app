package com.flipcash.app.bills.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.app.bills.components.ScannableCode
import com.flipcash.services.models.UserProfile
import com.flipcash.shared.bills.R
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.theme.CodeTheme
import com.getcode.theme.xxl


/** Alpha applied to the card fill so its translucency is identical at every call site. */
private const val TipCardAlpha = 0.82f
/**
 * Base color the tip card is tinted with. Callers may override it, but the alpha is applied
 * uniformly by [TipCard] itself (see [TipCardAlpha]) so the card reads identically everywhere —
 * the differing per-usage opacities were a bug.
 */
val LocalTipCardBaseAlpha = staticCompositionLocalOf { TipCardAlpha }

/**
 * Optional backdrop rendered as the card's bottom layer, clipped to the card's rounded bounds and
 * sitting under the translucent fill. Used to give the card a "frosted glass" look over rich
 * content behind it (e.g. the live camera in the scanner). Null means no backdrop — the card is
 * just its translucent fill.
 */
val LocalTipCardBackdrop = staticCompositionLocalOf<(@Composable BoxScope.() -> Unit)?> { null }



/** Blur radius used for the card's backdrop, tuned to read like the design's 40px background blur. */
val TipCardBlurRadius: Dp = 16.dp

/**
 * Base tint color for the card. Defaults to the theme's tip-card color; overridden on the opaque
 * fallback path (see [TipCardOpaqueFallback]).
 */
val LocalTipCardColor = staticCompositionLocalOf { Color.Unspecified }

/**
 * Opaque stand-in for the frosted-glass tone, used on devices where the live blurred-camera
 * backdrop is disabled (pre-API-31 / low-RAM). Rendered at full opacity so the card stays cheap and
 * never shows the stutter-prone live feed through a translucent fill. Tune to taste to match the
 * average frosted output.
 */
val TipCardOpaqueFallback = Color(0xFF1A1A1C)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TipCard(
    payloadData: List<Byte>,
    user: UserProfile,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
) {
    val fillColor = LocalTipCardColor.current
        .takeOrElse { CodeTheme.colors.tipCardColor }
        .copy(alpha = LocalTipCardBaseAlpha.current)
    val backdrop = LocalTipCardBackdrop.current

    BoxWithConstraints(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = CodeTheme.dimens.inset),
        contentAlignment = contentAlignment
    ) {
        val mW = this.maxWidth
        val codeSize = remember { mW * 0.65f }

        Box(
            modifier = Modifier.clip(CodeTheme.shapes.xxl)
        ) {
            // Backdrop (blurred content behind the card) sits under the translucent fill. It is
            // clipped to the rounded card bounds by the parent's clip.
            if (backdrop != null) {
                Box(modifier = Modifier.matchParentSize(), content = backdrop)
            }

            Column(
                modifier = Modifier
                    .background(color = fillColor)
                    .padding(vertical = CodeTheme.dimens.grid.x8, horizontal = CodeTheme.dimens.grid.x7)
                    .heightIn(0.dp, 800.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4)
            ) {
                if (payloadData.isNotEmpty()) {
                    ScannableCode(
                        modifier = Modifier.size(codeSize),
                        data = payloadData,
                        icon = null,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.label_tip),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )

                    ContactAvatar(
                        modifier = Modifier
                            .padding(start = CodeTheme.dimens.grid.x2, end = CodeTheme.dimens.grid.x1)
                            .size(CodeTheme.dimens.staticGrid.x5)
                            .clip(CircleShape),
                        userProfile = user
                    )

                    Text(
                        text = user.displayName.orEmpty(),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                }
            }
        }
    }
}
