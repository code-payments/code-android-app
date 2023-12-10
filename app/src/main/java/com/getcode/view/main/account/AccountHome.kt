package com.getcode.view.main.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.App
import com.getcode.BuildConfig
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.theme.BrandLight
import com.getcode.theme.White10
import com.getcode.util.getActivity

@Composable
fun AccountHome(
    onPageSelected: (AccountPage) -> Unit
) {

    val viewModel = hiltViewModel<AccountSheetViewModel>()
    val dataState by viewModel.uiFlow.collectAsState()
    val context = LocalContext.current

    fun onPage(page: AccountPage) {
        onPageSelected(page)
        viewModel.onNavigation(page)
    }

    Box(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            val actions: MutableList<AccountMainItem> = mutableListOf(
                AccountMainItem(
                    name = R.string.title_depositKin,
                    icon = R.drawable.ic_menu_deposit
                ) { onPage(AccountPage.DEPOSIT) },
                AccountMainItem(
                    name = R.string.title_withdrawKin,
                    icon = R.drawable.ic_menu_withdraw
                ) { onPage(AccountPage.WITHDRAW) },
                AccountMainItem(
                    name = R.string.title_myAccount,
                    icon = R.drawable.ic_menu_account
                ) { onPage(AccountPage.ACCOUNT_DETAILS) },
                AccountMainItem(
                    name = R.string.title_faq,
                    icon = R.drawable.ic_faq,
                ) { onPage(AccountPage.FAQ) },
                AccountMainItem(
                    name = R.string.action_logout,
                    icon = R.drawable.ic_menu_logout
                ) {
                    BottomBarManager.showMessage(
                        BottomBarManager.BottomBarMessage(
                            title = App.getInstance().getString(R.string.prompt_title_logout),
                            subtitle = App.getInstance()
                                .getString(R.string.prompt_description_logout),
                            positiveText = App.getInstance().getString(R.string.action_logout),
                            negativeText = App.getInstance().getString(R.string.action_cancel),
                            onPositive = {
                                context.getActivity()?.let {
                                    viewModel.logout(it)
                                }
                            },
                            onNegative = {
                            }
                        )
                    )
                }
            )

            if (dataState.isDebug) {
                AccountMainItem(
                    name = R.string.account_debug_options,
                    icon = R.drawable.ic_bug,
                ) { onPage(AccountPage.ACCOUNT_DEBUG_OPTIONS) }
                    .let { actions.add(4, it) }
            }

            Image(
                painterResource(
                    R.drawable.ic_code_logo_near_white
                ),
                contentDescription = "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .height(50.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        viewModel.onLogoClick()
                    }
            )

            for (action in actions) {
                ListItem(action)
            }

            Text(
                modifier = Modifier
                    .padding(top = 35.dp)
                    .fillMaxWidth()
                    .align(CenterHorizontally),
                text = "v${BuildConfig.VERSION_NAME}",
                color = BrandLight,
                style = MaterialTheme.typography.body2.copy(
                    textAlign = TextAlign.Center
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.reset()
    }
}

@Composable
fun ListItem(item: AccountMainItem) {
    Row(
        modifier = Modifier
            .clickable { item.onClick() }
            .padding(vertical = 25.dp, horizontal = 25.dp)
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Image(
            modifier = Modifier
                .padding(end = 20.dp)
                .height(25.dp)
                .width(25.dp),
            painter = painterResource(id = item.icon),
            contentDescription = ""
        )
        Text(
            modifier = Modifier.align(CenterVertically),
            text = stringResource(item.name),
            style = MaterialTheme.typography.subtitle1.copy(
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
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(start = 5.dp),
                    text = if (isPhoneLinked) stringResource(id = R.string.title_linked)
                    else stringResource(id = R.string.title_notLinked),
                    color = BrandLight,
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 12.sp
                    ),
                )
            }
        }
    }

    Divider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = White10,
        thickness = 0.5.dp
    )
}