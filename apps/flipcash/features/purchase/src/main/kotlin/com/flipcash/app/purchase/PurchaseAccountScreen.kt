package com.flipcash.app.purchase

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.purchase.internal.PurchaseAccountScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.Parcelize

@Parcelize
class PurchaseAccountScreen(
    private val fromLogin: Boolean = false
) : Screen, Parcelable {

    @Composable
    override fun Content() {
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
            PurchaseAccountScreen(getViewModel())
        }
    }
}