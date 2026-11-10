package com.flipcash.app.puppet

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.puppet.chats.ChatListFixture
import com.flipcash.app.puppet.chats.archivedNode
import com.flipcash.app.puppet.newchat.newChatNode
import com.flipcash.libs.puppet.PuppetNode
import com.flipcash.libs.puppet.PuppetStack
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Every route a hosted screen can push, resolved to the node the stack shows. A screen pushes a
 * route, not a node, so a push can land on a screen from another feature module: Chats (chat-list)
 * opens New Chat (tipping) through here. A route without a hosted screen is a bare node named
 * after the screen, offering only the lent `Back`.
 */
internal class HostedRoutes(private val fixture: ChatListFixture, private val stack: PuppetStack) {

    fun push(route: AppRoute) = stack.push(node(route))

    @OptIn(ExperimentalStdlibApi::class)
    fun node(route: AppRoute): PuppetNode = when (route) {
        is AppRoute.Messaging.Chat -> {
            val chatId = (route.identifier as? ChatIdentifier.ByChatId)?.chatId?.bytes?.toHexString()
            PuppetNode("Chat", { buildJsonObject { put("chatId", chatId) } }, emptyList())
        }
        AppRoute.Messaging.ArchivedChats -> archivedNode(fixture.archivedViewModel())
        AppRoute.Messaging.NewChat -> newChatNode(fixture.newChatViewModel(), stack, this)
        AppRoute.Messaging.FindByUsername -> bare("FindByUsername")
        is AppRoute.Messaging.NewGroup -> bare(if (route.isPrivate) "NewPrivateGroup" else "NewCommunityGroup")
        AppRoute.Token.Discovery -> bare("DiscoverCurrencies")
        else -> bare(route::class.simpleName ?: "Unknown")
    }

    private fun bare(name: String) = PuppetNode(name, { buildJsonObject {} }, emptyList())
}
