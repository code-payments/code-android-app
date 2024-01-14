package com.getcode.view.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = CodeTheme.dimens.grid.x10, bottom = CodeTheme.dimens.grid.x4)
            .padding(horizontal = CodeTheme.dimens.inset)
            .imePadding()
    ) {
        val (countryCodeRow, captionText, buttonAction) = createRefs()
        Row(
            modifier = Modifier
                .constrainAs(countryCodeRow) {
                    linkTo(
                        start = parent.start,
                        top = parent.top,
                        end = parent.end,
                        bottom = parent.bottom,
                        verticalBias = 0.4f
                    )
                }
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
                    .rememberedClickable { openCountrySelector() }
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
            TextField(
                modifier = Modifier
                    .wrapContentWidth()
                    .focusRequester(focusRequester)
                    .padding(top = CodeTheme.dimens.grid.x1),
                value = dataState.phoneNumberFormattedTextFieldValue,
                textStyle = CodeTheme.typography.subtitle1,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone),
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                ),
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
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.title_phoneNumber),
                        color = White50,
                        fontSize = 20.sp
                    )
                }
            )

        }

        val inset = CodeTheme.dimens.inset
        Text(
            modifier = Modifier.constrainAs(captionText) {
                linkTo(captionText.start, parent.start)
                linkTo(countryCodeRow.bottom, captionText.top, topMargin = inset)
                linkTo(captionText.end, parent.end)
            },
            style = CodeTheme.typography.body2,
            textAlign = TextAlign.Center,
            color = BrandLight,
            text = stringResource(R.string.subtitle_phoneVerificationDescription)
        )

        CodeButton(
            modifier = Modifier
                .constrainAs(buttonAction) {
                    bottom.linkTo(parent.bottom)
                },
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