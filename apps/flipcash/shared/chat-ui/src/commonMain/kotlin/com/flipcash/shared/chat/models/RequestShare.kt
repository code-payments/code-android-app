package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent

/**
 * Whether this content is a cash message that pays a payment request share. The request's card and
 * Request details already show that payment, so the transcript draws no bubble for it. The message
 * stays stored and paged; only the mapping to rows skips it.
 *
 * The chat list's preview and unread queries apply the same test in SQL (`ChatMessageDao`), so a
 * change here has to change them too.
 */
val MessageContent.paysRequestShare: Boolean
    get() = this is MessageContent.Cash && paymentRequestId != null
