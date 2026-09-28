package com.flipcash.app.messenger.internal.link

import com.flipcash.app.messenger.internal.joinedLine
import com.flipcash.app.persistence.sources.LinkPreviewDataSource
import com.flipcash.app.persistence.sources.LinkPreviewRecord
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.models.LinkCard
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * [LinkCardMemory] that also keeps group and person answers in `link_previews`, so the first visit
 * after a cold start paints those cards resolved rather than growing into them.
 *
 * Only those two are stored because only those two change size when they resolve: a group card
 * gains its requirement lines, a person card its handle and join line. Cash and token cards are
 * the same height in every state, so fetching them again moves nothing.
 *
 * The table is per user, like the database it lives in. Each time a database opens it is read
 * whole into memory, replacing whatever the previous user had, and a logout empties both.
 *
 * A person is stored as their public profile, not as the finished card: whether the link is the
 * viewer's own and how the join date is worded are worked out again on load, so neither a
 * different account nor a different locale reads a stale answer.
 */
@OptIn(ExperimentalStdlibApi::class)
@Singleton
internal class PersistedLinkCardMemory @Inject constructor(
    private val store: LinkPreviewDataSource,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
) : LinkCardMemory() {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)
    private val loaded = MutableStateFlow(false)

    init {
        scope.launch {
            store.observeAll().collect { records ->
                loaded.value = false
                _groups.clear()
                _users.clear()
                records.forEach { load(it) }
                loaded.value = true
                trace(tag = TAG, message = "Loaded ${records.size} link previews", type = TraceType.Process)
            }
        }
    }

    override suspend fun awaitLoaded() {
        loaded.first { it }
    }

    override fun putGroup(chatId: ChatId, state: LinkCard.GroupInvite.State.Resolved) {
        if (_groups.put(chatId, state) == state) return
        write(groupKey(chatId), json.encodeToString(StoredGroup.serializer(), StoredGroup.of(state)))
    }

    override fun putUser(identity: LinkCard.User.Identity, state: LinkCard.User.State.Resolved) {
        if (_users.put(identity, state) == state) return
        write(userKey(identity), json.encodeToString(UserProfile.serializer(), state.profile.publicOnly()))
    }

    private fun write(key: String, value: String) {
        scope.launch {
            runCatching {
                store.upsert(LinkPreviewRecord(key, value, Clock.System.now().toEpochMilliseconds()))
            }.onFailure { trace(tag = TAG, message = "Failed to store $key", error = it) }
        }
    }

    /**
     * One stored row back into memory. A row that no longer decodes -- a shape an older build
     * wrote -- is deleted rather than kept: the card resolves it again the ordinary way.
     */
    private suspend fun load(record: LinkPreviewRecord) {
        val loadedOk = runCatching {
            when {
                record.key.startsWith(GROUP_PREFIX) -> {
                    val chatId = ChatId(record.key.removePrefix(GROUP_PREFIX))
                    _groups[chatId] = json.decodeFromString(StoredGroup.serializer(), record.json).toState()
                }
                record.key.startsWith(USER_PREFIX) -> {
                    val identity = identityOf(record.key.removePrefix(USER_PREFIX))
                    val profile = json.decodeFromString(UserProfile.serializer(), record.json)
                    userCardState(profile, userManager.accountId) { joinedLine(it, resources) }
                        ?.let { _users[identity] = it }
                        ?: error("stored profile names no account")
                }
                else -> error("unknown key")
            }
        }.isSuccess
        if (!loadedOk) runCatching { store.delete(record.key) }
    }

    /** A group card's answer as stored. Mirrors [LinkCard.GroupInvite.State.Resolved]. */
    @Serializable
    private data class StoredGroup(
        val title: String?,
        val picture: MediaItem?,
        val memberCount: Long,
        val requirement: StoredRequirement?,
    ) {
        fun toState() = LinkCard.GroupInvite.State.Resolved(
            title = title,
            picture = picture,
            memberCount = memberCount,
            requirement = requirement?.let {
                LinkCard.GroupInvite.Requirement(it.amount, it.currencyName, it.staffOnly)
            },
        )

        companion object {
            fun of(state: LinkCard.GroupInvite.State.Resolved) = StoredGroup(
                title = state.title,
                picture = state.picture,
                memberCount = state.memberCount,
                requirement = state.requirement?.let {
                    StoredRequirement(it.amount, it.currencyName, it.staffOnly)
                },
            )
        }
    }

    @Serializable
    private data class StoredRequirement(
        val amount: String?,
        val currencyName: String?,
        val staffOnly: Boolean,
    )

    private companion object {
        const val TAG = "LinkCardMemory"
        const val GROUP_PREFIX = "group:"
        const val USER_PREFIX = "user:"
        const val BY_ID = "id:"
        const val BY_NAME = "name:"

        val json = Json { ignoreUnknownKeys = true }

        fun groupKey(chatId: ChatId) = GROUP_PREFIX + chatId.bytes.toHexString()

        fun userKey(identity: LinkCard.User.Identity) = USER_PREFIX + when (identity) {
            is LinkCard.User.Identity.ById -> BY_ID + identity.userId.toByteArray().toHexString()
            is LinkCard.User.Identity.ByUsername -> BY_NAME + identity.username
        }

        fun identityOf(key: String): LinkCard.User.Identity = when {
            key.startsWith(BY_ID) -> LinkCard.User.Identity.ById(key.removePrefix(BY_ID).hexToByteArray().toList())
            key.startsWith(BY_NAME) -> LinkCard.User.Identity.ByUsername(key.removePrefix(BY_NAME))
            else -> error("unknown identity $key")
        }

        /**
         * The fields a stranger's card and the DM header read, and nothing else. A fetched
         * profile of someone else carries no contact methods, but this does not rely on that;
         * social accounts are dropped as well, being polymorphic and read by neither.
         */
        fun UserProfile.publicOnly() = copy(socialAccounts = emptyList(), phoneNumber = null, email = null)
    }
}
