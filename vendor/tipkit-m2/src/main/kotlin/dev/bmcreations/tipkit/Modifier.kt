package dev.bmcreations.tipkit

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import dev.bmcreations.tipkit.data.PopupData
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Renders the [tip] at of the attached Composable, aligned according to [alignment].
 *
 * @param tip The [Tip] to render when criteria is met
 * @param alignment The [Alignment] line to follow. This is where the tip will align relative to the
 * the anchor. For example, [Alignment.BottomCenter] implies that the tip will render below the anchor
 * centered horizontally, whereas [Alignment.TopStart] implies that the tip will render above and to the left
 * of the anchor.
 * @param padding The space between the anchor and aligned tip.
 */
fun Modifier.popoverTip(
    tip: Tip,
    alignment: Alignment = TipDefaultAlignment,
    padding: PaddingValues = TipDefaultPadding,
) = composed {
    val tipScope = LocalTipScope.current

    var size by remember {
        mutableStateOf(IntSize.Zero)
    }

    var position by remember {
        mutableStateOf(Offset.Zero)
    }

    val data by remember(tip, position, size, alignment, padding) {
        derivedStateOf {
            PopupData(
                tip = tip,
                content = tipScope.buildPopupTip(tip, position, size),
                anchorPosition = position,
                anchorSize = size,
                alignment = alignment,
                padding = padding,
            )
        }
    }

    val tipProvider = LocalTipProvider.current
    LaunchedEffect(tip) {
        tip.observe()
            .filterNot { tip.hasBeenSeen() }
            .map { tip.show() }
            .distinctUntilChanged()
            .filter { it }
            .onEach { tipProvider.show(data) }
            .launchIn(this)

        tip.flowContinuation
            .map { tip.show() }
            .distinctUntilChanged()
            .filter { it }
            .onEach { tipProvider.show(data) }
            .launchIn(this)

    }

    return@composed Modifier.onPlaced {
        size = it.size
        position = it.positionInRoot()
    }
}
