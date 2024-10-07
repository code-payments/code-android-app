package com.flipchat.features.chat.lookup

import android.os.Parcelable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.oct24.R
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.ConstraintMode
import com.getcode.ui.components.TextInput
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.keyboardAsState
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object ChatByUsernameScreen : Screen, NamedScreen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(id = R.string.title_whatsTheirUsername)

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        Column {
            AppBarWithTitle(
                title = name,
                backButton = true,
                onBackIconClicked = navigator::pop
            )
            ChatByUsernameScreenContent(getViewModel())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatByUsernameScreenContent(
    viewModel: ChatByUsernameViewModel
) {
    val state by viewModel.stateFlow.collectAsState()
    val navigator = LocalCodeNavigator.current

    val keyboardVisible by keyboardAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val composeScope = rememberCoroutineScope()
    var isChecking by remember(state.checkingUsername) { mutableStateOf(false) }

    val checkUsername = {
        composeScope.launch {
            isChecking = true
            if (keyboardVisible) {
                keyboardController?.hide()
                delay(500)
            }
            viewModel.dispatchEvent(ChatByUsernameViewModel.Event.CheckUsername)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<ChatByUsernameViewModel.Event.OnSuccess>()
            .map { it.user }
            .onEach {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(user = it)))
            }.launchIn(this)
    }

    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                CodeButton(
                    enabled = state.canAdvance,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .padding(bottom = CodeTheme.dimens.grid.x2),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_next),
                    isLoading = isChecking,
                    isSuccess = state.isValidUsername
                ) {
                    checkUsername()
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
            TextInput(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(CodeTheme.dimens.inset)
                    .focusRequester(focusRequester),
                state = state.textFieldState,
                colors = inputColors(
                    backgroundColor = Color.Transparent,
                    borderColor = Color.Transparent
                ),
                maxLines = 1,
                constraintMode = ConstraintMode.AutoSize(minimum = CodeTheme.typography.displaySmall),
                contentPadding = PaddingValues(horizontal = 20.dp),
                style = CodeTheme.typography.displayMedium,
                placeholderStyle = CodeTheme.typography.displayMedium,
                placeholder = stringResource(R.string.subtitle_xUsername),
            )
        }

        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
    }
}