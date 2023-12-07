package com.getcode.view.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.theme.sheetHeight
import com.getcode.view.main.home.ModalSheetLayout


enum class BackType {
    Close, Back
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BaseCodeBottomsheet(
    state: ModalBottomSheetState,
    title: String,
    backType: BackType = BackType.Back,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalSheetLayout(state) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            SheetTitle(
                modifier = Modifier.padding(horizontal = 20.dp),
                title = title,
                onCloseIconClicked = onBack,
                onBackIconClicked = onBack,
                backButton = backType == BackType.Back,
                closeButton = backType == BackType.Close
            )
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }
        }
    }

    //handle back appropriately
    if (backType == BackType.Back) {
        BackHandler { onBack() }
    }
}
