package com.getcode.view.main.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccountDebugOptionsScreen
import com.getcode.navigation.screens.AccountDetailsScreen
import com.getcode.navigation.screens.BuyMoreKinModal
import com.getcode.navigation.screens.BuySellScreen
import com.getcode.navigation.screens.DepositKinScreen
import com.getcode.navigation.screens.FaqScreen
import com.getcode.navigation.screens.WithdrawalAmountScreen
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.utils.getActivity
import com.getcode.ui.utils.rememberedClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AccountHome(
    viewModel: AccountSheetViewModel,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.stateFlow.collectAsState()
    val context = LocalContext.current

    val composeScope = rememberCoroutineScope()

    val handleItemClicked = remember {
        { item: AccountPage ->
            composeScope.launch {
                when (item) {
                    AccountPage.BUY_KIN -> {
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
                    }
                    AccountPage.DEPOSIT -> navigator.push(DepositKinScreen)
                    AccountPage.WITHDRAW -> navigator.push(WithdrawalAmountScreen)
                    AccountPage.FAQ -> navigator.push(FaqScreen)
                    AccountPage.ACCOUNT_DETAILS -> navigator.push(AccountDetailsScreen)
                    AccountPage.ACCOUNT_DEBUG_OPTIONS -> navigator.push(AccountDebugOptionsScreen)
                    AccountPage.LOGOUT -> {
                        BottomBarManager.showMessage(
                            BottomBarManager.BottomBarMessage(
                                title = context.getString(R.string.prompt_title_logout),
                                subtitle = context
                                    .getString(R.string.prompt_description_logout),
                                positiveText = context.getString(R.string.action_logout),
                                negativeText = context.getString(R.string.action_cancel),
                                onPositive = {
                                    composeScope.launch {
                                        delay(150) // wait for dismiss
                                        context.getActivity()?.let {
                                            viewModel.logout(it)
                                        }
                                    }
                                }
                            )
                        )
                    }

                    AccountPage.PHONE -> Unit
                    AccountPage.DELETE_ACCOUNT -> Unit
                    AccountPage.ACCESS_KEY -> Unit
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(dataState.items, key = { it.type }, contentType = { it }) { item ->
            ListItem(item = item) {
                handleItemClicked(item.type)
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x7)
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = CodeTheme.colors.textSecondary,
                    style = CodeTheme.typography.textSmall.copy(
                        textAlign = TextAlign.Center
                    ),
                )
            }
        }
    }
}

@Composable
fun ListItem(item: AccountMainItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .rememberedClickable { onClick() }
            .padding(CodeTheme.dimens.grid.x5)
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = CenterVertically
    ) {
        Image(
            modifier = Modifier
                .padding(end = CodeTheme.dimens.inset)
                .height(CodeTheme.dimens.staticGrid.x5)
                .width(CodeTheme.dimens.staticGrid.x5),
            painter = painterResource(id = item.icon),
            contentDescription = ""
        )
        Text(
            modifier = Modifier.align(CenterVertically),
            text = stringResource(item.name),
            style = CodeTheme.typography.textLarge.copy(
                fontWeight = FontWeight.Bold
            ),
        )
        item.isPhoneLinked?.let { isPhoneLinked ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isPhoneLinked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        tint = Color.Green,
                        contentDescription = "Linked",
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3)
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(start = CodeTheme.dimens.grid.x1),
                    text = if (isPhoneLinked) stringResource(id = R.string.title_linked)
                    else stringResource(id = R.string.title_notLinked),
                    color = CodeTheme.colors.textSecondary,
                    style = CodeTheme.typography.textMedium.copy(
                        fontSize = 12.sp
                    ),
                )
            }
        }
    }

    Divider(
        modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset),
        color = White10,
        thickness = 0.5.dp
    )
}