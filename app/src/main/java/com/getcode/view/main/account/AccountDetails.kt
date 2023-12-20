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
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager

@Composable
fun AccountDetails(
    onPageSelected: (AccountPage) -> Unit,
    viewModel: AccountSheetViewModel = hiltViewModel(),
) {
    val dataState by viewModel.stateFlow.collectAsState()

    Box(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            val actions: List<AccountMainItem> = listOf(
                AccountMainItem(
                    name = R.string.title_accessKey,
                    icon = R.drawable.ic_menu_key
                ) {
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

                },
                AccountMainItem(
                    name = R.string.title_phoneNumber,
                    icon = R.drawable.ic_menu_phone,
                    isPhoneLinked = dataState.isPhoneLinked,
                ) { onPageSelected(AccountPage.PHONE) },
                AccountMainItem(
                    name = R.string.action_deleteAccount,
                    icon = R.drawable.ic_delete
                 ) { onPageSelected(AccountPage.DELETE_ACCOUNT) },
            )

            for (action in actions) {
                ListItem(action)
            }
        }
    }
}
