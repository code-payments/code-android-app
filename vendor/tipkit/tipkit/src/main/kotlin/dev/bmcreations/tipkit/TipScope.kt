package dev.bmcreations.tipkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

val LocalTipScope =
    staticCompositionLocalOf<TipScope> { NoOpTipScopeImpl() }

interface TipScope {
    fun buildPopupTip(
        tip: Tip,
        anchorPosition: Offset,
        anchorSize: IntSize,
    ): @Composable () -> Unit

    fun buildInlineTip(
        tip: Tip,
        onDismiss: () -> Unit
    ): @Composable () -> Unit
}

class NoOpTipScopeImpl : TipScope {

    override fun buildInlineTip(tip: Tip, onDismiss: () -> Unit): @Composable () -> Unit {
        return { }
    }

    override fun buildPopupTip(
        tip: Tip,
        anchorPosition: Offset,
        anchorSize: IntSize,
    ): @Composable () -> Unit {
        return { }
    }
}
