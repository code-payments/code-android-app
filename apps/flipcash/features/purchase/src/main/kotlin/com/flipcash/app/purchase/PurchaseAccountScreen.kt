package com.flipcash.app.purchase

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.purchase.internal.PurchaseAccountScreenContent
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.Parcelize

@Parcelize
class PurchaseAccountScreen: Screen, Parcelable {

    @Composable
    override fun Content() {
        Column {
            AppBarWithTitle(
                backButton = false,
                isInModal = true
            )
            PurchaseAccountScreenContent(getViewModel())
        }
        /** swallow **/
        BackHandler { /** swallow **/ }
    }
}