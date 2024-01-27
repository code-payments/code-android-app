package com.getcode.view.main.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.App
import com.getcode.R
import com.getcode.manager.BottomBarManager
import com.getcode.manager.SessionManager
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.PhoneVerificationScreen
import com.getcode.network.repository.urlEncode
import com.getcode.theme.Brand
import com.getcode.theme.CodeTheme
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton

@Composable
fun AccountPhone(
    viewModel: AccountPhoneViewModel,
) {
    val context = LocalContext.current
    val navigator = LocalCodeNavigator.current
    val dataState: AccountPhoneUiModel by viewModel.uiFlow.collectAsState()

    ConstraintLayout(
        modifier = Modifier
            .background(Brand)
            .fillMaxSize()
            .padding(horizontal = CodeTheme.dimens.inset)
    ) {
        val (image, title, text, button) = createRefs()
        Image(
            modifier = Modifier
                .padding(bottom = CodeTheme.dimens.staticGrid.x6)
                .constrainAs(image) {
                    bottom.linkTo(title.top, 0.dp)
                }
                .size(CodeTheme.dimens.staticGrid.x17),
            painter = painterResource(id = if (dataState.isLinked) R.drawable.ic_phone_filled else R.drawable.ic_phone_empty),
            contentDescription = ""
        )
        Text(
            modifier = Modifier
                .padding(bottom = CodeTheme.dimens.grid.x6)
                .constrainAs(title) {
                    centerVerticallyTo(parent, 0.4f)
                },
            text = if (!dataState.isLinked) stringResource(R.string.subtitle_noLinkedPhoneNumber) else dataState.phoneNumberFormatted.orEmpty(),
            style = CodeTheme.typography.h1
        )
        Text(
            modifier = Modifier
                .constrainAs(text) {
                    top.linkTo(title.bottom)
                },
            text = if (!dataState.isLinked) stringResource(R.string.subtitle_noLinkedPhoneNumberDescription)
            else stringResource(R.string.subtitle_linkedPhoneNumberDescription),
            style = CodeTheme.typography.body1
        )

        CodeButton(
            onClick = {
                if (!dataState.isLinked) {
                    val entropyB64 = SessionManager.authState.value?.entropyB64
                    if (!entropyB64.isNullOrBlank()) {
                        navigator.push(PhoneVerificationScreen(signInEntropy = entropyB64.urlEncode(), isPhoneLinking = true))
                    }
                } else {
                    BottomBarManager.showMessage(
                        BottomBarManager.BottomBarMessage(
                            title = context.getString(R.string.prompt_title_unlinkPhoneNumber),
                            subtitle = context.getString(R.string.prompt_description_unlinkPhoneNumber),
                            positiveText = "Yes",
                            negativeText = context.getString(R.string.action_cancel),
                            onPositive = viewModel::unlinkPhone,
                            onNegative = {}
                        )
                    )
                }
            },
            text = if (!dataState.isLinked) stringResource(R.string.action_linkPhoneNumber) else stringResource(R.string.action_removeYourPhoneNumber),
            buttonState = if (!dataState.isLinked) ButtonState.Filled else ButtonState.Filled10,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CodeTheme.dimens.inset)
                .constrainAs(button) {
                    bottom.linkTo(parent.bottom)
                }
        )
    }

    SideEffect {
        viewModel.init()
    }
}