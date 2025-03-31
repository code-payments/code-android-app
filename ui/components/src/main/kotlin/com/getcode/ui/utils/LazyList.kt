package com.getcode.ui.utils


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

suspend fun LazyListState.scrollToItemWithFullVisibility(to: Int) {
    // 1️⃣ Scroll to the item initially
    scrollToItem(to)

    // 2️⃣ Fetch updated layout info
    val layoutInfo = layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == to }

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
            scrollToItem(to, scrollOffset = 0)
        }
    } else {
        // 6️⃣ Fallback alignment
        println("Item not found in visibleItemsInfo, performing fallback alignment")
        scrollToItem(to, scrollOffset = 0)
    }
}

suspend fun LazyListState.animateScrollToItemWithFullVisibility(to: Int) {
    val previousItemIndex = to - 1

    // First scroll to bring the item into view
    animateScrollToItem(previousItemIndex)

    // Dynamically calculate the correct offset
    val itemInfo = layoutInfo.visibleItemsInfo
        .find { it.index == previousItemIndex }

    itemInfo?.let {
        val viewportEnd = layoutInfo.viewportEndOffset
        val offsetFromEnd = viewportEnd - (it.offset + it.size)

        // Scroll only if the item isn't sufficiently visible
        if (offsetFromEnd > 0) {
            animateScrollToItem(previousItemIndex, scrollOffset = it.offset)
        }
    }
}
