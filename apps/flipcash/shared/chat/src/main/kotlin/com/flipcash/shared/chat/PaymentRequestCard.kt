package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.getcode.opencode.model.core.ID

/** What a payment request widget message draws as. */
sealed interface RequestCardState {
    /** The request isn't held yet, or its text hasn't opened yet: the card's frame with placeholders. */
    data object Loading : RequestCardState

    /** Drawn as the chat's unsupported-message bubble. */
    data object Unsupported : RequestCardState

    data class Ready(val request: PaymentRequest, val title: String) : RequestCardState
}

/**
 * The card for a widget naming [request], sent by [senderId] in [chatId]. [missing] is true once a
 * fetch for it came back DENIED or NOT_FOUND.
 *
 * A request from another chat, or one the sender didn't create, is unsupported: anyone can paste a
 * request id into a widget, and the card must not lend that request the chat's or sender's name.
 */
fun requestCardState(
    request: PaymentRequest?,
    missing: Boolean,
    chatId: ChatId,
    senderId: ID?,
): RequestCardState {
    if (missing) return RequestCardState.Unsupported
    request ?: return RequestCardState.Loading
    val kind = request.kind as? PaymentRequest.Kind.GroupSplit ?: return RequestCardState.Unsupported
    if (kind.chatId != chatId) return RequestCardState.Unsupported
    if (senderId == null || request.creator != senderId) return RequestCardState.Unsupported
    return when (val text = kind.split.text) {
        is PaymentRequestText.Plain -> RequestCardState.Ready(request, text.title)
        is PaymentRequestText.Encrypted ->
            if (text.scheme == PaymentRequestText.SCHEME_CHAT_KEY_XCHACHA20POLY1305) RequestCardState.Loading
            else RequestCardState.Unsupported
        PaymentRequestText.Undecryptable -> RequestCardState.Unsupported
    }
}
