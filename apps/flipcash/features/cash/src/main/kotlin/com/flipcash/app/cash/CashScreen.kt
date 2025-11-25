package com.flipcash.app.cash

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.currentOrThrow
import com.flipcash.app.cash.internal.CashScreenViewModel
import com.flipcash.app.cash.internal.GiveScreenContent
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenIconWithName
import com.flipcash.app.session.LocalSessionController
import com.flipcash.features.cash.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.solana.keys.Mint
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.core.rememberedClickable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class CashScreen(
    private val selectedMint: Mint?
) : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val session = LocalSessionController.currentOrThrow

        val viewModel = getViewModel<CashScreenViewModel>()
        val state by viewModel.stateFlow.collectAsStateWithLifecycle()

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<CashScreenViewModel.Event.PresentBill>()
                .onEach {
                    session.showBill(it.bill)
                    navigator.hide()
                }
                .launchIn(this)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .rememberedClickable {
                                    navigator.push(
                                        ScreenRegistry.get(
                                            AppRoute.Sheets.TokenSelection(TokenPurpose.Select)
                                        )
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                space = CodeTheme.dimens.grid.x1,
                                alignment = Alignment.CenterHorizontally
                            )
                        ) {
                            state.token?.let { (token, _) ->
                                TokenIconWithName(
                                    token = token,
                                    imageSize = CodeTheme.dimens.staticGrid.x5,
                                    spacing = CodeTheme.dimens.grid.x1,
                                )

                                Image(
                                    modifier = Modifier
                                        .width(CodeTheme.dimens.grid.x4),
                                    painter = painterResource(R.drawable.ic_dropdown),
                                    contentDescription = ""
                                )
                            }

                        }
                    }
                },
                rightContents = {
                    AppBarDefaults.Close { navigator.hide() }
                }
            )
            GiveScreenContent(viewModel)
        }

        LaunchedEffect(viewModel, selectedMint) {
            selectedMint?.let {
                viewModel.dispatchEvent(CashScreenViewModel.Event.OnTokenSelected(it))
            }
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<CashScreenViewModel.Event.OpenScreen>()
                .map { ScreenRegistry.get(it.screen) }
                .onEach { navigator.push(it) }
                .launchIn(this)
        }
    }
}