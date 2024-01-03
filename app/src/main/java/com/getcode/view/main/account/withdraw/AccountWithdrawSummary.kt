package com.getcode.view.main.account.withdraw

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.getcode.App
import com.getcode.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.WithdrawalArgs
import com.getcode.theme.Brand01
import com.getcode.theme.BrandLight
import com.getcode.view.components.ButtonState
import com.getcode.view.components.CodeButton
import com.getcode.view.main.giveKin.AmountArea

@Composable
fun AccountWithdrawSummary(
    viewModel: AccountWithdrawSummaryViewModel,
    arguments: WithdrawalArgs,
) {
    val navigator = LocalCodeNavigator.current
    val dataState by viewModel.uiFlow.collectAsState()

    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 20.dp)
            .imePadding()
    ) {
        val (centerColumn, nextButton) = createRefs()

        Column(
            modifier = Modifier
                .wrapContentHeight()
                .constrainAs(centerColumn) {
                    top.linkTo(parent.top)
                    bottom.linkTo(nextButton.top)
                }
        ) {
            Box(
                modifier = Modifier
                    .border(width = 1.dp, color = BrandLight, shape = RoundedCornerShape(10.dp))
                    .background(Brand01)
                    .padding(20.dp)
            ) {
                AmountArea(
                    currencyResId = dataState.currencyResId,
                    amountText = dataState.amountText,
                    captionText = String.format("%,.0f", dataState.amountKin?.toKin()?.toDouble()),
                    isAltCaption = true,
                    isAltCaptionKinIcon = true,
                    altCaptionColor = BrandLight
                )
            }

            Image(
                modifier = Modifier
                    .padding(vertical = 20.dp)
                    .align(CenterHorizontally),
                painter = painterResource(id = R.drawable.ic_arrow_down),
                contentDescription = ""
            )

            Box(
                modifier = Modifier
                    .border(width = 1.dp, color = BrandLight, shape = RoundedCornerShape(10.dp))
                    .background(Brand01)
                    .padding(20.dp)
            ) {
                Text(
                    text = dataState.resolvedDestination,
                    style = MaterialTheme.typography.subtitle1.copy(textAlign = TextAlign.Center)
                )
            }
        }

        CodeButton(
            modifier = Modifier
                .padding(bottom = 20.dp)
                .constrainAs(nextButton) {
                    bottom.linkTo(parent.bottom)
                },
            onClick = {
                viewModel.onSubmit(navigator, arguments)
            },
            enabled = true,
            text = stringResource(R.string.action_withdrawKin),
            buttonState = ButtonState.Filled,
        )
    }

    LaunchedEffect(rememberUpdatedState(Unit)) {
        viewModel.setArguments(navigator, arguments)
    }

    LaunchedEffect(dataState.isSuccess) {
        if (dataState.isSuccess == true) {
            navigator.hide()
        }
    }
}