package com.getcode.libs.opengraph.cache

import com.getcode.libs.opengraph.model.OpenGraphResult

interface CacheProvider {
    suspend fun get(url: String): OpenGraphResult?
    suspend fun set(openGraphResult: OpenGraphResult, url: String)
}