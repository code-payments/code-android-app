package com.flipcash.shared.chat.ui.request

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.shared.chat.RequestCardState
import com.flipcash.shared.chat.requestCardState
import com.getcode.opencode.model.core.ID
import kotlin.time.Instant

/**
 * What a transcript needs to draw payment request cards: the chat they sit in, the held requests,
 * and the one-minute clock. Provided by the conversation screen; null elsewhere, where a request
 * widget draws as unsupported.
 */
class PaymentRequestCards(
    val chatId: ChatId,
    val viewerId: ID?,
    val requests: Map<ID, PaymentRequest>,
    val missing: Set<ID>,
    val now: Instant,
    /** Fetches a request the store doesn't hold; safe to call on every composition. */
    val onNeeded: (ID) -> Unit,
    val onOpen: (ID) -> Unit,
) {
    fun stateOf(id: ID, isFromSelf: Boolean, senderId: ID?): RequestCardState = requestCardState(
        request = requests[id],
        missing = id in missing,
        chatId = chatId,
        senderId = if (isFromSelf) viewerId else senderId,
    )
}

val LocalPaymentRequestCards = compositionLocalOf<PaymentRequestCards?> { null }

/** Node 12254:1929: the card at its transcript width. */
internal object PaymentRequestBubbleDefaults {
    val WIDTH: Dp = 264.dp
    val PADDING: Dp = 14.dp
}

/** Fetches [id] when the card comes on screen without it, which covers a widget scrolled into view. */
@Composable
internal fun FetchWhenAbsent(id: ID, cards: PaymentRequestCards) {
    val absent = id !in cards.requests && id !in cards.missing
    LaunchedEffect(id, absent) { if (absent) cards.onNeeded(id) }
}

@Composable
internal fun PaymentRequestCardBody(state: RequestCardState, now: Instant) {
    when (state) {
        is RequestCardState.Ready -> PaymentRequestCardContent(state.request, state.title, now, Modifier.fillMaxWidth())
        else -> PaymentRequestCardContent(null, null, now, Modifier.fillMaxWidth())
    }
}
