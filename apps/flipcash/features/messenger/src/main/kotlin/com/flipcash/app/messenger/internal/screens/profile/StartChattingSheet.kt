package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.flipcash.app.messenger.internal.screens.cash.ChatInitPaymentSheet
import com.getcode.theme.CodeTheme

/**
 * The fee confirmation, held by the profile itself rather than pushed as a route.
 *
 * In-screen on purpose: once the payment lands the profile is still the top entry, so opening the
 * DM is a push (or a pop back to the chat) from where the person is standing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartChattingSheet(
    state: ChatProfileViewModel.State,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.paymentSheetVisible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CodeTheme.colors.background,
    ) {
        ChatInitPaymentSheet(
            fee = state.fee?.formatted(),
            token = state.token,
            sendProgress = state.sendProgress,
            onConfirm = onConfirm,
            onSendComplete = {},
        )
    }
}
