package com.getcode.ui.utils


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable


fun LazyListScope.animatedItem(
    key: Any? = null,
    contentType: Any? = null,
    visible: Boolean,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    content: @Composable LazyItemScope.() -> Unit
) {
    item(key = key, contentType = contentType) {
        if (enter != null && exit != null) {
            AnimatedVisibility(
                visible = visible,
                enter = enter,
                exit = exit,
            ) {
                content()
            }
        } else {
            AnimatedVisibility(
                visible = visible,
            ) {
                content()
            }
        }
    }
}

fun LazyListState.isScrolledToTheEnd() =
    layoutInfo.visibleItemsInfo.lastOrNull()?.index == layoutInfo.totalItemsCount - 1

fun LazyListState.isScrolledToTheBeginning() =
    (layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0) == 0

suspend fun LazyListState.scrollToItemWithFullVisibility(index: Int) {
    // 1️⃣ Scroll to the item initially
    scrollToItem(index)

    // 2️⃣ Fetch updated layout info
    val layoutInfo = layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }

    if (itemInfo != null) {
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset

        val itemStart = itemInfo.offset
        val itemEnd = itemInfo.offset + itemInfo.size

        println(
            "ItemStart: $itemStart, ItemEnd: $itemEnd, ViewportStart: $viewportStart, ViewportEnd: $viewportEnd"
        )

        // 3️⃣ Determine if the item is partially clipped upwards
        if (itemStart < viewportStart) {
            val scrollAmount = (viewportStart - itemStart).coerceAtLeast(0)
            println("Item is clipped upwards, scrolling upwards by $scrollAmount")
            scrollBy(-scrollAmount.toFloat()) // Scroll upwards
        }

        // 4️⃣ Determine if the item is partially clipped downwards
        if (itemEnd > viewportEnd) {
            val scrollAmount = (itemEnd - viewportEnd).coerceAtLeast(0)
            println("Item is clipped downwards, scrolling downwards by $scrollAmount")
            scrollBy(scrollAmount.toFloat()) // Scroll downwards
        }

        // 5️⃣ If the item is still misaligned, enforce alignment manually
        val fullyVisible = itemStart >= viewportStart && itemEnd <= viewportEnd
        if (!fullyVisible) {
            println("Item is still misaligned, performing final alignment")
            scrollToItem(index, scrollOffset = 0)
        }
    } else {
        // 6️⃣ Fallback alignment
        println("Item not found in visibleItemsInfo, performing fallback alignment")
        scrollToItem(index, scrollOffset = 0)
    }
}

suspend fun LazyListState.animateScrollToItemWithFullVisibility(to: Int, from: Int) {
    println("🔄 Ensuring full visibility for index: $to")

    val firstIndex = firstVisibleItemIndex
    val firstOffset = firstVisibleItemScrollOffset

    println("📊 FirstVisibleItemIndex: $firstIndex, FirstVisibleItemScrollOffset: $firstOffset")

    val layoutInfo = layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == to }

    // Estimate item height if dynamic size is not available
    val averageItemHeight = itemInfo?.size ?: 200 // Default to 200px if not found

    // Calculate the true item offset based on reverseLayout
    val indexDifference = to - firstIndex
    val estimatedItemStart = firstOffset

    println(
        "📐 Calculated EstimatedItemStart: $estimatedItemStart (IndexDifference: $indexDifference, AverageItemHeight: $averageItemHeight)"
    )

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset

    println("ViewportStart: $viewportStart, ViewportEnd: $viewportEnd")

    // Determine if adjustment is needed
    val scrollOffset = when {
        estimatedItemStart > viewportEnd -> {
            println("🔼 Item is clipped at the bottom in reverse layout, adjusting by ${estimatedItemStart - viewportEnd}")
            estimatedItemStart - viewportEnd // Scroll upwards in reverse
        }
        estimatedItemStart < viewportStart -> {
            println("🔽 Item is clipped at the top in reverse layout, adjusting by ${viewportStart - estimatedItemStart}")
            viewportStart - estimatedItemStart // Scroll downwards in reverse
        }
        else -> {
            println("✅ Item is fully visible in reverse layout, no adjustment needed.")
            0
        }
    }

    // Perform the final scroll adjustment
    println("🎯 Performing final scroll with offset: $scrollOffset")
    animateScrollToItem(to, scrollOffset = scrollOffset)
}
