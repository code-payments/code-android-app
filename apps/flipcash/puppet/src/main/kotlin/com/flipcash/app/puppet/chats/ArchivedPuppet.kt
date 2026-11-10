package com.flipcash.app.puppet.chats

import com.flipcash.app.core.data.Loadable
import com.flipcash.app.tipping.internal.ArchivedChatsViewModel
import com.flipcash.libs.puppet.PuppetAction
import com.flipcash.libs.puppet.PuppetNode
import com.flipcash.services.models.chat.ChatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Archived screen as a Puppet node, pushed when Chats opens it.
 *
 * Its one shared action is [Unarchive], the row's swipe action, which moves the chat back to the
 * main list. Select mode is left out: iOS's Archived screen has none, so a shared file can't use it.
 * Unlike Chats' events, [Unarchive] is declared here because the view model takes it as a method
 * call rather than an event.
 */
fun archivedNode(viewModel: ArchivedChatsViewModel): PuppetNode = PuppetNode(
    name = "ArchivedChats",
    state = { archivedState(viewModel.stateFlow.value) },
    actions = listOf(PuppetAction(Unarchive.serializer()) { viewModel.unarchive(it.chatId) }),
)

/** Swipe a row's Unarchive. */
@Serializable
@SerialName("Unarchive")
data class Unarchive(val chatId: ChatId)

@OptIn(ExperimentalStdlibApi::class)
internal fun archivedState(state: ArchivedChatsViewModel.State): JsonElement {
    val rows = state.chats.dataOrNull.orEmpty()
    return buildJsonObject {
        put("loading", state.chats is Loadable.Loading)
        // Newest first, the order the screen lists them in.
        put("chatIds", JsonArray(rows.map { JsonPrimitive(it.chatId.bytes.toHexString()) }))
        put("chats", JsonArray(rows.map { row ->
            buildJsonObject {
                put("id", row.chatId.bytes.toHexString())
                put("name", row.title ?: row.displayName)
                put("unreadCount", row.unreadCount)
            }
        }))
    }
}
