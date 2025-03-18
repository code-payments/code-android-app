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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.LoginArgs
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.theme.White50
import com.getcode.theme.extraSmall
import com.getcode.util.PhoneUtils
import com.getcode.ui.utils.getActivity
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = CodeTheme.dimens.grid.x4)
            .padding(horizontal = CodeTheme.dimens.inset)
            .imePadding()
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Center) {
            Column(
                modifier = Modifier.align(Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
            ) {
                PhoneEntry(
                    modifier = Modifier
                        .height(CodeTheme.dimens.grid.x12),
                    focusManager = focusManager,
                    focusRequester = focusRequester,
                    locale = dataState.countryLocale,
                    isLoading = dataState.isLoading,
                    isSuccess = dataState.isSuccess,
                    value = dataState.phoneNumberFormattedTextFieldValue,
                    onValueChanged = {
                        viewModel.setPhoneInput(it.text, it.selection)
                    },
                    openCountrySelector = openCountrySelector,
                    onSubmit = { viewModel.onSubmit(navigator, context.getActivity()) }
                )

                Text(
                    modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x2),
                    style = CodeTheme.typography.textSmall,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                    text = stringResource(R.string.subtitle_phoneVerificationDescription)
                )
            }
        }


        CodeButton(
            modifier = Modifier.fillMaxWidth(),
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
            viewModel.dismissedHint()
            return@rememberLauncherForActivityResult
        }

        val phoneNum = Identity.getSignInClient(context)
            .getPhoneNumberFromIntent(it.data)

        viewModel.setPhoneFromHint(phoneNum)
    }

    LaunchedEffect(dataState.hasDismissedHint) {
        val request = GetPhoneNumberHintIntentRequest
            .builder()
            .build()

        val isSettled = navigator.lastItem == navigator.lastModalItem || arguments.isNewAccount
        if (isSettled && dataState.phoneNumberFormatted.isEmpty() && !dataState.hasDismissedHint) {
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

@Composable
private fun PhoneEntry(
    modifier: Modifier = Modifier,
    focusManager: FocusManager,
    focusRequester: FocusRequester,
    locale: PhoneUtils.CountryLocale,
    isLoading: Boolean,
    isSuccess: Boolean,
    value: TextFieldValue,
    onValueChanged: (TextFieldValue) -> Unit,
    openCountrySelector: () -> Unit,
    onSubmit: () -> Unit,
) {
    val composeScope = rememberCoroutineScope()
    Row(
        modifier = modifier
            .border(
                width = CodeTheme.dimens.border,
                color = CodeTheme.colors.brandLight,
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
                },
        ) {
            locale.resId?.let { resId ->
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
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = CodeTheme.dimens.border)
                    .padding(horizontal = CodeTheme.dimens.grid.x3,),
                contentAlignment = Center
            ) {
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.border),
                    style = CodeTheme.typography.textLarge,
                    text = "+${locale.phoneCode}"
                )
            }
        }
        Spacer(
            modifier = Modifier
                .background(CodeTheme.colors.brandLight)
                .width(1.dp)
                .fillMaxHeight()
        )
        BasicTextField(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .focusRequester(focusRequester)
                .padding(top = CodeTheme.dimens.thickBorder),
            value = value,
            textStyle = CodeTheme.typography.textLarge.copy(color = CodeTheme.colors.onBackground),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone),
            cursorBrush = SolidColor(Color.White),
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            onValueChange = {
                if (!isLoading) {
                    onValueChanged(it)
                }
            },
            enabled = !isLoading && !isSuccess,
        ) { textField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = CodeTheme.dimens.staticGrid.x2),
                contentAlignment = CenterStart
            ) {
                if (value.text.isEmpty()) {
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
}