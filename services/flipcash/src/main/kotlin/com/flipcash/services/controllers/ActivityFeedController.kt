package com.flipcash.services.controllers

import com.flipcash.services.models.ActivityFeedMessage
import com.flipcash.services.models.ActivityFeedType
import com.flipcash.services.repository.ActivityFeedRepository
import com.flipcash.services.user.UserManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

class ActivityFeedController @Inject constructor(
    private val repository: ActivityFeedRepository,
    private val userManager: UserManager,
) {
    // Cache entry to store messages and their timestamp
    private data class CacheEntry(
        val messages: List<ActivityFeedMessage>,
        val timestamp: TimeSource.Monotonic.ValueTimeMark,
    )

    // Thread-safe cache
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val mutex = Mutex() // For safe cache updates
    private val cacheTTL = 1.hours // Time-to-live for cache entries

    suspend fun getLatestMessagesFor(type: ActivityFeedType, maxItems: Int = -1): Result<List<ActivityFeedMessage>> {
        // Generate cache key based on type and maxItems
        val cacheKey = "${type.name}_$maxItems"

        // Check cache
        mutex.withLock {
            val cached = cache[cacheKey]
            if (cached != null && !isCacheExpired(cached)) {
                return Result.success(cached.messages)
            }
        }

        // Cache miss or expired: fetch from repository
        return fetchAndCacheMessages(type, maxItems, cacheKey)
    }

    // New method to refresh cache after an event
    suspend fun refreshAfterEvent(type: ActivityFeedType, maxItems: Int = -1): Result<List<ActivityFeedMessage>> {
        // Generate cache key
        val cacheKey = "${type.name}_$maxItems"

        // Invalidate cache for this type and maxItems
        mutex.withLock {
            cache.remove(cacheKey)
        }

        // Fetch fresh data
        return fetchAndCacheMessages(type, maxItems, cacheKey)
    }

    // Helper to fetch messages and update cache
    private suspend fun fetchAndCacheMessages(
        type: ActivityFeedType,
        maxItems: Int,
        cacheKey: String
    ): Result<List<ActivityFeedMessage>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getLatestNotifications(owner, type, maxItems).also { result ->
            // On success, update cache
            result.onSuccess { messages ->
                mutex.withLock {
                    cache[cacheKey] = CacheEntry(messages, TimeSource.Monotonic.markNow())
                }
            }
        }
    }

    // Check if cache entry is expired
    private fun isCacheExpired(entry: CacheEntry): Boolean {
        return entry.timestamp.elapsedNow() > cacheTTL
    }

    // Clear entire cache (e.g., for testing or manual invalidation)
    suspend fun clearCache() {
        mutex.withLock {
            cache.clear()
        }
    }
}