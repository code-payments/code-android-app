package com.flipcash.app.messenger.internal.request

import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
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
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.shared.chat.ui.request.PaymentRequestCards
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

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
    val collections = LocalNavBarShell.current == NavBarShell.GroupMoney
    return remember(chatId, held, missing, now, collections) {
        PaymentRequestCards(
            chatId = chatId,
            viewerId = viewModel.viewerId,
            requests = held,
            missing = missing,
            now = now,
            onNeeded = viewModel::fetchIfAbsent,
            onOpen = { open(chatId, it) },
            collections = collections,
        )
    }
}

/** What the request deck under a group chat's title draws: [cards] front first, at [now]. */
internal class RequestDeck(
    val cards: List<PaymentRequest>,
    val now: Instant,
    val expanded: Boolean,
)

/**
 * The deck for [chatId], pointed at the chat before it is read so requests already held are there
 * on the first frame. Null with Group Money on, where a collection is reached only from its bubble:
 * the chat then attaches and collects nothing, and nothing draws under its title.
 */
@Composable
internal fun rememberRequestDeck(viewModel: ChatRequestsViewModel, chatId: ChatId?): RequestDeck? {
    if (LocalNavBarShell.current == NavBarShell.GroupMoney) return null
    remember(chatId) { chatId?.also(viewModel::attach) }
    val deck by viewModel.deck.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val cards = remember(deck) { deck.filter { it.deckTitle != null } }
    return RequestDeck(cards = cards, now = now, expanded = expanded)
}
