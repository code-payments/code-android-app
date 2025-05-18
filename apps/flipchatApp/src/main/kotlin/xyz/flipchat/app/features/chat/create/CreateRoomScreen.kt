package xyz.flipchat.app.features.chat.create

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.keyboardAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.features.chat.name.RoomNameScreenContent
import xyz.flipchat.app.features.chat.name.RoomNameScreenViewModel
import kotlin.time.Duration.Companion.seconds

@Parcelize
class CreateRoomScreen : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey


    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        val keyboardIsVisible by keyboardAsState()
        val keyboard = LocalSoftwareKeyboardController.current
        val scope = rememberCoroutineScope()

        val goBack = {
            scope.launch {
                if (keyboardIsVisible) {
                    keyboard?.hide()
                    delay(500)
                }
                navigator.hide()
            }
        }
        BackHandler {
            if (keyboardIsVisible) {
                keyboard?.hide()
                return@BackHandler
            }

            navigator.hide()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AppBarWithTitle(
                backButton = false,
                isInModal = true,
                title = "",
                endContent = {
                    AppBarDefaults.Close { goBack() }
                },
            )

            val viewModel = getViewModel<RoomNameScreenViewModel>()

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<RoomNameScreenViewModel.Event.OnSuccess>()
                    .onEach {
                        delay(2.seconds)
                        navigator.pop()
                    }
                    .launchIn(this)
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<RoomNameScreenViewModel.Event.OpenRoom>()
                    .map { it.roomId }
                    .onEach {
                        navigator.hideWithResult(it)
                    }
                    .launchIn(this)
            }



            val state by viewModel.stateFlow.collectAsState()
            RoomNameScreenContent(
                isUpdate = false,
                state = state,
                dispatch = viewModel::dispatchEvent
            )
        }
    }
}