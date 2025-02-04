package xyz.flipchat.app.features.chat.name

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.ID
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.TextInput
import com.getcode.ui.components.keyboardAsState
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.ConstraintMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import kotlin.time.Duration.Companion.seconds

@Parcelize
data class RoomNameScreen(val roomId: ID, val customTitle: String) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
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
                navigator.pop()
            }
        }
        BackHandler {
            goBack()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AppBarWithTitle(
                backButton = true,
                title = stringResource(R.string.action_changeRoomName),
                onBackIconClicked = { goBack() },
            )

            val viewModel = getViewModel<RoomNameScreenViewModel>()

            LaunchedEffect(viewModel, roomId, customTitle) {
                viewModel.dispatchEvent(
                    RoomNameScreenViewModel.Event.OnNewRequest(
                        roomId,
                        customTitle
                    )
                )
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<RoomNameScreenViewModel.Event.OnSuccess>()
                    .onEach {
                        delay(2.seconds)
                        navigator.pop()
                    }
                    .launchIn(this)
            }

            val state by viewModel.stateFlow.collectAsState()
            RoomNameScreenContent(
                isUpdate = true,
                state = state,
                dispatch = viewModel::dispatchEvent
            )
        }
    }
}

@Composable
internal fun RoomNameScreenContent(
    isUpdate: Boolean,
    state: RoomNameScreenViewModel.State,
    dispatch: (RoomNameScreenViewModel.Event) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
            ) {
                CodeButton(
                    enabled = state.canCheck || state.previousRoomName.isNotEmpty(), // allow resets
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    buttonState = ButtonState.Filled,
                    text = if (isUpdate) stringResource(R.string.action_save) else stringResource(R.string.action_next),
                    isLoading = state.update.loading,
                    isSuccess = state.update.success
                ) {
                    keyboard?.hide()
                    if (isUpdate) {
                        dispatch(RoomNameScreenViewModel.Event.UpdateName)
                    } else {
                        dispatch(RoomNameScreenViewModel.Event.CreateRoom)
                    }
                }
            }
        }
    ) { padding ->

        val focusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(horizontal = CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
            ) {
                TextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    state = state.textFieldState,
                    colors = inputColors(
                        backgroundColor = Color.Transparent,
                        borderColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    maxLines = 1,
                    constraintMode = ConstraintMode.AutoSize(minimum = CodeTheme.typography.displaySmall),
                    style = CodeTheme.typography.displayMedium,
                    placeholderStyle = CodeTheme.typography.displayMedium,
                    placeholder = stringResource(R.string.subtitle_roomName),
                )

                Text(
                    text = stringResource(R.string.subtitle_roomNameHint),
                    style = CodeTheme.typography.textMedium,
                    color = Color.White.copy(0.4f)
                )
            }
        }

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
    }
}
