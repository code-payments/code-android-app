package com.flipcash.app.blocklist

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.flipcash.app.core.blocklist.BlockedUserProfile
import com.flipcash.app.persistence.sources.BlockedUserDataSource
import com.flipcash.app.persistence.sources.mapper.blocklist.ResolvedBlockedUser
import com.flipcash.app.persistence.sources.mediator.BlocklistRemoteMediator
import com.flipcash.services.controllers.BlocklistController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the offline-first blocklist stream and unblock action for the account's blocklist screen.
 *
 * Mirrors [com.flipcash.shared.transactionhistory.ActivityFeedCoordinator]: gated on an authenticated
 * session, it wires a [Pager] + [BlocklistRemoteMediator] over the Room-backed
 * [BlockedUserDataSource] and maps cached entities to display [BlockedUserProfile]s.
 */
@Singleton
class BlocklistCoordinator @Inject constructor(
    private val blocklistController: BlocklistController,
    private val profileController: ProfileController,
    private val dataSource: BlockedUserDataSource,
    private val chatCoordinator: ChatCoordinator,
    private val userManager: UserManager,
) {
    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
    val blocklist: Flow<PagingData<BlockedUserProfile>> = userManager.state
        // Dedupe the auth gate so the Pager is built ONCE. Without this, every unrelated
        // userManager.state emission re-passes the filter and flatMapLatest rebuilds the Pager —
        // each new PagingData resets the list to 0 items, flashing the empty state.
        .map { it.authState.canAccessAuthenticatedApis }
        .distinctUntilChanged()
        .filter { it }
        .flatMapLatest {
            Pager(
                config = pagingConfig,
                remoteMediator = BlocklistRemoteMediator(
                    blocklistController,
                    profileController,
                    dataSource,
                ),
            ) {
                dataSource.observe()
            }.flow.map { page -> page.map { entity -> dataSource.toProfile(entity) } }
        }
    
    /** Blocks [userId] and hides the DM so it drops out of the Tips feed immediately. */
    suspend fun blockUser(userId: ID): Result<Unit> =
        blocklistController.blockUser(userId).onSuccess {
            // TIP_DM ids are derivable, so no network lookup is needed to find the chat to hide.
            chatCoordinator.generateChatId(userId).getOrNull()
                ?.let { chatCoordinator.setChatHidden(it, hidden = true) }
        }

    /** Unblocks [userId], removing the cached row so the list reflects it immediately. */
    suspend fun unblock(userId: ID): Result<Unit> =
        blocklistController.unblockUser(userId).onSuccess {
            dataSource.delete(userId)
            // Inverse of the hide-on-block: restore the DM so it reappears in the Tips feed.
            chatCoordinator.generateChatId(userId).getOrNull()
                ?.let { chatCoordinator.setChatHidden(it, hidden = false) }
        }

    /**
     * Pulls the latest blocklist from the server and atomically replaces the local cache. Meant to
     * run on app launch/resume so the list stays current — including blocks/unblocks made on other
     * devices — without needing the blocklist screen to be opened.
     */
    suspend fun refresh(): Result<Unit> = runCatching {
        val page = blocklistController.getBlocklist(QueryOptions()).getOrThrow()
        val resolved = coroutineScope {
            page.users.map { blocked ->
                async {
                    ResolvedBlockedUser(
                        blocked = blocked,
                        profile = profileController.getProfileForUser(blocked.userId).getOrNull(),
                    )
                }
            }.awaitAll()
        }
        dataSource.replaceAll(resolved)
    }
}
