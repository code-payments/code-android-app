package com.flipcash.app.pools

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.pools.internal.betting.PoolBettingScreen
import com.flipcash.app.pools.internal.betting.PoolBettingViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.opencode.model.core.ID
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.Parcelize

@Parcelize
class PoolBettingScreen(
    val poolId: ID,
): ModalScreen, Parcelable {
    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { navigator.popAll() },
            )
            val viewModel = getViewModel<PoolBettingViewModel>()
            PoolBettingScreen(viewModel)

            LaunchedEffect(viewModel, poolId) {
                viewModel.dispatchEvent(PoolBettingViewModel.Event.OnPoolIdChanged(poolId))
            }
        }
    }

}