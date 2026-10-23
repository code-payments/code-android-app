package com.flipcash.services.repository

import com.codeinc.flipcash.gen.poll.v1.Model
import com.flipcash.services.models.chat.ChatId
import com.getcode.ed25519kmp.Ed25519.KeyPair

/**
 * Polls in group chats. Poll values are the generated `poll.v1` types for now; a domain model
 * is deferred until a UI consumes them.
 */
interface PollRepository {
    suspend fun createPoll(
        owner: KeyPair,
        chatId: ChatId,
        definition: Model.PollDefinition,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PollMetadata>

    suspend fun getPoll(owner: KeyPair, pollId: Model.PollId): Result<Model.PollMetadata>

    suspend fun getPolls(owner: KeyPair, pollIds: List<Model.PollId>): Result<List<Model.PollMetadata>>

    suspend fun vote(owner: KeyPair, pollId: Model.PollId, optionIndices: List<Int>): Result<Model.PollState>
}
