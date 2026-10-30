package com.flipcash.services.models.chat

import com.getcode.opencode.model.core.ID

/** The variant carried by a [MessageContent.Widget]. */
sealed interface WidgetContent {
    /** Invites the viewer to open [username]'s profile. */
    data class ShareProfile(val username: String) : WidgetContent

    /**
     * Points at a payment request by id. The request itself lives in the payment request store;
     * the card checks it belongs to this chat and was created by the sender before rendering.
     */
    data class PaymentRequest(val id: ID) : WidgetContent

    /** A `WidgetContent.type` this client does not recognise. */
    data object Unsupported : WidgetContent
}
