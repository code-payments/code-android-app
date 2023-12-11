package com.getcode.view.main.account

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.util.getActivity

@Composable
fun AccountDetails(
    onPageSelected: (AccountPage) -> Unit,
    viewModel: AccountSheetViewModel = hiltViewModel(),
) {
    val dataState by viewModel.stateFlow.collectAsState()
    val context = LocalContext.current

    fun onPage(page: AccountPage) {
        onPageSelected(page)
        viewModel.dispatchEvent(AccountSheetViewModel.Event.Navigate(page))
    }

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
                            onPositive = { onPage(AccountPage.ACCESS_KEY) },
                            onNegative = {}
                        )
                    )

                },
                AccountMainItem(
                    name = R.string.title_phoneNumber,
                    icon = R.drawable.ic_menu_phone,
                    isPhoneLinked = dataState.isPhoneLinked,
                ) { onPage(AccountPage.PHONE) },
            )

            for (action in actions) {
                ListItem(action)
            }
        }
    }
}
