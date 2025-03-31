package xyz.flipchat.app.features.login.accesskey

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.constraintlayout.compose.ConstraintLayout
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import xyz.flipchat.app.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.theme.topBarHeight
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.util.permissions.notificationPermissionCheck
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data object SeedInputScreen: Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_enterAccessKeyWords)

    @Composable
    override fun Content() {
        val viewModel: SeedInputViewModel = getViewModel()
        val navigator = LocalCodeNavigator.current
        Column {
            AppBarWithTitle(
                backButton = true,
                onBackIconClicked = { navigator.pop() },
                title = name,
            )
            SeedInput(viewModel)
        }
    }
}

@Composable
private fun SeedInput(viewModel: SeedInputViewModel) {
    val navigator: CodeNavigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = FocusRequester()

    val notificationPermissionCheck = notificationPermissionCheck(isShowError = false) {  }

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
                    },
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