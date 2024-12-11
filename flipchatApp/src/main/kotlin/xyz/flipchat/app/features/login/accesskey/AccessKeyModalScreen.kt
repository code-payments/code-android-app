package xyz.flipchat.app.features.login.accesskey

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.ui.LocalUserManager

@Parcelize
class AccessKeyModalScreen : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val userManager = LocalUserManager.current
        val viewModel = getViewModel<LoginAccessKeyViewModel>()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AccessKeyScreenContent(viewModel) {
                navigator.hideWithResult(userManager?.userFlags?.isRegistered == true)
            }
        }

        BackHandler { /* intercept */ }
    }
}