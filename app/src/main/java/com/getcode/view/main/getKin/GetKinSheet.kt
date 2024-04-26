package com.getcode.view.main.getKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.BuyMoreKinModal
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.navigation.screens.HomeResult
import com.getcode.navigation.screens.RequestKinModal
import com.getcode.navigation.screens.RequestTip
import com.getcode.theme.BrandLight
import com.getcode.theme.BrandMuted
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.Success
import com.getcode.ui.components.CodeCircularProgressIndicator
import com.getcode.ui.components.CodeScaffold
import com.getcode.ui.components.showSnackbar
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.rememberedClickable
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
                    navigator.hideWithResult(HomeResult.ShowTipCard)
                } else {
                    navigator.push(RequestTip)
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
        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = CodeTheme.dimens.inset)
                .then(Modifier.padding(padding)),
        ) {
            val (topSection, bottomSection) = createRefs()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(topSection) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_graphic_wallet),
                    contentDescription = "",
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
                )
                Text(
                    text = stringResource(R.string.title_getKin),
                    style = CodeTheme.typography.h1,
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x3),
                )
                Text(
                    text = stringResource(R.string.subtitle_getKin),
                    style = CodeTheme.typography.body1,
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x2),
                )
            }

            val x10 = CodeTheme.dimens.grid.x15
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = x10)
                    .constrainAs(bottomSection) {
                        top.linkTo(topSection.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    },
            ) {
                Column {
                    for (item in items) {
                        if (!item.isVisible) {
                            continue
                        }

                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(White05),
                        )

                        Row(
                            modifier = Modifier
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
                                    color = if (item.isActive) Color.White else BrandLight,
                                    style = CodeTheme.typography.body2.copy(
                                        textDecoration = if (item.isStrikeThrough) TextDecoration.LineThrough else CodeTheme.typography.button.textDecoration,
                                    ),
                                )
                                item.subtitleText?.let {
                                    Text(
                                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                                        text = it,
                                        style = CodeTheme.typography.caption,
                                        color = BrandLight
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
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
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
    }


    LaunchedEffect(sheetAnimatedIn, dataState.snackbarData) {
        dataState.snackbarData?.let {
            delay(400)
            val result = snackbarHostState.showSnackbar(it)
            if (result == SnackbarResult.ActionPerformed) {
                navigator.hideWithResult(HomeResult.ShowTipCard)
            }
            viewModel.dispatchEvent(GetKinSheetViewModel.Event.ClearSnackbar)
        }
    }
}
