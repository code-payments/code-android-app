package com.getcode.view.main.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.account.AccountFaq

@Composable
fun HomeFaqSheet(isVisible: Boolean = true, onClose: () -> Unit = {}) {
    SheetTitle(
        modifier = Modifier.padding(horizontal = 20.dp),
        title = stringResource(id = R.string.title_faq),
        onCloseIconClicked = onClose,
        closeButton = true
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        if (isVisible) {
            AccountFaq()
        }
    }
}