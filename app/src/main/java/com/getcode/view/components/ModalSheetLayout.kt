package com.getcode.view.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.getcode.theme.Brand

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
        sheetShape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp),
    ) {
    }
}