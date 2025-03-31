package com.getcode.view.main.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.screens.BackupScreen
import com.getcode.navigation.screens.DeleteCodeScreen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.PhoneNumberScreen

@Composable
fun AccountDetails(
    viewModel: AccountSheetViewModel,
) {
    val context = LocalContext.current
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.stateFlow.collectAsState()

    fun handleItemClick(item: AccountPage) {
        when (item) {
            AccountPage.ACCESS_KEY -> {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = context.getString(R.string.prompt_title_viewAccessKey),
                        subtitle = context.getString(R.string.prompt_description_viewAccessKey),
                        positiveText = context.getString(R.string.action_viewAccessKey),
                        negativeText = context.getString(R.string.action_cancel),
                        onPositive = { navigator.push(BackupScreen) },
                        onNegative = {}
                    )
                )
            }

            AccountPage.PHONE -> navigator.push(PhoneNumberScreen)
            AccountPage.DELETE_ACCOUNT -> navigator.push(DeleteCodeScreen)

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
