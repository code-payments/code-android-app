package com.flipcash.shared.transactionhistory

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.services.models.chat.ChatId

/**
 * Where tapping an activity row leads.
 *
 * Most rows open their details. A send-back opens the DM it was paid in, falling back to the
 * details when that DM can't be opened: the server no longer has it, or it can't be reached right
 * now. Without that check a missing DM would open as an empty chat.
 *
 * The chat lookup is passed in rather than read here because the chat module already depends on
 * this one; the app binds it (see `TransactionRouterModule`).
 */
fun interface TransactionRouter {
    suspend fun routeFor(item: TransactionListItem): AppRoute

    companion object {
        /** Every row opens its details. For callers with no chat to open. */
        val DetailsOnly = TransactionRouter { item -> AppRoute.Sheets.TransactionDetails(item.messageId) }

        /** Opens a send-back's DM when [canOpen] says it exists, else the row's details. */
        fun forChats(canOpen: suspend (ChatId) -> Boolean) = TransactionRouter { item ->
            val chatId = item.returnChatId
            if (chatId != null && canOpen(chatId)) {
                AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(chatId))
            } else {
                AppRoute.Sheets.TransactionDetails(item.messageId)
            }
        }
    }
}
