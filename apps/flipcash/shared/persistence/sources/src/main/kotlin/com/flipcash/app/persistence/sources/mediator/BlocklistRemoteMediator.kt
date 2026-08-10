package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.BlockedUserWithProfile
import com.flipcash.app.persistence.sources.BlockedUserDataSource
import com.flipcash.app.persistence.sources.mapper.blocklist.ResolvedBlockedUser
import com.flipcash.services.controllers.BlocklistController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.QueryOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Drives the offline-first blocklist: fetches server pages via [BlocklistController], resolves each
 * blocked user's display profile via [ProfileController] (the blocklist endpoint returns only id +
 * timestamp), and writes them into Room ([dataSource]) where the [BlockedUserDao] paging source is
 * the single source of truth the UI observes.
 *
 * The server cursor ([PagingToken]) is opaque and not derivable from the last row, so it's held in
 * memory across APPENDs and reset on REFRESH. A fresh Pager therefore re-runs REFRESH from page 1
 * (default [initialize] behavior) — cheap for a typically-small blocklist, and Room still serves
 * cached rows instantly while it revalidates.
 */
@OptIn(ExperimentalPagingApi::class)
class BlocklistRemoteMediator(
    private val controller: BlocklistController,
    private val profileController: ProfileController,
    private val dataSource: BlockedUserDataSource,
) : RemoteMediator<Int, BlockedUserWithProfile>() {

    private var nextToken: PagingToken? = null

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, BlockedUserWithProfile>,
    ): MediatorResult {
        return try {
            val token = when (loadType) {
                LoadType.REFRESH -> {
                    nextToken = null
                    null
                }

                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
                LoadType.APPEND -> nextToken
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
            }

            val page = controller.getBlocklist(
                QueryOptions(limit = state.config.pageSize, token = token)
            ).getOrElse { return MediatorResult.Error(it) }

            withContext(Dispatchers.IO) {
                // Resolve display profiles for this page in parallel — the endpoint gives us only ids.
                val resolved = coroutineScope {
                    page.users.map { blocked ->
                        async {
                            val profile = profileController.getProfileForUser(blocked.userId).getOrNull()
                            ResolvedBlockedUser(blocked, profile)
                        }
                    }.awaitAll()
                }

                // REFRESH replaces the list atomically (single Room invalidation) so the UI never
                // sees an empty table between clearing old rows and inserting the fresh page.
                if (loadType == LoadType.REFRESH) {
                    dataSource.replaceAll(resolved)
                } else {
                    dataSource.upsert(resolved)
                }
            }

            nextToken = page.pagingToken
            MediatorResult.Success(endOfPaginationReached = !page.hasMore)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
