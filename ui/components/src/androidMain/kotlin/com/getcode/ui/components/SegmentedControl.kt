package com.getcode.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    onSelectionChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    colors: SegmentedButtonColors = SegmentedButtonDefaults.colors(),
    contentPadding: PaddingValues = SegmentedButtonDefaults.ContentPadding,
    space: Dp = SegmentedButtonDefaults.BorderWidth,
    mapper: @Composable SingleChoiceSegmentedButtonRowScope.(T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier,
        space = space,
    ) {
        options.mapIndexed { index, item ->
            SegmentedButton(
                selected = item == selected,
                onClick = { onSelectionChanged(item) },
                colors = colors,
                shape = SegmentedButtonDefaults.itemShape(index, count = options.count()),
                contentPadding = contentPadding,
                icon = {},
                label = {
                    mapper(item)
                },
            )
        }
    }
}

