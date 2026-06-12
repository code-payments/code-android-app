package com.flipcash.app.onramp

import com.coinbase.onramp.api.CoinbaseApi
import com.flipcash.services.user.UserManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuyOptionsCache @Inject constructor(
    private val api: CoinbaseApi,
    private val jwtExecutor: CoinbaseJwtExecutor,
    private val userManager: UserManager,
) {
    private val cache = mutableMapOf<String, Set<BuyOptionsMint>>()
    private val mutex = Mutex()

    fun getCached(region: PhoneRegion): Set<BuyOptionsMint>? = cache[region.cacheKey]

    suspend fun prefetchForCurrentUser(): Set<BuyOptionsMint>? {
        val phone = userManager.profile?.verifiedPhoneNumber ?: return null
        val region = regionFromPhone(phone) ?: return null
        return prefetch(region)
    }

    fun isUsdfAvailable(region: PhoneRegion): Boolean {
        val mints = cache[region.cacheKey] ?: return true // default to true on cache miss
        return BuyOptionsMint.USDF in mints
    }

    suspend fun prefetch(region: PhoneRegion): Set<BuyOptionsMint>? {
        cache[region.cacheKey]?.let { return it }

        return mutex.withLock {
            // double-check after acquiring lock
            cache[region.cacheKey]?.let { return it }

            requestAndCache(region)
        }
    }

    private suspend fun requestAndCache(region: PhoneRegion): Set<BuyOptionsMint>? {
        val host = "api.developer.coinbase.com/"
        val path = "onramp/v1/buy/options"
        val response = jwtExecutor.execute(
            scheme = "https",
            host = host,
            path = path,
            method = "GET",
        ) { jwt ->
            runCatching {
                api.getBuyOptions(
                    url = "https://$host$path",
                    jwt = "Bearer $jwt",
                    country = region.country,
                    subdivision = region.subdivision,
                )
            }
        }.getOrNull() ?: return null

        val mints = parseMints(response)
        cache[region.cacheKey] = mints
        return mints
    }

    private fun parseMints(response: JsonObject): Set<BuyOptionsMint> {
        return response["purchase_currencies"]
            ?.jsonArray
            ?.mapNotNull { element ->
                element.jsonObject["symbol"]?.jsonPrimitive?.content?.let { BuyOptionsMint(it) }
            }
            ?.toSet()
            ?: emptySet()
    }
}
