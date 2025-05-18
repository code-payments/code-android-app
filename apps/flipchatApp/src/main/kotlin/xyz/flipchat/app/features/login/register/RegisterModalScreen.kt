package xyz.flipchat.app.features.login.register

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.hilt.getViewModel
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.FullScreenModalScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.keyboardAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
class RegisterModalScreen : FullScreenModalScreen, Parcelable {

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val keyboardIsVisible by keyboardAsState()
        val keyboard = LocalSoftwareKeyboardController.current
        val composeScope = rememberCoroutineScope()

        Column {
            AppBarWithTitle(
                backButton = false,
                endContent = {
                    AppBarDefaults.Close {
                        composeScope.launch {
                            if (keyboardIsVisible) {
                                keyboard?.hide()
                                delay(500)
                            }
                            navigator.hide()
                        }
                    }
                }
            )
            RegisterDisplayNameScreenContent(getViewModel()) {
                navigator.push(ScreenRegistry.get(NavScreenProvider.CreateAccount.AccessKey(true)))
            }
        }
    }
}