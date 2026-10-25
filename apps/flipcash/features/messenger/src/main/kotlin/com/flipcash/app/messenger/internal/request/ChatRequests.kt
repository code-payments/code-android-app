package com.flipcash.app.messenger.internal.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.request.PaymentRequestCards
import com.getcode.opencode.model.core.ID

/**
 * The transcript's payment request cards for a group chat, and the poll that keeps them current
 * while the chat is on screen with the app in the foreground. Null [chatId] (a DM, or a chat still
 * resolving) polls nothing and draws request widgets as unsupported.
 */
@Composable
internal fun rememberPaymentRequestCards(
    viewModel: ChatRequestsViewModel,
    chatId: ChatId?,
    onOpen: (ChatId, ID) -> Unit,
): PaymentRequestCards? {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(chatId, lifecycleOwner) {
        chatId ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.poll(chatId) }
    }
    chatId ?: return null
    val held by viewModel.held.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpen)
    return remember(chatId, held, missing, now) {
        PaymentRequestCards(
            chatId = chatId,
            viewerId = viewModel.viewerId,
            requests = held,
            missing = missing,
            now = now,
            openedAt = viewModel.openedAt,
            onNeeded = viewModel::fetchIfAbsent,
            onRefresh = viewModel::refresh,
            onOpen = { open(chatId, it) },
        )
    }
}
