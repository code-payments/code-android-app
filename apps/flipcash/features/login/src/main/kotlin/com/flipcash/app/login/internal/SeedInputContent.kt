package com.flipcash.app.login.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsBackupRestore
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.AppRoute
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.login.seed.SeedInputUiModel
import com.flipcash.app.login.seed.SeedInputViewModel
import com.flipcash.features.login.R
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.inputColors
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.scaled
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.keyboardAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SeedInputContent(viewModel: SeedInputViewModel) {
    val navigator: CodeNavigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()
    SeedInputContent(
        state = dataState,
        onTextChange = { viewModel.onTextChange(it) },
        onLogin = { viewModel.onSubmit(navigator) },
        onRestore = { viewModel.restoreAccount(navigator) },
        onCantFind = { navigator.push(ScreenRegistry.get(AppRoute.Onboarding.AccessKeySavedLocation)) }
    )
}

@Composable
private fun SeedInputContent(
    state: SeedInputUiModel,
    onTextChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRestore: suspend () -> Unit,
    onCantFind: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val featureFlags = LocalFeatureFlags.current
    val restoreEnabled by featureFlags.observe(FeatureFlag.CredentialManager).collectAsState()

    val keyboardVisible by keyboardAsState()
    val ime = LocalSoftwareKeyboardController.current
    val composeScope = rememberCoroutineScope()
    val animationScale by rememberAnimationScale()

    CodeScaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        floatingActionButton = {
            if (restoreEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x4),
                ) {
                    Text(
                        modifier = Modifier,
                        text = stringResource(R.string.action_recoverExistingAccount),
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textSecondary
                    )

                    FloatingActionButton(
                        backgroundColor = CodeTheme.colors.brandMuted,
                        contentColor = CodeTheme.colors.textMain,
                        shape = CircleShape,
                        onClick = {
                            composeScope.launch {
                                if (keyboardVisible) {
                                    ime?.hide()
                                    delay(500.scaled(animationScale))
                                }
                                onRestore()
                            }
                        }
                    ) {
                        Image(
                            imageVector = Icons.Default.SettingsBackupRestore,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(CodeTheme.colors.textMain),
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(bottom = CodeTheme.dimens.grid.x4),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3)
        ) {
            Box {
                OutlinedTextField(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x3)
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
                            bottom = CodeTheme.dimens.grid.x1,
                            start = CodeTheme.dimens.grid.x2
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
                            painter = painterResource(id = R.drawable.ic_checked),
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
                    .padding(bottom = CodeTheme.dimens.grid.x4),
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

            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        composeScope.launch {
                            if (keyboardVisible) {
                                ime?.hide()
                                delay(500.scaled(animationScale))
                            }
                            onCantFind()
                        }
                    },
                text = stringResource(R.string.title_cantFindYourAccessKey),
                style = CodeTheme.typography.textMedium,
                color = CodeTheme.colors.textSecondary
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}