package com.getcode.view.login

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.R
import com.getcode.theme.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton

@SuppressLint("InlinedApi")
@Preview
@Composable
fun SeedInput(
    viewModel: SeedInputViewModel = hiltViewModel(),
    arguments: LoginArgs = LoginArgs(),
) {
    val navigator: CodeNavigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = FocusRequester()

    val notificationPermissionCheck =
        com.getcode.util.permissions.notificationPermissionCheck(isShowError = false) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = topBarHeight)
            .padding(bottom = CodeTheme.dimens.grid.x4)
            .verticalScroll(rememberScrollState())
            .imePadding(),
    ) {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            val (input, wordCount, checkboxValid, captionText) = createRefs()

            Text(
                modifier = Modifier
                    .constrainAs(captionText) {
                        top.linkTo(parent.top)
                    }
                    .padding(top = CodeTheme.dimens.grid.x4),
                style = CodeTheme.typography.textSmall.copy(textAlign = TextAlign.Center),
                color = CodeTheme.colors.textSecondary,
                text = stringResource(R.string.subtitle_loginDescription)
            )

            OutlinedTextField(
                modifier = Modifier
                    .constrainAs(input) {
                        top.linkTo(captionText.bottom)
                    }
                    .padding(top = CodeTheme.dimens.inset)
                    .fillMaxWidth()
                    .height(120.dp)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = VisualTransformation.None,
                value = dataState.wordsString,
                onValueChange = { viewModel.onTextChange(it) },
                textStyle = CodeTheme.typography.textLarge.copy(
                    fontSize = 16.sp,
                ),
                colors = inputColors(),
            )

            Text(
                text = dataState.wordCount.toString(),
                color = CodeTheme.colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .constrainAs(wordCount) {
                        bottom.linkTo(input.bottom)
                    }
                    .padding(bottom = CodeTheme.dimens.grid.x2, start = CodeTheme.dimens.grid.x2)
            )

            if (dataState.isValid) {
                Image(
                    painter = painterResource(id = R.drawable.ic_checked_blue),
                    modifier = Modifier
                        .constrainAs(checkboxValid) {
                            start.linkTo(wordCount.end)
                            top.linkTo(wordCount.top)
                            bottom.linkTo(wordCount.bottom)
                        }
                        .padding(bottom = CodeTheme.dimens.grid.x2, start = CodeTheme.dimens.grid.x1)
                        .height(CodeTheme.dimens.grid.x3),
                    contentDescription = ""
                )
            }
        }

        if (dataState.isSuccess) {
            notificationPermissionCheck(true)
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
                viewModel.onSubmit(navigator)
            },
            isLoading = dataState.isLoading,
            isSuccess = dataState.isSuccess,
            enabled = dataState.continueEnabled,
            text = stringResource(R.string.action_logIn),
            buttonState = ButtonState.Filled,
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}