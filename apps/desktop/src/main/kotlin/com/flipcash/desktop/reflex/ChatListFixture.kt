package com.flipcash.desktop.reflex

import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.storage.DirectoryPreferencesFiles
import com.flipcash.app.persistence.sources.PaymentRequestDataSource
import com.flipcash.app.persistence.sources.TokenDataSource
import com.flipcash.app.persistence.sources.mapper.tokens.EntityToTokenMapper
import com.flipcash.app.persistence.sources.mapper.tokens.RelationToTokenWithBalanceMapper
import com.flipcash.app.persistence.sources.mapper.tokens.TokenToEntityMapper
import com.flipcash.app.tipping.internal.ChatRowActions
import com.flipcash.app.tipping.internal.ChatsViewModel
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.chat.GroupKeyEnvelopes
import com.flipcash.services.chat.WrongKeyChats
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.PaymentRequestController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.AnalyticsIdentifier
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatArchiveStore
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.ChatFeeds
import com.flipcash.shared.chat.ChatMarkedUnreadStore
import com.flipcash.shared.chat.ChatReadActions
import com.flipcash.shared.chat.ChatSummary
import com.flipcash.shared.chat.HostLobbies
import com.flipcash.shared.chat.LobbyRequestStore
import com.flipcash.shared.chat.LobbyRequests
import com.flipcash.shared.chat.PaymentRequests
import com.getcode.chatcipher.DefaultChatCipher
import com.getcode.chatcipher.DefaultInviteCipher
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.controllers.CurrencyController
import com.getcode.opencode.controllers.TokenController
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.generators.MnemonicGenerator
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Rate
import com.getcode.util.resources.ComposeResourceHelper
import com.hoc081098.channeleventbus.ChannelEventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.nio.file.Files
import kotlin.time.Instant

/**
 * A [ChatsViewModel] over seeded chats, for the Reflex host. Nothing here reaches the backend:
 * the chat list comes from a [ChatCoordinator] holding [seed], and every repository under the
 * view model's other dependencies is a [FixtureGuard.network] stand-in.
 *
 * The coordinator is the seam because it is what the view model reads its feeds from. Faking
 * `ChatRepository` instead would mean running the real coordinator, which needs a signed-in
 * account's Room database and group keys to decrypt. The view model's other dependencies are
 * the real classes, built by hand: the desktop graph binds the real coordinator in `ChatModule`,
 * whose bindings are internal to that module, so a second graph can't swap it.
 */
