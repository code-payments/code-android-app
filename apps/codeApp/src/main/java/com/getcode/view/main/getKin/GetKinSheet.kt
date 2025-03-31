package com.getcode.view.main.getKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.currentOrThrow
import com.getcode.LocalSession
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.BuyMoreKinModal
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.navigation.screens.ConnectAccount
import com.getcode.navigation.screens.RequestKinModal
import com.getcode.theme.BrandMuted
import com.getcode.theme.CodeTheme
import com.getcode.theme.Success
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.bolded
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.components.snack.showSnackbar
import com.getcode.ui.core.addIf
import com.getcode.ui.core.rememberedClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class GetKinItem(
    val imageResId: Int,
    val inactiveImageResId: Int = imageResId,
    val titleText: String,
    val subtitleText: String? = null,
    val isVisible: Boolean = true,
    val isActive: Boolean = true,
    val isLoading: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun GetKinSheet(
    viewModel: GetKinSheetViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val session = LocalSession.currentOrThrow

    val dataState by viewModel.stateFlow.collectAsState()

    var sheetAnimatedIn by rememberSaveable(viewModel) {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    val items = listOf(
        GetKinItem(
            imageResId = R.drawable.ic_currency_dollar_active,
            titleText = stringResource(R.string.subtitle_buyKin),
            onClick = {
                if (dataState.buyModule.enabled) {
                    if (dataState.buyModule.available) {
                        navigator.push(BuyMoreKinModal())
                    } else {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                title = context.getString(R.string.error_title_buyModuleUnavailable),
                                message = context.getString(R.string.error_description_buyModuleUnavailable),
                                type = TopBarManager.TopBarMessageType.ERROR
                            )
                        )
                    }
                } else {
                    navigator.push(BuySellScreen)
                }
            },
        ),
        GetKinItem(
            imageResId = R.drawable.ic_menu_tip_card,
            titleText = stringResource(R.string.title_requestTip),
            isVisible = dataState.tips.enabled,
            onClick = {
                if (dataState.isTipCardConnected) {
                    session.presentShareableTipCard()
                    navigator.hide()
                } else {
                    navigator.push(ConnectAccount())
                }
            },
        ),
        GetKinItem(
            imageResId = R.drawable.ic_menu_buy_kin,
            titleText = stringResource(R.string.title_requestKin),
            isVisible = dataState.requestKin.enabled,
            onClick = {
                navigator.push(RequestKinModal())
            },
        ),
    )

    LaunchedEffect(viewModel) {
        snapshotFlow { navigator.sheetFullyVisible }
            .onEach {
                sheetAnimatedIn = true
            }.launchIn(this)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scaffoldState = rememberScaffoldState(snackbarHostState = snackbarHostState)

    CodeScaffold(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        scaffoldState = scaffoldState,
        snackbarHost = {
            SnackbarHost(it) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = CodeTheme.shapes.small,
                    backgroundColor = BrandMuted,
                    contentColor = CodeTheme.colors.onBackground,
                    actionColor = Success
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CodeTheme.dimens.inset)
                .then(Modifier.padding(padding)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Header()
            Items(items)
            Spacer(modifier = Modifier.requiredHeight(CodeTheme.dimens.grid.x12))
        }
    }


    LaunchedEffect(sheetAnimatedIn, dataState.snackbarData) {
        dataState.snackbarData?.let {
            delay(400)
            val result = snackbarHostState.showSnackbar(it)
            if (result == SnackbarResult.ActionPerformed) {
                session.presentShareableTipCard()
                navigator.hide()
            }
            viewModel.dispatchEvent(GetKinSheetViewModel.Event.ClearSnackbar)
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.ic_graphic_wallet),
            contentDescription = "",
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
        )
        Text(
            text = stringResource(R.string.title_getCash),
            style = CodeTheme.typography.displayMedium.bolded(),
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
        )
        Text(
            text = stringResource(R.string.subtitle_getKin),
            style = CodeTheme.typography.textMedium,
            modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
        )
    }
}

@Composable
private fun Items(items: List<GetKinItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column {
            items
                .filter { it.isVisible }
                .onEach { item ->
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(White05),
                    )
                    GetKinItemRow(item = item)
                }
        }

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(White05),
        )
    }
}

@Composable
private fun GetKinItemRow(modifier: Modifier = Modifier, item: GetKinItem) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .addIf(
                item.isStrikeThrough.not(),
            ) {
                Modifier.rememberedClickable { item.onClick() }
            }
            .padding(
                vertical = CodeTheme.dimens.grid.x4,
                horizontal = CodeTheme.dimens.grid.x2
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
            painter = if (item.isActive) painterResource(id = item.imageResId) else painterResource(
                id = item.inactiveImageResId
            ),
            contentDescription = "",
        )
        Column(
            modifier = Modifier
                .padding(start = CodeTheme.dimens.grid.x3)
                .weight(1f),
        ) {
            Text(
                text = item.titleText,
                color = if (item.isActive) Color.White else CodeTheme.colors.brandLight,
                style = CodeTheme.typography.textSmall.copy(
                    textDecoration = if (item.isStrikeThrough) TextDecoration.LineThrough else null,
                ),
            )
            item.subtitleText?.let {
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                    text = it,
                    style = CodeTheme.typography.caption,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }

        if (item.isLoading) {
            CodeCircularProgressIndicator(
                strokeWidth = CodeTheme.dimens.thickBorder,
                color = White,
                modifier = Modifier
                    .size(CodeTheme.dimens.grid.x3)
                    .align(Alignment.CenterVertically),
            )
        }
    }
}
