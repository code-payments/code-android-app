package com.flipcash.app.messenger.internal.screens.cash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.core.ui.TokenSelectionPill
import com.flipcash.app.messenger.internal.sendback.SendBackFlow
import com.flipcash.features.messenger.R
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.SlideToConfirm

/**
 * The Send Back confirm sheet over the chat, held in-screen like the fee sheet. The sheet stays
 * composed until it has slid away, so a dismissal from the flow (the swipe, or a balance that
 * dropped meanwhile) animates out like a drag does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendBackSheetHost(
    sheet: SendBackFlow.Sheet?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var shown by remember { mutableStateOf<SendBackFlow.Sheet?>(null) }
    LaunchedEffect(sheet) {
        if (sheet != null) {
            shown = sheet
        } else if (shown != null) {
            sheetState.hide()
            shown = null
        }
    }
    val current = shown ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CodeTheme.colors.background,
    ) {
        SendBackSheet(
            amount = current.amount,
            token = current.token,
            recipientName = current.recipientName,
            onConfirm = onConfirm,
        )
    }
}

/**
 * Returning a DM's opening payment: the original's amount, its currency read-only (the contract
 * fixes currency and mint), who it goes back to, and the swipe. [ChatInitPaymentSheet]'s layout
 * with the picker taken out.
 */
@Composable
internal fun SendBackSheet(
    amount: Fiat,
    token: Token?,
    recipientName: String?,
    onConfirm: () -> Unit,
) {
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
                text = amount.formatted(),
                style = CodeTheme.typography.displayLarge,
                color = CodeTheme.colors.textMain,
            )

            TokenSelectionPill(
                token = token,
                background = White10,
                textStyle = CodeTheme.typography.textMedium,
                imageSize = CodeTheme.dimens.staticGrid.x3,
                contentPadding = PaddingValues(
                    horizontal = CodeTheme.dimens.grid.x2,
                    vertical = CodeTheme.dimens.grid.x1,
                ),
                showsPicker = false,
                onClick = {},
            )

            Text(
                text = stringResource(
                    R.string.label_sendBackTo,
                    recipientName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.title_unnamedUser),
                ),
                style = CodeTheme.typography.caption,
                color = CodeTheme.colors.textSecondary,
            )
        }

        SlideToConfirm(
            onConfirm = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.action_swipeToSendBack),
        )
    }
}
