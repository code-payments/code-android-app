package com.flipcash.app.pools

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.AppRoute
import com.flipcash.app.pools.internal.betting.PoolBettingScreen
import com.flipcash.app.pools.internal.betting.PoolBettingViewModel
import com.getcode.ed25519.Ed25519
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.opencode.model.core.ID
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
class PoolBettingScreen(
    val poolId: ID?,
    val rendezvous: Ed25519.KeyPair?,
) : ModalScreen, Parcelable {
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

            LaunchedEffect(viewModel, rendezvous, poolId) {
                if (rendezvous != null) {
                    viewModel.dispatchEvent(
                        PoolBettingViewModel.Event.OnPoolRendezvousChanged(
                            rendezvous
                        )
                    )
                } else if (poolId != null) {
                    viewModel.dispatchEvent(PoolBettingViewModel.Event.OnPoolIdChanged(poolId))
                }
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<PoolBettingViewModel.Event.OnFailedToLoad>()
                    .onEach {
                        navigator.popAll()
                    }.launchIn(this)
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<PoolBettingViewModel.Event.AddCashToWallet>()
                    .onEach {
                        navigator.push(
                            ScreenRegistry.get(
                                AppRoute.OnRamp.ProviderList(
                                    AppRoute.Pool.Details(
                                        poolId = poolId,
                                        rendezvous = rendezvous
                                    )
                                )
                            )
                        )
                    }.launchIn(this)
            }
        }
    }
}