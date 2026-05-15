package com.flipcash.app.purchase

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.purchase.internal.PurchaseAccountScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun PurchaseAccountScreen(
    fromLogin: Boolean = false
) {
    val navigator = LocalCodeNavigator.current
    Column {
        AppBarWithTitle(
            backButton = true,
            onBackIconClicked = {
                if (fromLogin) {
                    navigator.popAll()
                } else {
                    navigator.pop()
                }
            }
        )
        PurchaseAccountScreen(hiltViewModel())
    }
}
