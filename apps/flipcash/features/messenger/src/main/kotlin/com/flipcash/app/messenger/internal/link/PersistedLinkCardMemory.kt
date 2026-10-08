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
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * [LinkCardMemory] that also keeps group, person and web page answers in `link_previews`, so the first visit
 * after a cold start paints those cards resolved rather than growing into them.
 *
 * Only those two are stored because only those two change size when they resolve: a group card
 * gains its requirement lines, a person card its handle and join line. Cash and token cards are
 * the same height in every state, so fetching them again moves nothing.
 *
 * The table is per user, like the database it lives in. Each time a database opens it is read
 * whole into memory, replacing whatever the previous user had, and a logout empties both.
 *
 * A row not written for [MAX_AGE] is dropped when the table is next read, so a link the reader
 * has stopped coming across does not stay forever. Drawing a card rewrites its row, at most once
 * per [REWRITE_AFTER] when the answer has not changed, to keep a card still in use from expiring.
 *
 * A person is stored as their public profile, not as the finished card: whether the link is the
 * viewer's own and how the join date is worded are worked out again on load, so neither a
 * different account nor a different locale reads a stale answer.
 */
@OptIn(ExperimentalStdlibApi::class)
internal class PersistedLinkCardMemory(
    private val store: LinkPreviewDataSource,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
    dispatchers: DispatcherProvider,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : LinkCardMemory(clock = now) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.IO)
    private val loaded = MutableStateFlow(false)

    /** When each stored row was last written, so an unchanged answer rewrites it only when due. */
    private val writtenAt = ConcurrentHashMap<String, Long>()

    init {
        scope.launch {
            store.observeAll(writtenSince = { now() - MAX_AGE.inWholeMilliseconds }).collect { records ->
                loaded.value = false
                _groups.clear()
                _users.clear()
                clearWebs()
                writtenAt.clear()
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
        val key = groupKey(chatId)
        if (_groups.put(chatId, state) == state && !rewriteDue(key)) return
        write(key, json.encodeToString(StoredGroup.serializer(), StoredGroup.of(state)))
    }

    override fun putUser(identity: LinkCard.User.Identity, state: LinkCard.User.State.Resolved) {
        val key = userKey(identity)
        if (_users.put(identity, state) == state && !rewriteDue(key)) return
        write(key, json.encodeToString(UserProfile.serializer(), state.profile.publicOnly()))
    }

    override fun putWeb(key: String, state: LinkCard.Web.State) {
        val rowKey = WEB_PREFIX + key
        val unchanged = webs[key] == state
        storeWeb(key, state)
        if (unchanged && !rewriteDue(rowKey)) return
        write(rowKey, webJson.encodeToString(StoredWeb.serializer(), StoredWeb.of(state)))
    }

    override fun removeGroup(chatId: ChatId) {
        if (_groups.remove(chatId) != null) delete(groupKey(chatId))
    }

    override fun removeUser(identity: LinkCard.User.Identity) {
        if (_users.remove(identity) != null) delete(userKey(identity))
    }

    private fun rewriteDue(key: String): Boolean =
        now() - (writtenAt[key] ?: 0L) >= REWRITE_AFTER.inWholeMilliseconds

    private fun delete(key: String) {
        writtenAt.remove(key)
        scope.launch {
            runCatching { store.delete(key) }
                .onFailure { trace(tag = TAG, message = "Failed to delete $key", error = it) }
        }
    }

    private fun write(key: String, value: String) {
        writtenAt[key] = now()
        scope.launch {
            runCatching {
                store.upsert(LinkPreviewRecord(key, value, writtenAt[key] ?: now()))
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
                record.key.startsWith(WEB_PREFIX) -> {
                    val stored = webJson.decodeFromString(StoredWeb.serializer(), record.json)
                    val state = stored.toState()
                    // Freshness comes from the row itself. A stale row is dropped like one that no
                    // longer decodes, and the link is looked up again the ordinary way.
                    val age = (now() - record.updatedAt).milliseconds
                    val ttl = if (state is LinkCard.Web.State.Resolved) WebLinks.RESOLVED_TTL else WebLinks.EMPTY_TTL
                    if (age > ttl) error("stale web row")
                    storeWeb(record.key.removePrefix(WEB_PREFIX), state, at = record.updatedAt)
                }
                else -> error("unknown key")
            }
        }.isSuccess
        if (loadedOk) writtenAt[record.key] = record.updatedAt else runCatching { store.delete(record.key) }
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

    /**
     * A web card's answer as stored, field names shared with iOS. A null title is
     * [LinkCard.Web.State.None]. Every field is written, nulls included.
     */
    @Serializable
    private data class StoredWeb(
        val title: String? = null,
        val description: String? = null,
        val imageUrl: String? = null,
        val host: String? = null,
    ) {
        fun toState(): LinkCard.Web.State =
            if (title == null) {
                LinkCard.Web.State.None
            } else {
                LinkCard.Web.State.Resolved(title, description, imageUrl, host ?: error("web row without a host"))
            }

        companion object {
            fun of(state: LinkCard.Web.State) = when (state) {
                is LinkCard.Web.State.Resolved -> StoredWeb(state.title, state.description, state.imageUrl, state.host)
                // Loading is never stored; it stands for no answer, so it is written as none only if asked to.
                LinkCard.Web.State.None, LinkCard.Web.State.Loading -> StoredWeb()
            }
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
        val MAX_AGE = 30.days
        val REWRITE_AFTER = 1.days
        const val GROUP_PREFIX = "group:"
        const val USER_PREFIX = "user:"
        const val WEB_PREFIX = "web:"
        const val BY_ID = "id:"
        const val BY_NAME = "name:"

        val json = Json { ignoreUnknownKeys = true }
        val webJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