class ChatListFixture(
    dispatchers: DispatcherProvider,
    seed: ChatFeeds = defaultSeed,
    val guard: FixtureGuard = FixtureGuard(),
    dataDir: File = Files.createTempDirectory("reflex-chats").toFile(),
) {
    val feeds = MutableStateFlow(seed)

    private val chatCoordinator: ChatCoordinator = object : ChatCoordinator by guard.unfaked() {
        override fun feedWithArchived(vararg chatTypes: ChatType): Flow<ChatFeeds> = feeds
        override fun currentFeedWithArchived(vararg chatTypes: ChatType): ChatFeeds = feeds.value
        override fun observeSenderProfiles(): Flow<Map<String, UserProfile>> = flowOf(emptyMap())
        override fun currentSenderProfiles(): Map<String, UserProfile> = emptyMap()
    }

    private val exchange: Exchange = object : Exchange by guard.unfaked() {
        override fun observePreferredRate(): Flow<Rate> = flowOf(Rate.oneToOne)
        override fun observeRates() = flowOf(emptyMap<com.getcode.opencode.model.financial.CurrencyCode, Rate>())
    }

    private val accountController = AccountController(guard.network(), guard.unfaked())

    private val userManager = UserManager(
        mnemonicManager = MnemonicManager(MnemonicGenerator()),
        analyticsIdentifier = AnalyticsIdentifier { },
        eventBus = ChannelEventBus(),
        accountController = accountController,
    ).apply { set(selfId) }

    private val tokenCoordinator = TokenCoordinator(
        preferencesFiles = DirectoryPreferencesFiles(dataDir),
        tokenController = TokenController(
            accountController,
            CurrencyController(guard.network(), TransactionController(guard.network(), guard.network(), accountController)),
        ),
        accountController = accountController,
        networkObserver = guard.unfaked(),
        exchange = exchange,
        dataSource = TokenDataSource(TokenToEntityMapper(), EntityToTokenMapper(), RelationToTokenWithBalanceMapper(EntityToTokenMapper())),
        dispatchers = dispatchers,
    )

    private val chatController = ChatController(guard.network(), userManager)

    private val lobbyStore = object : LobbyRequestStore {
        override fun observe(): Flow<Map<ChatId, Instant>> = flowOf(emptyMap())
        override suspend fun put(chatId: ChatId, enteredAt: Instant) = Unit
        override suspend fun remove(chatId: ChatId) = Unit
    }

    val viewModel = ChatsViewModel(
        exchange = exchange,
        chatCoordinator = chatCoordinator,
        userManager = userManager,
        tokenCoordinator = tokenCoordinator,
        resources = ComposeResourceHelper(),
        dispatchers = dispatchers,
        archiveStore = ChatArchiveStore.None,
        lobbyRequests = LobbyRequests(chatController, lobbyStore, chatCoordinator),
        hostLobbies = HostLobbies(
            chatController,
            GroupKeyEnvelopes(DefaultChatCipher, DefaultInviteCipher, guard.unfaked(), chatController, WrongKeyChats(), guard.unfaked()),
        ),
        paymentRequests = PaymentRequests(
            PaymentRequestController(guard.network(), userManager),
            GroupContentCrypto(DefaultChatCipher, guard.unfaked()),
            PaymentRequestDataSource(),
        ),
        rowActions = ChatRowActions(
            chatCoordinator,
            ChatReadActions(chatCoordinator, ChatMarkedUnreadStore.None),
            ChatArchiveStore.None,
            FlipcashAnalytics.None,
        ),
    )

    companion object {
        val selfId: ID = List(32) { 1 }

        fun chatId(n: Int) = ChatId(ByteArray(16) { n.toByte() })

        private fun group(n: Int, title: String, activity: Long, unread: Int, markedUnread: Boolean = false) = ChatSummary(
            metadata = ChatMetadata(
                chatId = chatId(n),
                type = ChatType.GROUP,
                members = emptyList(),
                lastMessage = null,
                lastActivity = Instant.fromEpochSeconds(activity),
                title = title,
            ),
            unreadCount = unread,
            markedUnread = markedUnread,
        )

        private fun tipDm(n: Int, name: String, activity: Long, unread: Int): ChatSummary {
            fun member(id: ID, displayName: String) = ChatMember(
                userId = id,
                userProfile = UserProfile(displayName = displayName, socialAccounts = emptyList(), phoneNumber = null, email = null),
                pointers = emptyList(),
            )
            return ChatSummary(
                metadata = ChatMetadata(
                    chatId = chatId(n),
                    type = ChatType.TIP_DM,
                    members = listOf(member(selfId, "Me"), member(List(32) { n.toByte() }, name)),
                    lastMessage = null,
                    lastActivity = Instant.fromEpochSeconds(activity),
                ),
                unreadCount = unread,
            )
        }

        /**
         * Four main-list chats (two unread, two groups) and one unread archived group. No private group
         * the fixture user hosts (its lobby would be fetched), no payment requests (each would be
         * fetched), no mutes (muting reads the clock).
         */
        val defaultSeed = ChatFeeds(
            main = listOf(
                group(1, "Ballers", activity = 4_000, unread = 3),
                tipDm(2, "Ava", activity = 3_000, unread = 0),
                group(3, "Book club", activity = 2_000, unread = 0),
                tipDm(4, "Sam", activity = 1_000, unread = 1),
            ),
            archived = listOf(group(5, "Old crew", activity = 500, unread = 2)),
        )
    }
}
