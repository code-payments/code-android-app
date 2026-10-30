package com.getcode.ui.components.picker

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.fadingEdge
import com.getcode.ui.core.measured
import com.getcode.util.vibration.LocalVibrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun <T> rememberPickerState(
    items: List<T>,
    prefix: String = "",
    initialIndex: Int = 0,
    labelForItem: (T) -> String = { item -> item.toString() }
): PickerState<T> {

    return remember(items, prefix) {
        PickerState(
            items = items,
            initialIndex = initialIndex,
            labelForItem = labelForItem,
            prefix = prefix
        )
    }
}

@Immutable
data class PickerState<T>(
    val items: List<T>,
    val labelForItem: (T) -> String = { item -> item.toString() },
    val initialIndex: Int = 0,
    val prefix: String = "",
) {
    var selectedItem by mutableStateOf<T?>(null)
        internal set
}

@Composable
fun <T> Picker(
    state: PickerState<T>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visibleItemsCount: Int = 3,
    textModifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val items = remember(state.items) {
        val labels = state.items.map { state.labelForItem(it) }
        listOf("") + labels + listOf("")
    }

    fun getItem(index: Int): String = items[index]

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    var itemHeight by remember { mutableStateOf(0.dp) }

    val vibrator = LocalVibrator.current

    LaunchedEffect(items) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .map { (first, last) ->
                val index = ((first + (last ?: first)) / 2).coerceIn(1..items.lastIndex)
                getItem(index)
            }
            .distinctUntilChanged()
            .onEach { vibrator.tick() }
            .debounce(300.milliseconds)
            .onEach { item ->
                withContext(Dispatchers.Main) {
                    state.selectedItem = state.items.find { state.labelForItem(it) == item }
                }
            }.launchIn(this)
    }

    val textMeasurer = rememberTextMeasurer()
    val buffer = with(LocalDensity.current) { CodeTheme.dimens.grid.x4.roundToPx() }
    val itemWidthPixels = remember(items) {
        items.maxOfOrNull {
            textMeasurer.measure(text = it, style = textStyle, maxLines = 1).size.width + buffer
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            userScrollEnabled = enabled,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * visibleItemsCount)
                .fadingEdge()
        ) {
            itemsIndexed(items) { _, item ->
                Text(
                    text = item,
                    maxLines = 1,
                    style = textStyle.copy(Color.White),
                    modifier = Modifier
                        .measured { itemHeight = it.height }
                        .then(textModifier)
                )
            }
        }
        // Fixed prefix
        if (itemWidthPixels != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            ) {
                Text(
                    text = state.prefix,
                    style = textStyle.copy(Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(end = pixelsToDp(itemWidthPixels + buffer))
                )
            }
        }
    }
}

@Composable
private fun pixelsToDp(pixels: Int) = with(LocalDensity.current) { pixels.toDp() }