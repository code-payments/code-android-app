package com.flipchat.features.settings

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.flipchat.features.login.LoginScreen
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.oct24.MainRoot
import com.getcode.oct24.R
import com.getcode.oct24.SettingsViewModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
data object SettingsScreen : Screen, Parcelable {
    @Composable
    override fun Content() {
        val viewModel = getViewModel<SettingsViewModel>()
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current
        val composeScope = rememberCoroutineScope()
        CodeScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    vertical = CodeTheme.dimens.grid.x2,
                    horizontal = CodeTheme.dimens.inset
                ),
            bottomBar = {
                LogoutButton {
                    composeScope.launch {
                        delay(150) // wait for dismiss
                        context.getActivity()?.let {
                            viewModel.logout(it) {
                                navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                            }
                        }
                    }
                }
            }
        ) {

        }
    }
}

@Composable
private fun LogoutButton(
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    CodeButton(
        modifier = Modifier.fillMaxWidth(),
        buttonState = ButtonState.Filled,
        text = "Log out"
    ) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = context.getString(R.string.prompt_title_logout),
                subtitle = context
                    .getString(R.string.prompt_description_logout),
                positiveText = context.getString(R.string.action_logout),
                negativeText = context.getString(R.string.action_cancel),
                onPositive = {
                    onConfirmed()
                }
            )
        )
    }
}