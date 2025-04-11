package com.flipcash.app.login.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flipcash.app.login.seed.SeedInputUiModel
import com.flipcash.app.login.seed.SeedInputViewModel
import com.flipcash.features.login.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.permissions.notificationPermissionCheck

@Composable
internal fun SeedInputContent(viewModel: SeedInputViewModel) {
    val navigator: CodeNavigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()

    SeedInputContent(
        state = dataState,
        onTextChange = { viewModel.onTextChange(it) },
        onLogin = { viewModel.onSubmit(navigator) }
    )
}

@Composable
private fun SeedInputContent(
    state: SeedInputUiModel,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = FocusRequester()

    val notificationPermissionCheck = notificationPermissionCheck(isShowError = false) { }

    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(bottom = CodeTheme.dimens.grid.x4),
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                style = CodeTheme.typography.textSmall.copy(textAlign = TextAlign.Center),
                color = CodeTheme.colors.textSecondary,
                text = stringResource(R.string.subtitle_loginDescription)
            )

            Box {
                OutlinedTextField(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.inset)
                        .fillMaxWidth()
                        .height(120.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = VisualTransformation.None,
                    value = state.wordsString,
                    onValueChange = { onTextChange(it) },
                    textStyle = CodeTheme.typography.textLarge.copy(
                        fontSize = 16.sp,
                    ),
                    colors = inputColors(),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            top = CodeTheme.dimens.grid.x2,
                            bottom = CodeTheme.dimens.grid.x2,
                            start = CodeTheme.dimens.grid.x1
                        ),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.wordCount.toString(),
                        color = CodeTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    if (state.isValid) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_checked_blue),
                            modifier = Modifier
                                .height(CodeTheme.dimens.grid.x3),
                            contentDescription = ""
                        )
                    }
                }
            }

            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = CodeTheme.dimens.grid.x3,
                        bottom = CodeTheme.dimens.grid.x4
                    ),
                onClick = {
                    focusManager.clearFocus()
                    onLogin()
                },
                isLoading = state.isLoading,
                isSuccess = state.isSuccess,
                enabled = state.continueEnabled,
                text = stringResource(R.string.action_logIn),
                buttonState = ButtonState.Filled,
            )

            if (state.isSuccess) {
                notificationPermissionCheck(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}