package com.getcode.view.main.chat.create.byusername

import android.graphics.Paint.Align
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ChatMessageConversationScreen
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.components.ButtonState
import com.getcode.ui.components.CodeButton
import com.getcode.ui.components.CodeScaffold
import com.getcode.ui.components.TextInput
import com.getcode.ui.components.keyboardAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatByUsernameScreen(
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
            .map { it.username }
            .onEach {
                navigator.push(ChatMessageConversationScreen(username = it))
            }.launchIn(this)
    }

    CodeScaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
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