package xyz.flipchat.app.features.login.register

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.navigation.NavScreenProvider
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
import com.getcode.ui.utils.ConstraintMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.login.register.RegisterDisplayNameViewModel.Event

@Parcelize
class RegisterScreen : Screen, Parcelable {
    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        Column {
            AppBarWithTitle(
                backButton = true,
                onBackIconClicked = navigator::pop
            )
            RegisterDisplayNameScreenContent(getViewModel()) {
                navigator.push(ScreenRegistry.get(NavScreenProvider.CreateAccount.AccessKey(false)))
            }
        }
    }
}

@Composable
internal fun RegisterDisplayNameScreenContent(
    viewModel: RegisterDisplayNameViewModel,
    onShowAccessKey: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsState()

    val keyboardVisible by keyboardAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val composeScope = rememberCoroutineScope()
    val animationScale by rememberAnimationScale()
    var isChecking by remember(state.checkingDisplayName) { mutableStateOf(state.checkingDisplayName) }

    val register = {
        composeScope.launch {
            isChecking = true
            if (keyboardVisible) {
                keyboardController?.hide()
                delay(500.scaled(animationScale))
            }
            viewModel.dispatchEvent(Event.RegisterDisplayName)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<Event.OnSuccess>()
            .onEach { delay(400.scaled(animationScale)) }
            .onEach { onShowAccessKey() }
            .launchIn(this)
    }

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
                    enabled = state.canAdvance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_next),
                    isLoading = isChecking,
                    isSuccess = state.isValidDisplayName
                ) {
                    register()
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
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    maxLines = 1,
                    constraintMode = ConstraintMode.AutoSize(minimum = CodeTheme.typography.displaySmall),
                    style = CodeTheme.typography.displayMedium,
                    placeholderStyle = CodeTheme.typography.displayMedium,
                    placeholder = stringResource(R.string.subtitle_yourName),
                )

                Text(
                    text = stringResource(R.string.subtitle_displayNameHint),
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