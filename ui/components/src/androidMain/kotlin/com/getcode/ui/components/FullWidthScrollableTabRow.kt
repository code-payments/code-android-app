package com.getcode.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.contentColorFor
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
@UiComposable
fun FullWidthScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primarySurface,
    contentColor: Color = contentColorFor(backgroundColor),
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowPadding,
    indicator: @Composable @UiComposable
        (tabPositions: List<TabPosition>) -> Unit = @Composable { tabPositions ->
        TabRowDefaults.Indicator(
            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
        )
    },
    divider: @Composable @UiComposable () -> Unit =
        @Composable {
            TabRowDefaults.Divider()
        },
    tabs: @Composable @UiComposable () -> Unit
) {
    BoxWithConstraints(
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = backgroundColor,
            contentColor = contentColor
        ) {
            val scrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            val scrollableTabData = remember(scrollState, coroutineScope) {
                ScrollableTabData(
                    scrollState = scrollState,
                    coroutineScope = coroutineScope
                )
            }
            SubcomposeLayout(
                Modifier.fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .selectableGroup()
                    .clipToBounds()
            ) { constraints ->
                val padding = edgePadding.roundToPx()
                val availableWidth = maxWidth.roundToPx() - (padding * 2)

                // Measure tabs first to get their count
                val tabPlaceables = subcompose(TabSlots.Tabs, tabs)
                val tabCount = tabPlaceables.size
                val tabWidth = if (tabCount > 0) {
                    val minTabWidth = (availableWidth / tabCount).coerceAtLeast(ScrollableTabRowMinimumTabWidth.roundToPx())
                    if (minTabWidth * tabCount <= availableWidth) {
                        // If tabs fit without scrolling, divide screen width evenly
                        availableWidth / tabCount
                    } else {
                        // If scrolling is needed, use a minimum width (could adjust this value)
                        ScrollableTabRowMinimumTabWidth.roundToPx()
                    }
                } else {
                    0
                }

                // Constrain each tab to equal width
                val tabConstraints = constraints.copy(
                    minWidth = tabWidth,
                    maxWidth = tabWidth
                )

                // Measure tabs with equal width
                val measuredTabPlaceables = tabPlaceables.fastMap {
                    it.measure(tabConstraints)
                }

                var layoutWidth = padding * 2
                measuredTabPlaceables.fastForEach {
                    layoutWidth += it.width
                }
                val layoutHeight = measuredTabPlaceables.fastMaxBy { it.height }?.height ?: 0

                // Position the children
                layout(layoutWidth, layoutHeight) {
                    // Place the tabs
                    val tabPositions = mutableListOf<TabPosition>()
                    var left = padding
                    measuredTabPlaceables.fastForEach {
                        it.placeRelative(left, 0)
                        tabPositions.add(TabPosition(left = left.toDp(), width = it.width.toDp()))
                        left += it.width
                    }

                    // Place divider
                    subcompose(TabSlots.Divider, divider).fastForEach {
                        val placeable = it.measure(
                            constraints.copy(
                                minHeight = 0,
                                minWidth = layoutWidth,
                                maxWidth = layoutWidth
                            )
                        )
                        placeable.placeRelative(0, layoutHeight - placeable.height)
                    }

                    // Place indicator
                    subcompose(TabSlots.Indicator) {
                        indicator(tabPositions)
                    }.fastForEach {
                        it.measure(Constraints.fixed(layoutWidth, layoutHeight)).placeRelative(0, 0)
                    }

                    scrollableTabData.onLaidOut(
                        density = this@SubcomposeLayout,
                        edgeOffset = padding,
                        tabPositions = tabPositions,
                        selectedTab = selectedTabIndex
                    )
                }
            }
        }
    }
}

@Immutable
class TabPosition internal constructor(val left: Dp, val width: Dp) {
    val right: Dp get() = left + width

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabPosition) return false

        if (left != other.left) return false
        if (width != other.width) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + width.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabPosition(left=$left, right=$right, width=$width)"
    }
}

private enum class TabSlots {
    Tabs,
    Divider,
    Indicator
}

/**
 * Class holding onto state needed for [ScrollableTabRow]
 */
private class ScrollableTabData(
    private val scrollState: ScrollState,
    private val coroutineScope: CoroutineScope
) {
    private var selectedTab: Int? = null

    fun onLaidOut(
        density: Density,
        edgeOffset: Int,
        tabPositions: List<TabPosition>,
        selectedTab: Int
    ) {
        // Animate if the new tab is different from the old tab, or this is called for the first
        // time (i.e selectedTab is `null`).
        if (this.selectedTab != selectedTab) {
            this.selectedTab = selectedTab
            tabPositions.getOrNull(selectedTab)?.let {
                // Scrolls to the tab with [tabPosition], trying to place it in the center of the
                // screen or as close to the center as possible.
                val calculatedOffset = it.calculateTabOffset(density, edgeOffset, tabPositions)
                if (scrollState.value != calculatedOffset) {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            calculatedOffset,
                            animationSpec = ScrollableTabRowScrollSpec
                        )
                    }
                }
            }
        }
    }

    /**
     * @return the offset required to horizontally center the tab inside this TabRow.
     * If the tab is at the start / end, and there is not enough space to fully centre the tab, this
     * will just clamp to the min / max position given the max width.
     */
    private fun TabPosition.calculateTabOffset(
        density: Density,
        edgeOffset: Int,
        tabPositions: List<TabPosition>
    ): Int = with(density) {
        val totalTabRowWidth = tabPositions.last().right.roundToPx() + edgeOffset
        val visibleWidth = totalTabRowWidth - scrollState.maxValue
        val tabOffset = left.roundToPx()
        val scrollerCenter = visibleWidth / 2
        val tabWidth = width.roundToPx()
        val centeredTabOffset = tabOffset - (scrollerCenter - tabWidth / 2)
        // How much space we have to scroll. If the visible width is <= to the total width, then
        // we have no space to scroll as everything is always visible.
        val availableSpace = (totalTabRowWidth - visibleWidth).coerceAtLeast(0)
        return centeredTabOffset.coerceIn(0, availableSpace)
    }
}

private val ScrollableTabRowMinimumTabWidth = 60.dp

/**
 * [AnimationSpec] used when scrolling to a tab that is not fully visible.
 */
private val ScrollableTabRowScrollSpec: AnimationSpec<Float> = tween(
    durationMillis = 250,
    easing = FastOutSlowInEasing
)

fun Modifier.tabIndicatorOffset(
    currentTabPosition: TabPosition
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "tabIndicatorOffset"
        value = currentTabPosition
    }
) {
    val currentTabWidth by animateDpAsState(
        targetValue = currentTabPosition.width,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
    )
    val indicatorOffset by animateDpAsState(
        targetValue = currentTabPosition.left,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
    )
    fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
        .width(currentTabWidth)
}