package com.flipcash.shared.chat.internal

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.ChatRosterDataSource
import com.flipcash.app.persistence.sources.RosterSyncState
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterSummary
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
 * Keeps the device's copy of a group's roster whole, so the mention picker can search every
 * member rather than the ones the feed happened to carry.
 *
 * `GetRoster` pages at most [PAGE_SIZE] members, most recently joined first, and each member
 * carries the roster version they joined at. There is no delta against a roster version, so
 * repairs aim for the smallest read instead:
 *
 * - **Joins missed** since the watermark come back from the top of the roster: [catchUp] reads
 *   down to the first member at or below the watermark, usually one page.
 * - **Leaves missed** are invisible from the top. A catch-up notices them only as more members
 *   held than `member_count`, and hands the chat to a [fullSync] in WorkManager, which is the one
 *   read that can say who is gone.
 *
 * Runs on a group's open, not on launch: reading every group's roster up front would cost a page
 * per hundred members of each whether or not the user ever mentions anyone there. Versions are
 * only ever compared, never subtracted: the proto calls them opaque.
 */
@Singleton
class RosterSync @Inject constructor(
    private val chatController: ChatController,
    private val metadataDataSource: ChatMetadataDataSource,
    private val memberDataSource: ChatMemberDataSource,
    private val rosterDataSource: ChatRosterDataSource,
    private val reconcileScheduler: RosterReconcileScheduler,
    dispatchers: DispatcherProvider,
) : RosterSyncTrigger {

    // Its own scope, like [SenderResolver]'s: a read outlives the screen that asked for it.
    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    // One read per chat at a time. A second open of the same chat waits, then finds nothing to do.
    private val locks = ConcurrentHashMap<ChatId, Mutex>()

    private suspend fun <T> locked(chatId: ChatId, block: suspend () -> T): T =
        locks.getOrPut(chatId) { Mutex() }.withLock { block() }

    override fun onChatOpened(chatId: ChatId) {
        scope.launch { onOpen(chatId) }
    }

    override fun onRosterGap(chatId: ChatId) {
        scope.launch { onGap(chatId) }
    }

    /**
     * What a group's open does: the first full read if it never had one, the pending reconcile if
     * one is owed, otherwise a catch-up if the roster has moved past the watermark.
     */
    internal suspend fun onOpen(chatId: ChatId) {
        if (metadataDataSource.getChatType(chatId) != ChatType.GROUP) return
        locked(chatId) {
            val state = rosterDataSource.getSyncState(chatId)
            when {
                state == null || !state.fullySynced -> firstSync(chatId)
                // KEEP makes this a no-op while the work is queued, and re-queues it if it gave up.
                state.reconcilePending -> reconcileScheduler.schedule(chatId)
                metadataDataSource.getRosterVersion(chatId) > state.watermark -> catchUp(chatId, state)
            }
        }
    }

    /**
     * A version skipped on the stream. Only a chat already read in full is caught up here; one
     * never read waits for its open, so a gap does not start a read of a group nobody opened.
     */
    internal suspend fun onGap(chatId: ChatId) = locked(chatId) {
        val state = rosterDataSource.getSyncState(chatId)
        if (state == null || !state.fullySynced || state.reconcilePending) return@locked
        catchUp(chatId, state)
    }

    /** The full read WorkManager runs. False when it should be retried. */
    internal suspend fun reconcileNow(chatId: ChatId): Boolean = locked(chatId) { fullSync(chatId) }

    // A roster of one page is a single request, so it is read on the spot. Anything larger goes
    // to WorkManager, where the walk survives the user leaving the chat or the process dying.
    private suspend fun firstSync(chatId: ChatId) {
        if (metadataDataSource.getMemberCount(chatId) > PAGE_SIZE) {
            reconcileScheduler.schedule(chatId)
        } else {
            fullSync(chatId)
        }
    }

    /**
     * Recovers the joins missed since [state]'s watermark by reading from the top of the roster,
     * then checks the count for leaves the top cannot show.
     */
    internal suspend fun catchUp(chatId: ChatId, state: RosterSyncState) {
        val watermark = state.watermark
        var token: PagingToken? = null
        var pages = 0
        var summary: RosterSummary? = null
        var readVersion = Long.MAX_VALUE
        var recovered = 0
        var reachedStored = false

        while (true) {
            val page = chatController.getRoster(chatId, QueryOptions(limit = PAGE_SIZE, token = token))
                .getOrElse {
                    trace(tag = TAG, message = "Roster catch-up failed for $chatId", type = TraceType.Error)
                    return
                }
            pages++
            if (summary == null) summary = page.rosterSummary
            readVersion = minOf(readVersion, page.rosterSummary.version)

            // The stopping rule. Pages run most recently joined first, and Member.version is the
            // version a member joined at, so the first member at or below the watermark is where
            // the stored copy begins and everyone after them is already held.
            // REVISIT: model.proto says Member.version will move on later member changes, such as
            // a role change. Once it does, version order stops matching page order and this rule
            // no longer holds: a long-standing member with a bumped version reads as a new join.
            val newer = page.members.takeWhile { it.version > watermark }
            memberDataSource.upsert(chatId, newer)
            recovered += newer.size

            if (newer.size < page.members.size || !page.hasMore) {
                reachedStored = true
                break
            }
            val next = page.pagingToken
            if (pages >= MAX_PAGES || next == null) break
            token = next
        }

        if (!reachedStored) {
            // More joins missed than the cap reads: only a full read catches up now.
            trace(tag = TAG, message = "Roster catch-up for $chatId ran past the cap", type = TraceType.Silent)
            rosterDataSource.markReconcilePending(chatId)
            reconcileScheduler.schedule(chatId)
            return
        }

        val held = memberDataSource.countMembers(chatId).toLong()
        val memberCount = summary!!.memberCount
        when {
            held == memberCount -> {
                // The page's version, not the stream's: a large group's pages can trail the stream,
                // and the watermark may only claim what the pages showed.
                rosterDataSource.setWatermark(chatId, readVersion)
            }
            held > memberCount -> {
                // Someone left and the top of the roster cannot say who. Search keeps them until
                // the full read settles it.
                rosterDataSource.markReconcilePending(chatId)
                reconcileScheduler.schedule(chatId)
            }
            // A roster cut off at the cap is always short, so short is no sign of a missed join.
            state.truncated -> rosterDataSource.setWatermark(chatId, readVersion)
            // Otherwise a join is still to come: the page trails the stream, and the join arrives
            // as a roster update. (A chat never read in full does not get here: its open reads it.)
            else -> Unit
        }

        trace(
            tag = TAG,
            message = "Roster catch-up for $chatId: $recovered joins over $pages pages; " +
                "holding $held of $memberCount",
            type = TraceType.Silent,
        )
    }

    /**
     * Reads [chatId]'s whole roster, up to [MAX_PAGES], and drops the members it shows have left.
     * False if a page failed or the database is not open, so nothing was recorded.
     *
     * The reconcile rule is the proto's: a held member absent from the read has left, unless they
     * joined after the version the read described. With pages at different versions the lowest is
     * used, since each page is only a promise about the roster as of its own version.
     */
    internal suspend fun fullSync(chatId: ChatId): Boolean {
        if (!rosterDataSource.isAvailable) return false

        var token: PagingToken? = null
        var pages = 0
        var truncated = false
        val seen = HashSet<ID>()
        var readSummary: RosterSummary? = null

        while (true) {
            val page = chatController.getRoster(chatId, QueryOptions(limit = PAGE_SIZE, token = token))
                .getOrElse {
                    trace(tag = TAG, message = "Roster page ${pages + 1} failed for $chatId", type = TraceType.Error)
                    return false
                }
            pages++
            memberDataSource.upsert(chatId, page.members)
            page.members.mapTo(seen) { it.userId }
            if (readSummary == null || page.rosterSummary.version < readSummary.version) {
                readSummary = page.rosterSummary
            }

            if (!page.hasMore) break
            val next = page.pagingToken
            if (pages >= MAX_PAGES || next == null) {
                truncated = true
                break
            }
            token = next
        }

        val summary = readSummary!!
        // A partial read cannot tell who left, so it drops no one.
        if (!truncated) memberDataSource.reconcile(chatId, seen, readVersion = summary.version)
        // No-op unless the read is ahead of what the stream has applied.
        metadataDataSource.updateRoster(chatId, memberCount = summary.memberCount, rosterVersion = summary.version)
        rosterDataSource.markFullySynced(chatId, watermark = summary.version, truncated = truncated)

        trace(
            tag = TAG,
            message = "Roster read for $chatId: ${seen.size} members over $pages pages at v${summary.version}" +
                if (truncated) ", stopped at the cap" else "",
            type = TraceType.Silent,
        )
        return true
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

        /** Where a read stops: 2,000 members. A search covers whatever was read by then. */
        const val MAX_PAGES = 20
    }
}

/**
 * Told when a chat opens or its roster stream skips a version, so the roster can be repaired off
 * the caller's path.
 *
 * A seam rather than [RosterSync] itself so the classes that report these can be built in tests
 * without one.
 */
interface RosterSyncTrigger {

    /** Returns at once; any read it starts runs in the background. */
    fun onChatOpened(chatId: ChatId)

    /** Returns at once; any read it starts runs in the background. */
    fun onRosterGap(chatId: ChatId)

    /** Does nothing. What a class built without a roster sync -- a unit test -- is given. */
    object None : RosterSyncTrigger {
        override fun onChatOpened(chatId: ChatId) = Unit
        override fun onRosterGap(chatId: ChatId) = Unit
    }
}
