package dev.bmcreations.tipkit.data

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.bmcreations.tipkit.Tip
import dev.bmcreations.tipkit.TipDefaultAlignment
import dev.bmcreations.tipkit.TipDefaultPadding

sealed interface TipPresentation {
    val tip: Tip?
    val content: @Composable () -> Unit
}

data class PopupData(
    override val tip: Tip? = null,
    override val content: @Composable () -> Unit = { },
    val anchorPosition: Offset = Offset.Zero,
    val anchorSize: IntSize = IntSize.Zero,
    val alignment: Alignment = TipDefaultAlignment,
    val padding: PaddingValues = TipDefaultPadding,
): TipPresentation

data class InlineTipData(
    override val tip: Tip? = null,
    override val content: @Composable () -> Unit = { },
): TipPresentation

data class TipPaddingPixels(
    val start: Float,
    val top: Float,
    val end: Float,
    val bottom: Float
)
