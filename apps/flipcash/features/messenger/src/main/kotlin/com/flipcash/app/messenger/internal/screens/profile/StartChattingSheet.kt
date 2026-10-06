package com.flipcash.app.messenger.internal.screens.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The sheet stays composed until it has slid away, so a dismissal from the view model (a
    // payment that landed) animates out like a swipe does rather than vanishing.
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(state.paymentSheetVisible) {
        if (state.paymentSheetVisible) {
            composed = true
        } else if (composed) {
            sheetState.hide()
            composed = false
        }
    }
    if (!composed) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
