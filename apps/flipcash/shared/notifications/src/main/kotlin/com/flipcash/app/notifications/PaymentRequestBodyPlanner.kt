package com.flipcash.app.notifications

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.paymentrequest.PaymentRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat

/**
 * The wording of a payment-request push, each a format string taking the amount and/or title as
 * `%1$s` / `%2$s`. Kept apart from [planPaymentRequestBody] so the rule needs no `Context`.
 *
 * The sender is not named: `MessagingStyle` draws it above the line in a group and in the title of
 * a DM, as it does for [planMessageBody].
 */
data class PaymentRequestBodyTemplates(
    /** Takes the amount, then the title. */
    val amountAndTitle: String,
    /** Takes the amount. */
    val amountOnly: String,
    /** Takes the title. */
    val titleOnly: String,
)

/**
 * The body for a push whose message is a payment-request widget, from the request it points at.
 *
 * The server's body for a widget is generic, and in a private group a stand-in because the request's
 * text is sealed. The fetched request carries the opened [PaymentRequestText.Plain] title and the
 * amounts, so the notification can say what was asked.
 *
 * The amount is the viewer's own share when they owe one, since that is what the push asks of them,
 * and otherwise the whole split. A blank or unopened title is left out.
 *
 * `null` when there is nothing worth saying (no request, one from another chat, or neither a title
 * nor an amount), in which case the caller keeps the push's own body.
 *
 * @param request the fetched request, null when the fetch failed or timed out
 * @param chatId the chat the push is for; a request made elsewhere is not shown here
 * @param viewerId the signed-in user, to pick their share
 */
fun planPaymentRequestBody(
    request: PaymentRequest?,
    chatId: ChatId,
    viewerId: ID?,
    templates: PaymentRequestBodyTemplates,
): String? {
    val split = request?.split ?: return null
    if (request.chatId != chatId) return null

    val title = (split.text as? PaymentRequestText.Plain)?.title?.trim()?.takeIf { it.isNotEmpty() }
    val amount = (viewerId?.let { split.shareOf(it)?.amount } ?: split.requested.takeIf { it.quarks > 0 })
        ?.takeIf { it.quarks > 0 }
        ?.formatted(Fiat.FormattingRule.Truncated)

    return when {
        amount != null && title != null -> templates.amountAndTitle.format(amount, title)
        amount != null -> templates.amountOnly.format(amount)
        title != null -> templates.titleOnly.format(title)
        else -> null
    }
}
