package com.flipcash.app.core.cache

sealed interface CachePolicy {
    data object CacheOnly: CachePolicy
    data object CacheFirst: CachePolicy
    data object NetworkOnly: CachePolicy
    data object NetworkFirst: CachePolicy
}