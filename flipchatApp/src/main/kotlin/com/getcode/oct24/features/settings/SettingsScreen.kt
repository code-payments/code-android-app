package com.flipchat.features.settings

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.flipchat.features.login.LoginScreen
import com.getcode.manager.SessionManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.oct24.MainRoot
import com.getcode.oct24.SettingsViewModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.parcelize.Parcelize

@Parcelize
data object SettingsScreen : Screen, Parcelable {
    @Composable
    override fun Content() {
        val viewModel = getViewModel<SettingsViewModel>()
        val navigator = LocalCodeNavigator.current
        CodeScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    vertical = CodeTheme.dimens.grid.x2,
                    horizontal = CodeTheme.dimens.inset
                ),
            bottomBar = {
                CodeButton(
                    modifier = Modifier.fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    text = "Log out"
                ) {
                    viewModel.logout {
                        navigator.replaceAll(LoginScreen())
                    }
                }
            }
        ) {

        }
    }

}