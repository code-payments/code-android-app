package com.getcode.view.main.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager

@Composable
fun AccountDetails(
    onPageSelected: (AccountPage) -> Unit,
    viewModel: AccountSheetViewModel = hiltViewModel(),
) {
    val dataState by viewModel.stateFlow.collectAsState()

    fun handleItemClick(item: AccountPage) {
        when (item) {
            AccountPage.ACCESS_KEY -> {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = App.getInstance()
                            .getString(R.string.prompt_title_viewAccessKey),
                        subtitle = App.getInstance()
                            .getString(R.string.prompt_description_viewAccessKey),
                        positiveText = App.getInstance()
                            .getString(R.string.action_viewAccessKey),
                        negativeText = App.getInstance().getString(R.string.action_cancel),
                        onPositive = { onPageSelected(AccountPage.ACCESS_KEY) },
                        onNegative = {}
                    )
                )
            }

            AccountPage.PHONE,
            AccountPage.DELETE_ACCOUNT -> onPageSelected(item)

            else -> Unit
        }
    }
    Box(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            val actions: List<AccountMainItem> = listOf(
                AccountMainItem(
                    type = AccountPage.ACCESS_KEY,
                    name = R.string.title_accessKey,
                    icon = R.drawable.ic_menu_key
                ),
                AccountMainItem(
                    type = AccountPage.PHONE,
                    name = R.string.title_phoneNumber,
                    icon = R.drawable.ic_menu_phone,
                    isPhoneLinked = dataState.isPhoneLinked,
                ),
                AccountMainItem(
                    type = AccountPage.DELETE_ACCOUNT,
                    name = R.string.action_deleteAccount,
                    icon = R.drawable.ic_delete
                ),
            )

            for (action in actions) {
                ListItem(action) {
                    handleItemClick(action.type)
                }
            }
        }
    }
}
