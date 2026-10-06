package com.flipcash.app.core.chat

import com.flipcash.app.core.AppRoute
import com.flipcash.services.models.chat.ChatId
import com.getcode.opencode.model.core.ID

/**
 * Where "open a DM with [userId]" lands: the chat once it exists, else the profile.
 *
 * [dmChatId] is the DM's id only when it is openable, which is the caller's call to make: it has
 * members and the person is not blocked. Null sends the viewer to the profile, tagged with
 * [origin] so the profile knows how to leave after a block.
 */
fun dmDestination(userId: ID, dmChatId: ChatId?, origin: ProfileOrigin): AppRoute =
    if (dmChatId != null) {
        AppRoute.Messaging.Chat(ChatIdentifier.ByChatId(dmChatId))
    } else {
        AppRoute.Messaging.Profile(ProfileAddress.ById(userId), origin)
    }
