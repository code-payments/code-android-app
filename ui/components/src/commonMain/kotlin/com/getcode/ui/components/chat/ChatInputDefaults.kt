package com.getcode.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import androidx.compose.ui.graphics.Brush

object ChatInputDefaults {
    /** The field's own fill: a faint lift over the bar. A caller drawing its own glass passes transparent. */
    val ContainerColor = Color.White.copy(alpha = 0.03f)

    /** The field's padding around its controls; the attach menu's leading edge sits this far out from "+"'s. */
    val FieldPadding: Dp
        @Composable get() = 8.dp

    /** The leading and send controls' diameter, and the text row's minimum height. */
    val AccessorySize: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x6

    /**
     * The `$` / cancel slot's diameter, and the height of the one-line field beside it: the
     * accessory plus the padding above and below it. The reply strip, the cancel button and the
     * attach menu's anchor all read this one value.
     */
    val OutsideSize: Dp
        @Composable get() = oneLineFieldHeight(FieldPadding, AccessorySize)

    /**
     * The gap between the `$` / cancel slot and the field. The jump-to-bottom button rides twice this
     * above the `$` button, so it is named here rather than repeated.
     */
    val OutsideSpacing: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2

    /**
     * The corner of the thumbnails the composer's header holds. The field's own corner is this plus
     * the header inset, and the photo chips draw with it, so the two curves share a centre.
     */
    val HeaderChipCorner: Dp
        @Composable get() = CodeTheme.dimens.staticGrid.x2

    /** The 1dp rim on the field and the circle beside it: bright at the top edge, nearly gone at the bottom. */
    val RimBrush: Brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))
    )

    /**
     * The glass behind the composer and the other floating surfaces it sits with. Layer
     * [ContainerColor] and [RimBrush] over it as the composer does.
     */
    @Composable
    fun glassMaterial(): HazeBlurStyle = HazeMaterials.ultraThin(containerColor = CodeTheme.colors.background)
}

internal fun oneLineFieldHeight(fieldPad: Dp, accessorySize: Dp): Dp = fieldPad * 2 + accessorySize
