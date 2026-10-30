package com.getcode.ui.theme

import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.ui.components.SegmentedControl

@Composable
fun <T> CodeSegmentedControl(
    options: List<T>,
    selected: T?,
    onSelectionChanged: (T) -> Unit,
    modifier: Modifier = Modifier,
    mapper: @Composable SingleChoiceSegmentedButtonRowScope.(T) -> Unit,
) {
    SegmentedControl(
        options = options,
        selected = selected,
        mapper = mapper,
        space = 0.dp,
        modifier = modifier,
        colors = SegmentedButtonDefaults.colors(
            activeBorderColor = White10,
            activeContentColor = CodeTheme.colors.textMain,
            inactiveContentColor = CodeTheme.colors.textSecondary,
            activeContainerColor = White10,
            inactiveContainerColor = White05,
            inactiveBorderColor = White05,
        ),
        onSelectionChanged = onSelectionChanged,
    )
}