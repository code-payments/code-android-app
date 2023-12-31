package com.getcode.view.login

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.getcode.R
import com.getcode.theme.*
import com.getcode.util.PhoneUtils
import com.getcode.util.getActivity
import com.getcode.view.ARG_IS_NEW_ACCOUNT
import com.getcode.view.ARG_IS_PHONE_LINKING
import com.getcode.view.ARG_SIGN_IN_ENTROPY_B64
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.components.SheetTitle
import com.getcode.view.main.home.ModalSheetLayout
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.Credentials
import com.google.android.gms.auth.api.credentials.HintRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Preview
@Composable
fun PhoneVerify(
    navController: NavController? = null,
    arguments: Bundle? = null
) {
    val viewModel = hiltViewModel<PhoneVerifyViewModel>()
    val dataState: PhoneVerifyUiModel by viewModel.uiFlow.collectAsState()
    val focusManager = LocalFocusManager.current
    val focusRequester = FocusRequester()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isPhoneRequestCompleted by remember { mutableStateOf(false) }
    val animationSpec = remember {
        Animatable(0f)
            .run {
                TweenSpec<Float>(durationMillis = 400, easing = LinearEasing)
            }
    }
    val bottomSheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true,
        animationSpec = animationSpec)


    fun showAreaCodes() {
        scope.launch {
            focusManager.clearFocus()
            bottomSheetState.show()
        }
    }

    fun hideAreaCodes() {
        scope.launch {
            bottomSheetState.hide()
        }
    }

    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 50.dp, bottom = 20.dp)
            .padding(horizontal = 20.dp)
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
                .height(60.dp)
                .border(width = 1.dp, color = BrandLight, shape = RoundedCornerShape(5.dp))
                .background(White05)
        ) {
            Row(
                modifier = Modifier
                    .height(60.dp)
                    .clip(RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    .clickable { showAreaCodes() }
            ) {
                dataState.countryLocale.resId?.let { resId ->
                    Image(
                        modifier = Modifier
                            .align(CenterVertically)
                            .padding(start = 15.dp)
                            .size(25.dp)
                            .clip(RoundedCornerShape(15.dp)),
                        painter = painterResource(resId),
                        contentDescription = "",
                    )
                }
                Text(
                    modifier = Modifier
                        .height(60.dp)
                        .align(CenterVertically)
                        .padding(horizontal = 15.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.subtitle1,
                    text = "+${dataState.countryLocale.phoneCode}"
                )
            }
            Spacer(
                modifier = Modifier
                    .background(BrandLight)
                    .width(1.dp)
                    .height(60.dp)
            )
            TextField(
                modifier = Modifier
                    .wrapContentWidth()
                    .focusRequester(focusRequester)
                    .padding(top = 2.dp),
                value = dataState.phoneNumberFormattedTextFieldValue,
                textStyle = MaterialTheme.typography.subtitle1,
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
                    onDone = { viewModel.onSubmit(navController, context.getActivity()) }
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

        Text(
            modifier = Modifier.constrainAs(captionText) {
                linkTo(countryCodeRow.bottom, captionText.top, topMargin = 20.dp)
            },
            style = MaterialTheme.typography.body2.copy(
                textAlign = TextAlign.Center
            ),
            color = BrandLight,
            text = stringResource(R.string.subtitle_phoneVerificationDescription)
        )

        CodeButton(
            modifier = Modifier
                .constrainAs(buttonAction) {
                    bottom.linkTo(parent.bottom)
                },
            onClick = {
                viewModel.onSubmit(navController, context.getActivity())
            },
            enabled = dataState.continueEnabled,
            isLoading = dataState.isLoading,
            isSuccess = dataState.isSuccess,
            text = stringResource(R.string.action_next),
            buttonState = ButtonState.Filled,
        )
    }

    ModalSheetLayout(
        bottomSheetState
    ) {
        PhoneAreaSelect(
            dataState = dataState,
            onUpdateSearchFilter = {
                viewModel.onUpdateSearchFilter(it)
            },
            onClick = {
                hideAreaCodes()
                viewModel.setCountryCode(it)
            }, onClose = {
                hideAreaCodes()
            }
        )
    }

    LaunchedEffect(bottomSheetState.isVisible) {
        if (!bottomSheetState.isVisible && isPhoneRequestCompleted) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(rememberUpdatedState(Unit)) {
        arguments?.getString(ARG_SIGN_IN_ENTROPY_B64)
            ?.let { viewModel.setSignInEntropy(it) }
        arguments?.getBoolean(ARG_IS_PHONE_LINKING)
            ?.let { viewModel.setIsPhoneLinking(it) }
        arguments?.getBoolean(ARG_IS_NEW_ACCOUNT)
            ?.let { viewModel.setIsNewAccount(it) }
    }

    val phoneNumberHintLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) {
        isPhoneRequestCompleted = true
        focusRequester.requestFocus()
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }

        val credential: Credential? = it.data?.getParcelableExtra(Credential.EXTRA_KEY)
        val hintResult = credential?.id

        if (hintResult?.isNotEmpty() == true) {
            viewModel.setPhoneInput(hintResult)
        }
    }

    LaunchedEffect(Unit) {
        val hintRequest: HintRequest = HintRequest.Builder()
            .setPhoneNumberIdentifierSupported(true)
            .build()

        val phoneNumberHintIntent = Credentials.getClient(context)
            .getHintPickerIntent(hintRequest)

        try {
            phoneNumberHintLauncher.launch(
                IntentSenderRequest.Builder(phoneNumberHintIntent)
                    .build()
            )
        } catch (e: ActivityNotFoundException) {
        }
    }
}

@Composable
private fun PhoneAreaSelect(
    dataState: PhoneVerifyUiModel,
    onUpdateSearchFilter: (filter: String) -> Unit,
    onClick: (countryLocale: PhoneUtils.CountryLocale) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(sheetHeight)
    ) {
        SheetTitle(title = stringResource(R.string.title_selectCountry), closeButton = true, onCloseIconClicked = onClose)

       /* TextField(
            placeholder = { Text("Search") },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 15.dp)
                .padding(horizontal = 10.dp),
            value = dataState.countrySearchFilterString,
            onValueChange = {
                onUpdateSearchFilter(it)
            },
            singleLine = true,
            colors = inputColors()
        ) */

        LazyColumn {
            items(dataState.countryLocalesFiltered) { countryCode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick(countryCode) },
                ) {
                    countryCode.resId?.let { resId ->
                        Image(
                            modifier = Modifier
                                .align(CenterVertically)
                                .padding(start = 20.dp)
                                .size(25.dp)
                                .clip(RoundedCornerShape(15.dp)),
                            painter = painterResource(id = resId),
                            contentDescription = ""
                        )
                    }
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 20.dp)
                            .padding(start = 20.dp)
                            .align(CenterVertically),
                        text = countryCode.name,
                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        modifier = Modifier
                            .padding(20.dp)
                            .align(CenterVertically),
                        color = BrandLight,
                        text = "+${countryCode.phoneCode}",
                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Divider(
                    color = White05,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                )
            }
        }
    }
}