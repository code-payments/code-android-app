package xyz.flipchat.app.features.chat.description

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.ID
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.TextInput
import com.getcode.ui.components.keyboardAsState
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
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
data class RoomDescriptionScreen(val roomId: ID, val existingDescription: String) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val keyboardIsVisible by keyboardAsState()
        val keyboard = LocalSoftwareKeyboardController.current
        val scope = rememberCoroutineScope()
        val animationScale by rememberAnimationScale()
        val goBack = {
            scope.launch {
                if (keyboardIsVisible) {
                    keyboard?.hide()
                    delay(500.scaled(animationScale))
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
                title = stringResource(R.string.action_changeRoomDescription),
                onBackIconClicked = { goBack() },
            )

            val viewModel = getViewModel<RoomDescriptionScreenViewModel>()

            LaunchedEffect(viewModel, roomId, existingDescription) {
                viewModel.dispatchEvent(
                    RoomDescriptionScreenViewModel.Event.OnNewRequest(
                        roomId,
                        existingDescription
                    )
                )
            }

            LaunchedEffect(viewModel) {
                viewModel.eventFlow
                    .filterIsInstance<RoomDescriptionScreenViewModel.Event.OnSuccess>()
                    .onEach {
                        delay(2.seconds)
                        navigator.pop()
                    }
                    .launchIn(this)
            }

            val state by viewModel.stateFlow.collectAsState()
            RoomDescriptionScreenContent(
                state = state,
                dispatch = viewModel::dispatchEvent
            )
        }
    }
}

@Composable
private fun RoomDescriptionScreenContent(
    state: RoomDescriptionScreenViewModel.State,
    dispatch: (RoomDescriptionScreenViewModel.Event) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

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
                    enabled = state.canCheck || state.previousRoomDescription.isNotEmpty(), // allow resets
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_save),
                    isLoading = state.update.loading,
                    isSuccess = state.update.success
                ) {
                    keyboard?.hide()
                    dispatch(RoomDescriptionScreenViewModel.Event.UpdateDescription)
                    focusRequester.freeFocus()
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CodeTheme.dimens.inset)
                    .align(Alignment.TopStart)
                    .padding(horizontal = CodeTheme.dimens.inset),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
            ) {
                TextInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    state = state.textFieldState,
                    colors = inputColors(backgroundColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    contentPadding = PaddingValues(CodeTheme.dimens.grid.x3),
                    textFieldAlignment = Alignment.TopStart,
                    minHeight = 120.dp,
                    maxLines = 6,
                    isError = state.textFieldState.text.length > state.maxLimit,
                    placeholder = stringResource(R.string.subtitle_roomDescription),
                )
            }
        }

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
    }
}