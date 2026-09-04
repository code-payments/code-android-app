package com.flipcash.app.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.transactions.internal.TransactionDetailsViewModel
import com.flipcash.shared.transactionhistory.TransactionDetailsContent
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.opencode.model.core.ID
import com.getcode.theme.CodeTheme

/**
 * One activity entry, opened from its row (Figma node 9708:105260).
 *
 * Lives here rather than beside [TransactionDetailsContent] in `:shared:transaction-history` because
 * of the cancel action: pulling a cash link back needs `TokenCoordinator` and `TransactionOperations`
 * from `:shared:tokens`, which that module can't depend on without closing a cycle (chat → tokens →
 * transaction-history). The drawing stays shared; only the wiring is here.
 */
@Composable
fun TransactionDetailsScreen(id: ID) {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<TransactionDetailsViewModel>()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, id) {
        viewModel.dispatchEvent(TransactionDetailsViewModel.Event.OnIdProvided(id))
    }

    val transaction = state.transaction
    if (transaction == null) {
        // Nothing to draw yet — a cached entry resolves on the first read, so this is a frame, not a
        // state worth an empty message.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CodeTheme.colors.background)
        )
        return
    }

    TransactionDetailsContent(
        details = transaction.details,
        onBack = { navigator.pop() },
        onCopyId = { viewModel.dispatchEvent(TransactionDetailsViewModel.Event.CopyId) },
        onViewInChat = {
            val userId = transaction.counterpartyId ?: return@TransactionDetailsContent
            val profile = transaction.counterparty ?: return@TransactionDetailsContent
            navigator.push(
                AppRoute.Messaging.Chat(ChatIdentifier.ByUser(userId = userId, profile = profile))
            )
        },
        onCancel = { viewModel.dispatchEvent(TransactionDetailsViewModel.Event.OnCancelRequested) },
    )
}
