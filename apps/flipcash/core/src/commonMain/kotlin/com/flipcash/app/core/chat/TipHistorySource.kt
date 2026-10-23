package com.flipcash.app.core.chat

import kotlinx.coroutines.flow.Flow

/**
 * Whether the signed-in account has ever sent a tip, read off the local chat cache.
 *
 * Emits `null` until that cache is known to be complete, since an absent TIPPED message proves
 * nothing before then. Split out of the chat coordinator so callers that need only this answer
 * (onboarding on the wallet tab) don't depend on the whole chat module.
 */
interface TipHistorySource {
    fun hasEverTipped(): Flow<Boolean?>
}
