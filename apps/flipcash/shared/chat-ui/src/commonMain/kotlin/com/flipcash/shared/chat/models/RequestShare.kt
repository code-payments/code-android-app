package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent

/**
 * Whether this content is a cash message that pays a payment request share. The transcript draws it
 * as a share payment row (`ChatListItem.SharePayment`), not a cash bubble.
 *
 * The chat list's unread queries apply the same test in SQL (`ChatMessageDao`), so a change here
 * has to change them too.
 */
val MessageContent.paysRequestShare: Boolean
    get() = this is MessageContent.Cash && paymentRequestId != null
