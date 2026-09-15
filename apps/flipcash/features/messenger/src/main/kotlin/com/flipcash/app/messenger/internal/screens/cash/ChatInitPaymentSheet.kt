package com.flipcash.app.messenger.internal.screens.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.tokens.TokenPurpose
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.features.messenger.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.SlideToConfirm
import com.getcode.view.LoadingSuccessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The payment that opens a tip DM. The recipient sets a fee to be messaged, so the amount is
 * already decided — the sheet states it and asks for the swipe, with no keypad to enter an amount
 * the flow would only have to reject.
 *
 * The token stays selectable: the fee is a fiat price, and which balance pays it is still the
 * sender's choice.
 *
 * @param fee the fee, already formatted in the sender's preferred currency. Null while it resolves,
 * which the conversation gates on before opening this sheet.
 */
@Composable
internal fun ChatInitPaymentSheet(
    fee: String?,
    token: Token?,
    sendProgress: LoadingSuccessState,
    eventFlow: Flow<ChatViewModel.Event>,
    onConfirm: () -> Unit,
    onSendComplete: () -> Unit,
) {
    val navigator = LocalCodeNavigator.current

    LaunchedEffect(eventFlow) {
        eventFlow
            .filterIsInstance<ChatViewModel.Event.SendComplete>()
            .onEach { onSendComplete() }
            .launchIn(this)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x6, bottom = CodeTheme.dimens.grid.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Text(
                text = fee.orEmpty(),
                style = CodeTheme.typography.displayLarge,
                color = CodeTheme.colors.textMain,
            )

            TokenSelectionPill(
                token = token,
                background = White10,
                textStyle = CodeTheme.typography.textMedium,
                imageSize = CodeTheme.dimens.staticGrid.x3,
                contentPadding = PaddingValues(
                    horizontal = CodeTheme.dimens.grid.x1 + 1.dp,
                    vertical = CodeTheme.dimens.grid.x1 - 1.dp,
                ),
                onClick = {
                    navigator.push(AppRoute.Sheets.TokenSelection(TokenPurpose.Select))
                },
            )
        }

        SlideToConfirm(
            onConfirm = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            // The fee is fixed, so the only thing that can gate the swipe is a send already
            // running — there is no amount for the user to get wrong.
            enabled = fee != null && sendProgress.isIdle,
            isLoading = sendProgress.loading,
            isSuccess = sendProgress.success,
            label = stringResource(R.string.action_swipeToSend),
        )
    }
}
