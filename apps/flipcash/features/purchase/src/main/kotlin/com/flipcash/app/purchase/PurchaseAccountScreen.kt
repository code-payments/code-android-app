package com.flipcash.app.purchase

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.purchase.internal.PurchaseAccountScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AppScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PurchaseAccountScreen(
    private val fromLogin: Boolean = false
) : AppScreen, Parcelable {

    @IgnoredOnParcel
    override val testTag: String = "purchase_account_screen"

    @Composable
    override fun ScreenContent() {
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