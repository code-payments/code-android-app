package com.getcode.view.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ModalSheetLayout(
    state: ModalBottomSheetState,
    sheetContent: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheetLayout(
        sheetState = state,
        sheetBackgroundColor = Brand,
        sheetContent = sheetContent,
        sheetShape = CodeTheme.shapes.medium.copy(
            bottomStart = ZeroCornerSize,
            bottomEnd = ZeroCornerSize,
        ),
    ) {
    }
}