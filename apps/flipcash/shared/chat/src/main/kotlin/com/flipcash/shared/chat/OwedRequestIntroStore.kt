package com.flipcash.shared.chat

import com.getcode.opencode.model.core.ID

/**
 * The owed requests whose chat has already opened its deck by itself on this device, per signed-in
 * account, so each does it once.
 */
interface OwedRequestIntroStore {
    /** The current account's shown requests; empty before sign-in. */
    suspend fun shown(): Set<ID>

    suspend fun markShown(ids: Collection<ID>)
}
