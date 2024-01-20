package com.getcode.view.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.theme.extraSmall
import com.getcode.util.getActivity
import com.getcode.util.rememberedClickable
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@Preview
@Composable
internal fun PhoneVerify(
    viewModel: PhoneVerifyViewModel = hiltViewModel(),
    arguments: LoginArgs = LoginArgs(),
    openCountrySelector: () -> Unit = { },
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val composeScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = CodeTheme.dimens.grid.x4)
            .padding(horizontal = CodeTheme.dimens.inset)
            .imePadding()
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.align(Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                Row(
                    modifier = Modifier
                        .height(CodeTheme.dimens.grid.x12)
                        .border(
                            width = CodeTheme.dimens.border,
                            color = BrandLight,
                            shape = CodeTheme.shapes.extraSmall
                        )
                        .background(White05)
                ) {
                    Row(
                        modifier = Modifier
                            .height(CodeTheme.dimens.grid.x12)
                            .clip(
                                CodeTheme.shapes.extraSmall
                                    .copy(bottomEnd = ZeroCornerSize, topEnd = ZeroCornerSize)
                            )
                            .rememberedClickable {
                                composeScope.launch {
                                    focusManager.clearFocus(true)
                                    delay(500)
                                    openCountrySelector()
                                }
                            }
                    ) {
                        dataState.countryLocale.resId?.let { resId ->
                            Image(
                                modifier = Modifier
                                    .align(CenterVertically)
                                    .padding(start = CodeTheme.dimens.grid.x3)
                                    .size(CodeTheme.dimens.staticGrid.x5)
                                    .clip(CodeTheme.shapes.large),
                                painter = painterResource(resId),
                                contentDescription = "",
                            )
                        }
                        Text(
                            modifier = Modifier
                                .height(CodeTheme.dimens.grid.x12)
                                .align(CenterVertically)
                                .padding(
                                    horizontal = CodeTheme.dimens.grid.x3,
                                    vertical = CodeTheme.dimens.grid.x4
                                ),
                            style = CodeTheme.typography.subtitle1,
                            text = "+${dataState.countryLocale.phoneCode}"
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .background(BrandLight)
                            .width(1.dp)
                            .height(CodeTheme.dimens.grid.x12)
                    )
                    BasicTextField(
                        modifier = Modifier
                            .wrapContentWidth()
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .padding(top = CodeTheme.dimens.grid.x1),
                        value = dataState.phoneNumberFormattedTextFieldValue,
                        textStyle = CodeTheme.typography.subtitle1.copy(color = CodeTheme.colors.onBackground),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        keyboardActions = KeyboardActions(
                            onDone = {
                                viewModel.onSubmit(navigator, context.getActivity())
                            }
                        ),
                        onValueChange = {
                            if (!dataState.isLoading) {
                                viewModel.setPhoneInput(it.text, it.selection)
                            }
                        },
                        enabled = !dataState.isLoading && !dataState.isSuccess,
                    ) { textField ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = CodeTheme.dimens.staticGrid.x2),
                            contentAlignment = CenterStart
                        ) {
                            if (dataState.phoneNumberFormattedTextFieldValue.text.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.title_phoneNumber),
                                    color = White50,
                                    fontSize = 20.sp
                                )
                            }
                            textField()
                        }
                    }

                }

                Text(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x2),
                    style = CodeTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    color = BrandLight,
                    text = stringResource(R.string.subtitle_phoneVerificationDescription)
                )
            }
        }


        CodeButton(
            onClick = {
                viewModel.onSubmit(navigator, context.getActivity())
            },
            enabled = dataState.continueEnabled,
            isLoading = dataState.isLoading,
            isSuccess = dataState.isSuccess,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }

    LaunchedEffect(arguments) {
        arguments.signInEntropy
            ?.let { viewModel.setSignInEntropy(it) }
        viewModel.setIsPhoneLinking(arguments.isPhoneLinking)
        viewModel.setIsNewAccount(arguments.isNewAccount)
    }

    val phoneNumberHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        if (it.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }

        val phoneNum = Identity.getSignInClient(context)
            .getPhoneNumberFromIntent(it.data)

        viewModel.setPhoneFromHint(phoneNum)
    }

    LaunchedEffect(phoneNumberHintLauncher) {
        val request = GetPhoneNumberHintIntentRequest
            .builder()
            .build()

        val isSettled = navigator.lastItem == navigator.lastModalItem || arguments.isNewAccount
        if (isSettled && dataState.phoneNumberFormatted.isEmpty()) {
            Identity.getSignInClient(context)
                .getPhoneNumberHintIntent(request)
                .addOnSuccessListener {
                    phoneNumberHintLauncher.launch(
                        IntentSenderRequest.Builder(it.intentSender).build()
                    )
                }.addOnFailureListener {
                    focusRequester.requestFocus()
                }
        }
    }
}