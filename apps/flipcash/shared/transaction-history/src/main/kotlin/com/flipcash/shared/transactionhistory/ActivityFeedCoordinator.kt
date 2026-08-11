package com.flipcash.shared.transactionhistory

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.ActivityFeedMessageWithToken
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.persistence.sources.MessageDataSource
import com.flipcash.app.persistence.sources.UserProfileDataSource
import com.flipcash.app.persistence.sources.mapper.notifications.MessageEntityToFeedMessageMapper
import com.flipcash.app.persistence.sources.mediator.FeedRemoteMediator
import com.flipcash.services.controllers.ActivityFeedController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.ActivityFeedNotification
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.models.NotificationMetadata
import com.flipcash.services.models.NotificationState
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.transactionhistory.internal.TransactionItemMapper
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityFeedCoordinator @Inject internal constructor(
    private val activityFeedController: ActivityFeedController,
    private val dataSource: MessageDataSource,
    private val mapper: MessageEntityToFeedMessageMapper,
    private val userManager: UserManager,
    private val tokenProvider: TokenMetadataProvider,
    private val transactionItemMapper: TransactionItemMapper,
    private val userProfiles: UserProfileDataSource,
    private val profileController: ProfileController,
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    // App-lifetime scope for caching the recent-transactions pages (see recentTransactions).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Counterparty user-ids (hex) currently being resolved over the network, so concurrent misses
    // for the same user collapse to a single fetch.
    private val resolvingProfiles = ConcurrentHashMap.newKeySet<String>()

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ActivityFeedMessage>> = userManager.state
        // Dedupe the auth gate so the Pager is built ONCE. Without distinctUntilChanged, every
        // unrelated userManager.state emission (balance polls, etc.) re-passes the filter and
        // flatMapLatest rebuilds the Pager → a REFRESH loop that constantly resets the list and
        // locks the UI. (Same guard as BlocklistCoordinator.) The previous nested
        // state→flatMapLatest→state→flatMapLatest wrapping doubled the churn; collapsed to one.
        .map { it.authState.canAccessAuthenticatedApis }
        .distinctUntilChanged()
        .filter { it }
        .flatMapLatest {
            Pager(
                config = pagingConfig,
                remoteMediator = FeedRemoteMediator(
                    activityFeedController,
                    dataSource,
                    // Resolve-on-fetch: back-fill counterparty profiles as each page lands.
                    onFetched = { notifications ->
                        ensureProfiles(notifications.mapNotNull { counterpartyOf(it.metadata) })
                    },
                )
            ) {
                dataSource.observe()
            }.flow.map { page -> page.map { entity -> mapper.map(entity) } }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun transactions(mint: Mint): Flow<PagingData<ActivityFeedMessageWithToken>> = messages.map { page ->
            page.filter { message ->
                message.amount?.mint == mint
            }.map { msg ->
                val result = msg.amount?.mint?.let { tokenProvider.getTokenMetadata(it) }?.getOrNull()
                ActivityFeedMessageWithToken(msg, result?.token)
            }
        }

    /**
     * Presentation-ready recent activity across every token, newest first.
     *
     * Raw message pages are cached in [scope] (so they survive config changes and re-subscription),
     * then combined with the observed profile **and** token caches so each row's avatar, title, and
     * (for deposits/withdrawals) token icon resolve **synchronously from memory** — no per-item I/O
     * in the paging transform. Because the caches are applied *after* [cachedIn], a profile or token
     * landing in cache re-maps the visible rows **without reloading** the list.
     *
     * This is a deliberate replacement for a previous design that called
     * `tokenProvider.getTokenMetadata(mint)` per item *inside* the transform. That path hit the
     * network for any mint the user held no account for (e.g. a tip in a creator coin) — and since
     * such mints are never cached, every page emission re-fetched them, so the wallet feed churned
     * network calls and never settled ("locks up when a tip comes into view"). Resolving tokens
     * cache-only here removes the churn entirely; a not-yet-cached counterparty/token simply resolves
     * later when it lands (or, for an unheld tip coin, stays absent — tips use the profile avatar,
     * not the token icon).
     *
     * NB: we intentionally do NOT `.filter` the PagingData — filtering pages makes the presenter keep
     * requesting more pages to fill the viewport (a runaway-load stall). Unresolved rows render with a
     * generic avatar + their server-fallback title instead of being dropped.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun recentTransactions(): Flow<PagingData<TransactionListItem>> =
        messages
            .cachedIn(scope)
            .combine(resolvers) { paging, (profiles, tokens) ->
                paging.map { msg ->
                    // Resolve-on-miss: a visible row whose counterparty isn't cached triggers a
                    // background fetch+store; when it lands, observeProfiles re-emits and this row
                    // re-maps with the resolved avatar + name (no reload).
                    counterpartyOf(msg.metadata)
                        ?.takeUnless { profiles.containsKey(it.hexEncodedString()) }
                        ?.let(::ensureProfile)
                    val token = msg.amount?.mint?.let { tokens[it] }
                    transactionItemMapper.map(ActivityFeedMessageWithToken(msg, token) to profiles)
                }
            }

    /**
     * Ensures a cached [UserProfile] exists for each of [userIds], fetching any miss over the network
     * and storing it so [UserProfileDataSource.observeProfiles] re-emits. In-flight fetches for the
     * same user are de-duplicated. Fire-and-forget on [scope]; failures are left for the next trigger.
     */
    private fun ensureProfiles(userIds: Collection<ID>) = userIds.forEach(::ensureProfile)

    private fun ensureProfile(userId: ID) {
        val hex = userId.hexEncodedString()
        if (!resolvingProfiles.add(hex)) return
        scope.launch {
            try {
                if (userProfiles.getCachedProfile(userId) != null) return@launch
                profileController.getProfileForUser(userId)
                    .onSuccess { userProfiles.store(userId, it) }
            } finally {
                resolvingProfiles.remove(hex)
            }
        }
    }

    private fun counterpartyOf(meta: MessageMetadata?): ID? = when (meta) {
        is MessageMetadata.DirectlySentCrypto -> meta.userId
        is MessageMetadata.ReceivedCrypto -> meta.userId
        else -> null
    }

    private fun counterpartyOf(meta: NotificationMetadata?): ID? = when (meta) {
        is NotificationMetadata.DirectlySentCrypto -> meta.userId
        is NotificationMetadata.ReceivedCrypto -> meta.userId
        else -> null
    }

    /**
     * A **preview** of the [limit] most recent transactions (newest first), presentation-ready. Unlike
     * [recentTransactions] this is a bounded, non-paged list for surfaces that show only a glimpse of
     * activity (e.g. the wallet screen); the full paged history uses [recentTransactions]. Same live
     * resolution as the paged flow: token/profile caches fill avatars, titles, and icons reactively,
     * and an uncached counterparty triggers a background fetch (see [ensureProfile]).
     */
    fun recentTransactions(limit: Int): Flow<List<TransactionListItem>> =
        combine(dataSource.observeRecent(limit), resolvers) { messages, (profiles, tokens) ->
            messages.map { msg ->
                counterpartyOf(msg.metadata)
                    ?.takeUnless { profiles.containsKey(it.hexEncodedString()) }
                    ?.let(::ensureProfile)
                val token = msg.amount?.mint?.let { tokens[it] }
                transactionItemMapper.map(ActivityFeedMessageWithToken(msg, token) to profiles)
            }
        }

    /**
     * Observed profile + token caches, paired for a single [combine] against the cached pages. Both
     * are network-free reads that re-emit as their caches hydrate, so rows resolve reactively from
     * memory. Started eagerly-cold: it only collects while [recentTransactions] is subscribed.
     */
    private val resolvers: Flow<Pair<Map<String, UserProfile>, Map<Mint, Token>>> =
        combine(userProfiles.observeProfiles(), tokenProvider.observeTokenCache()) { profiles, tokens ->
            profiles to tokens
        }

    /** Reactive "has the user ever added money" — any completed deposit/buy in the feed. */
    fun hasEverAddedMoney(): Flow<Boolean> = dataSource.hasEverAddedMoney()

    suspend fun checkPendingMessagesForUpdates(): Result<Int> {
        val pendingMessages = dataSource.query(whereClause = "state = '${NotificationState.PENDING.name}'")

        if (pendingMessages.isEmpty()) {
            trace(
                tag = "ActivityFeedCoordinator",
                message = "No pending messages found",
                type = TraceType.Silent
            )
            return Result.success(0)
        }

        return activityFeedController.getNotificationsById(pendingMessages.map { it.id })
            .map { notifications ->
                val completedCount = notifications.count { it.state == NotificationState.COMPLETED }

                trace(
                    tag = "ActivityFeedCoordinator",
                    message = "$completedCount notifications completed from batch",
                    type = TraceType.Silent
                )

                dataSource.upsert(notifications)
                completedCount
            }
    }

    suspend fun fetchSinceLatest(count: Int = 20): Result<Unit> {
        val latest = dataSource.getMostRecent()
        return activityFeedController.queryNotificationsFor(
            type = ActivityFeedType.TransactionHistory,
            queryOptions = QueryOptions(
                limit = count,
                token = latest?.id,
                descending = latest == null,
            )
        ).onSuccess { dataSource.upsert(it) }.map { Unit }
    }
}