package dev.bmcreations.tipkit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.getcode.theme.Brand
import com.getcode.theme.BrandLight
import com.getcode.theme.BrandOverlay
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import dev.bmcreations.tipkit.data.InlineTipData
import dev.bmcreations.tipkit.data.PopupData
import dev.bmcreations.tipkit.data.TipPaddingPixels
import dev.bmcreations.tipkit.data.TipPresentation
import dev.bmcreations.tipkit.engines.LocalTipsEngine
import dev.bmcreations.tipkit.engines.TipsEngine
import dev.bmcreations.tipkit.utils.getCoordinatesForPlacement
import kotlinx.coroutines.launch

object TipDefaults {
    private val SurfaceColor: Color
        @Composable get() = Brand.copy(alpha = 0.9f)
    private val ContentColor: Color
        @Composable get() = White
    private val Shape: CornerBasedShape
        @Composable get() = CodeTheme.shapes.medium

    @Composable
    private fun Arrow(padding: PaddingValues) {
        Box(
            modifier = Modifier
                .padding(padding)
                .background(
                    color = SurfaceColor,
                    shape = TriangleEdge()
                )
                .width(CodeTheme.dimens.grid.x3)
                .height(CodeTheme.dimens.grid.x1)
        )
    }

    @Composable
    private fun TipContainer(
        arrow: @Composable () -> Unit = { },
        content: @Composable () -> Unit
    ) {
        Column {
            arrow()
            Box(
                modifier = Modifier.background(SurfaceColor, Shape)
            ) {
                CompositionLocalProvider(LocalContentColor provides ContentColor) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun TipContents(tip: Tip, onDismiss: () -> Unit = { }) {
        val tipProvider = LocalTipProvider.current

        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
        ) {
            tip.asset()()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
            ) {
                tip.title()()
                tip.message()()
            }
            if (tip.showClose()) {
                Icon(
                    modifier = Modifier.clickable {
                        tipProvider.dismiss()
                        onDismiss()
                    },
                    imageVector = Icons.Default.Close,
                    contentDescription = "dismiss tip"
                )
            }
        }


        if (tip.actions().isNotEmpty()) {
            Row {
                Spacer(Modifier.width(CodeTheme.dimens.grid.x6))
                Column(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
                ) {
                    Divider(color = BrandLight)
                    tip.actions().onEach {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tipProvider.onActionClicked(it) },
                            text = it.title,
                            color = White
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun PopupContent(tip: Tip, anchorPosition: Offset, anchorSize: IntSize) {
        TipContainer(
            arrow = {
                Arrow(padding = PaddingValues(start = (anchorPosition.x).dp))
            }
        ) {
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            Column(
                modifier = Modifier
                    .padding(CodeTheme.dimens.grid.x2)
                    .widthIn(max = 240.dp),
            ) {
                TipContents(tip = tip)
            }
        }
    }

    @Composable
    fun InlineContent(tip: Tip, onDismiss: () -> Unit) {
        TipContainer {
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth(),
            ) {
                TipContents(tip = tip, onDismiss = onDismiss)
            }
        }
    }
}

private class TipScopeImpl : TipScope {
    override fun buildPopupTip(
        tip: Tip,
        anchorPosition: Offset,
        anchorSize: IntSize
    ): @Composable () -> Unit {
        return {
            TipDefaults.PopupContent(
                tip = tip,
                anchorPosition = anchorPosition,
                anchorSize = anchorSize
            )
        }
    }

    override fun buildInlineTip(tip: Tip, onDismiss: () -> Unit): @Composable () -> Unit {
        return { TipDefaults.InlineContent(tip = tip, onDismiss = onDismiss) }
    }
}

@Composable
fun TipScaffold(
    modifier: Modifier = Modifier,
    tipsEngine: TipsEngine,
    tipScope: TipScope = TipScopeImpl(),
    navigator: TipActionNavigation = NoOpTipNavigator(),
    content: @Composable TipScope.() -> Unit
) {
    var emission by remember {
        mutableStateOf<TipPresentation?>(null)
    }

    val composeScope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        val tipProvider = object : TipProvider() {
            override fun show(data: TipPresentation) {
                emission = data
            }

            override fun dismiss() {
                composeScope.launch {
                    val tip = emission?.tip
                    emission = null
                    tip?.dismiss()
                }
            }

            override fun onActionClicked(action: TipAction) {
                dismiss()
                navigator.onActionClicked(action)
            }

            override val isTipShowing: Boolean
                get() = emission != null
        }

        val density = LocalDensity.current
        val ldr = LocalLayoutDirection.current
        CompositionLocalProvider(
            LocalTipsEngine provides tipsEngine,
            LocalTipProvider provides tipProvider,
            LocalTipScope provides tipScope
        ) {
            tipScope.content()
            emission?.let { data ->
                when (data) {
                    is PopupData -> {
                        val paddingStartPx =
                            with(density) { data.padding.calculateStartPadding(ldr).toPx() }
                        val paddingTopPx =
                            with(density) { data.padding.calculateTopPadding().toPx() }
                        val paddingEndPx =
                            with(density) { data.padding.calculateEndPadding(ldr).toPx() }
                        val paddingBottomPx =
                            with(density) { data.padding.calculateBottomPadding().toPx() }
                        val paddingInPixels = TipPaddingPixels(
                            paddingStartPx, paddingTopPx, paddingEndPx, paddingBottomPx
                        )
                        Box(modifier = Modifier
                            .layout { measurable, constraints ->
                                val tip = measurable.measure(constraints)
                                layout(tip.width, tip.height) {
                                    tipProvider.debugLog(
                                        "anchorPosition (${data.anchorPosition})," +
                                                " anchorSize (${data.anchorSize}), " +
                                                " padding ${paddingInPixels}, " +
                                                " tip width ${tip.width}, " +
                                                " tip height ${tip.height}, " +
                                                " maxWidth=${constraints.maxWidth}, " +
                                                " maxHeight=${constraints.maxHeight}"
                                    )

                                    val (x, y) = getCoordinatesForPlacement(
                                        constraints,
                                        data.alignment,
                                        data.anchorPosition,
                                        data.anchorSize,
                                        paddingInPixels,
                                        tip
                                    ) { tipProvider.debugLog(it) }

                                    tip.place(x, y)
                                }
                            }
                        ) {
                            data.content()
                        }
                    }

                    is InlineTipData -> {
                        // This type is handled inline
                    }
                }
            }
        }
    }
}

@Composable
private fun PaddingValues.calculateHorizontalPadding(): Dp {
    val ldr = LocalLayoutDirection.current
    return calculateLeftPadding(ldr) + calculateRightPadding(ldr)
}

@Composable
private fun PaddingValues.calculateVerticalPadding(): Dp {
    return calculateTopPadding() + calculateBottomPadding()
}
