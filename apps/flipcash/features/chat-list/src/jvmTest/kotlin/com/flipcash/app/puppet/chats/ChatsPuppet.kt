package com.flipcash.app.puppet.chats

import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.app.tipping.internal.ChatsViewModel.Event
import com.flipcash.libs.puppet.PuppetAction
import com.flipcash.libs.puppet.PuppetHost
import com.flipcash.libs.puppet.PuppetNode
import com.flipcash.libs.puppet.PuppetStack
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ChatListFilter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Chats screen as a Puppet node.
 *
 * Its actions are the view model's [com.getcode.view.PublicAction] events; `ChatsPuppetTest`
 * checks this list against the events that implement the marker, so a new one can't be left out.
 *
 * Its state is mapped by hand rather than serializing [ChatsViewModel.State]: the rows carry
 * account ids as byte lists, instants and media, `chats` is a generic sealed `Loadable`, and the
 * projection names chats in base58 while action input uses hex. The mapping uses hex chat ids
 * throughout, keeps the row fields a test asserts on, and adds the state's derived values
 * (`visibleChatIds`, `showsChips`, ...), which is what the screen draws from. Formatted strings
 * such as the last-message preview are left out: they depend on locale and the clock.
 */
fun chatsNode(viewModel: ChatsViewModel): PuppetNode = PuppetNode(
    name = "Chats",
    state = { chatsState(viewModel.stateFlow.value) },
    actions = chatsActions.map { it(viewModel) },
)

/** One entry per [com.getcode.view.PublicAction] event of [ChatsViewModel]. */
internal val chatsActions: List<(ChatsViewModel) -> PuppetAction<*>> = listOf(
    { vm -> PuppetAction(Event.FilterSelected.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.ChipsRevealed.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.SelectionStarted.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.SelectionToggled.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.SelectionEnded.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.ChatOpened.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.ArchivedOpened.serializer(), vm::dispatchEvent) },
    { vm -> PuppetAction(Event.NewChatOpened.serializer(), vm::dispatchEvent) },
)

/** The route Chats sent to the stack, as a node. These screens aren't hosted: they offer only the lent `Back`. */
internal fun routeNode(route: AppRoute): PuppetNode = when (route) {
    is AppRoute.Messaging.Chat -> {
        val chatId = (route.identifier as? ChatIdentifier.ByChatId)?.chatId?.hex
        PuppetNode("Chat", { buildJsonObject { put("chatId", chatId) } }, emptyList())
    }
    AppRoute.Messaging.ArchivedChats -> PuppetNode("ArchivedChats", { buildJsonObject {} }, emptyList())
    AppRoute.Messaging.NewChat -> PuppetNode("NewChat", { buildJsonObject {} }, emptyList())
    else -> PuppetNode(route::class.simpleName ?: "Unknown", { buildJsonObject {} }, emptyList())
}

@OptIn(ExperimentalStdlibApi::class)
private val ChatId.hex: String get() = bytes.toHexString()

// The chips list only main-list chats, which are all rows; an id without one stays base58.
private fun base58ToHex(base58: String, rows: Map<String, ChatId>): String = rows[base58]?.hex ?: base58

internal fun chatsState(state: ChatsViewModel.State): JsonElement {
    val rows = state.chats.dataOrNull.orEmpty()
    val byBase58 = rows.associateBy({ it.chatId.toString() }, { it.chatId })
    val projection = state.projection
    return buildJsonObject {
        put("loading", state.chats is Loadable.Loading)
        put("filter", state.filter.name)
        put("chipsRevealed", state.chipsRevealed)
        put("showsChips", state.showsChips)
        put("isSelecting", state.isSelecting)
        put("selection", state.selection?.let { ids -> JsonArray(ids.map { it.hex }.sorted().map(::JsonPrimitive)) } ?: JsonNull)
        put("canReadAll", state.canReadAll)
        put("showsArchivedRow", state.showsArchivedRow)
        put("hasNoChatsAtAll", state.hasNoChatsAtAll)
        put("chats", JsonArray(rows.map { row ->
            buildJsonObject {
                put("id", row.chatId.hex)
                put("name", row.title ?: row.displayName)
                put("isGroup", row.isGroup)
                put("chatType", row.chatType.name)
                put("unreadCount", row.unreadCount)
                put("markedUnread", row.markedUnread)
                put("isUnread", row.isUnread)
            }
        }))
        put("visibleChatIds", JsonArray(state.visibleChats.map { JsonPrimitive(it.chatId.hex) }))
        put("chips", buildJsonObject {
            ChatListFilter.entries.forEach { filter ->
                put(filter.name, JsonArray(projection.idsFor(filter).map { JsonPrimitive(base58ToHex(it, byBase58)) }))
            }
        })
        put("unreadChipCount", projection.unreadChipCount)
        put("groupsChipCount", projection.groupsChipCount)
        put("archivedRowCount", projection.archivedRowCount)
        put("tabBadge", projection.tabBadge)
    }
}

/**
 * The Puppet host over a Chats fixture from [newFixture]. `launch` (or `reset`) closes the current
 * fixture, builds a fresh one and empties the stack. Each `OpenScreen` the view model emits pushes
 * that route's node, standing in for the screen's `navigator.push`; the collector runs on the view
 * model's scope, so closing the fixture stops it. [settle] runs before each response; tests pass `advanceUntilIdle`, so the
 * view model's work has landed without a wall-clock wait.
 */
fun chatsHost(
    newFixture: () -> ChatListFixture,
    settle: suspend (ChatListFixture) -> Unit,
): PuppetHost {
    val stack = PuppetStack()
    fun start(): ChatListFixture = newFixture().also { fixture ->
        fixture.viewModel.eventFlow
            .filterIsInstance<Event.OpenScreen>()
            .onEach { stack.push(routeNode(it.screen)) }
            .launchIn(fixture.viewModel.viewModelScope)
    }
    var fixture = start()
    return PuppetHost(
        root = { chatsNode(fixture.viewModel) },
        settle = { settle(fixture) },
        violations = { fixture.guard.drain() },
        reset = {
            fixture.close()
            fixture = start()
        },
        stack = stack,
        launchRoutes = setOf(CHATS_ROUTE),
    )
}

/** The Chats tab in both apps' deep-link form, the only route this host launches. */
const val CHATS_ROUTE = "/tab/chats"
