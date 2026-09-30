package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ChatRosterDataSource
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.getcode.opencode.model.core.ID
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a group's whole roster into the member table, so the mention picker can search every
 * member rather than the ones the feed happened to carry.
 *
 * `GetRoster` pages at most [PAGE_SIZE] members, so a full read walks the pages until `has_more`
 * is false, stopping at [MAX_PAGES]. It runs on a group's open, not on launch: reading every
 * group's roster up front would cost a page per hundred members of each whether or not the user
 * ever mentions anyone there.
 *
 * Once read, the roster stays current off the event stream: [RosterStateHolder] applies joins and
 * leaves, and flags the chat through [ChatRosterDataSource.markNeedsResync] when it sees a version
 * skipped, which is the one case the stream cannot repair.
 */
@Singleton
class RosterSync @Inject constructor(
    private val chatController: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val rosterDataSource: ChatRosterDataSource,
    dispatchers: DispatcherProvider,
) : RosterSyncTrigger {

    // Its own scope, like [SenderResolver]'s: a read outlives the screen that asked for it, so
    // leaving the chat before the last page lands still leaves the roster complete.
    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    // One read per chat at a time. A second open of the same chat waits, then finds nothing to do.
    private val locks = ConcurrentHashMap<ChatId, Mutex>()

    override fun onChatOpened(chatId: ChatId) {
        scope.launch { syncIfNeeded(chatId) }
    }

    /** Reads [chatId]'s roster to the end if it is a group whose held roster is incomplete. */
    suspend fun syncIfNeeded(chatId: ChatId) {
        if (metadataDataSource.getChatType(chatId) != ChatType.GROUP) return
        locks.getOrPut(chatId) { Mutex() }.withLock {
            if (needsFullSync(chatId)) syncAll(chatId)
        }
    }

    /**
     * A group needs a full read when it has never had one, when the stream skipped a version since,
     * or when the device holds fewer members than the roster has — unless the last read stopped at
     * the page cap, where a new read would stop in the same place.
     */
    internal suspend fun needsFullSync(chatId: ChatId): Boolean {
        val state = rosterDataSource.getSyncState(chatId) ?: return true
        if (state.needsResync) return true
        if (state.truncated) return false
        return memberDataSource.countMembers(chatId) < metadataDataSource.getMemberCount(chatId)
    }

    /**
     * Pages through [chatId]'s roster, writing each page as it lands, and records how far it got.
     *
     * Members absent from a complete read are dropped, but only when every page described the same
     * roster version and the stream has not moved past it: a change applied mid-read could be one
     * the pages predate, and a page that predates a leave would put the member back. In that case
     * the read keeps what it wrote and flags the chat to be read again.
     */
    internal suspend fun syncAll(chatId: ChatId) {
        var token: PagingToken? = null
        var pages = 0
        var truncated = false
        val seen = HashSet<ID>()
        val versions = HashSet<Long>()
        var memberCount = 0L

        while (true) {
            val page = chatController.getRoster(chatId, QueryOptions(limit = PAGE_SIZE, token = token))
                .getOrElse {
                    // Nothing is recorded, so the next open tries again from the first page.
                    trace(tag = TAG, message = "Roster page ${pages + 1} failed for $chatId", type = TraceType.Error)
                    return
                }
            pages++
            memberDataSource.upsert(chatId, page.members)
            page.members.mapTo(seen) { it.userId }
            versions += page.rosterSummary.version
            memberCount = page.rosterSummary.memberCount

            if (!page.hasMore) break
            val next = page.pagingToken
            if (pages >= MAX_PAGES || next == null) {
                truncated = true
                break
            }
            token = next
        }

        val version = versions.max()
        val stored = metadataDataSource.getRosterVersion(chatId)
        val consistent = versions.size == 1 && stored <= version
        if (consistent && !truncated) {
            memberDataSource.retainOnly(chatId, seen)
        }
        if (consistent) {
            // No-op unless the pages are ahead of what the stream has applied.
            metadataDataSource.updateRoster(chatId, memberCount = memberCount, rosterVersion = version)
        }
        rosterDataSource.markSynced(chatId, version = version, truncated = truncated, needsResync = !consistent)

        trace(
            tag = TAG,
            message = "Roster read for $chatId: ${seen.size} members over $pages pages at v$version" +
                (if (truncated) ", stopped at the cap" else "") +
                (if (!consistent) ", roster moved during the read" else ""),
            type = TraceType.Silent,
        )
    }

    /**
     * Rewrites the first page of [chatId]'s roster: the members most recently joined, with their
     * current profiles. Profile changes do not move the roster version, so this is what keeps held
     * names fresh for a search that is about to start.
     */
    suspend fun refreshFirstPage(chatId: ChatId) {
        val page = chatController.getRoster(chatId, QueryOptions(limit = PAGE_SIZE)).getOrElse {
            trace(tag = TAG, message = "Roster refresh failed for $chatId", type = TraceType.Error)
            return
        }
        memberDataSource.upsert(chatId, page.members)
    }

    companion object {
        private const val TAG = "RosterSync"

        /** The most `GetRoster` returns in one page. */
        const val PAGE_SIZE = 100

        /** Where a full read stops: 2,000 members. A search covers whatever was read by then. */
        const val MAX_PAGES = 20
    }
}

/**
 * Told when a chat opens, so a group's roster can be read in full off the screen's path.
 *
 * A seam rather than [RosterSync] itself so the delegates that report an open can be built in
 * tests without one.
 */
interface RosterSyncTrigger {

    /** Returns at once; any read it starts runs in the background. */
    fun onChatOpened(chatId: ChatId)

    /** Does nothing. What a delegate built without a roster sync -- a unit test -- is given. */
    object None : RosterSyncTrigger {
        override fun onChatOpened(chatId: ChatId) = Unit
    }
}
