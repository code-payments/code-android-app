package com.flipcash.services.internal.repositories

import com.codeinc.flipcash.gen.poll.v1.Model
import com.flipcash.services.internal.network.services.PollService
import com.flipcash.services.models.CreatePollError
import com.flipcash.services.models.GetPollError
import com.flipcash.services.models.VoteError
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.repository.PollRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.utils.ErrorUtils

internal class InternalPollRepository(
    private val service: PollService,
) : PollRepository {
    override suspend fun createPoll(
        owner: KeyPair,
        chatId: ChatId,
        definition: Model.PollDefinition,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PollMetadata> {
        return service.createPoll(owner, chatId, definition, idempotencyKey)
            .onFailure {
                if (it !is CreatePollError.Denied && it !is CreatePollError.TextModerated) ErrorUtils.handleError(it)
            }
    }

    override suspend fun getPoll(owner: KeyPair, pollId: Model.PollId): Result<Model.PollMetadata> {
        return service.getPoll(owner, pollId)
            .onFailure { if (it !is GetPollError.NotFound) ErrorUtils.handleError(it) }
    }

    override suspend fun getPolls(owner: KeyPair, pollIds: List<Model.PollId>): Result<List<Model.PollMetadata>> {
        return service.getPolls(owner, pollIds)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun vote(owner: KeyPair, pollId: Model.PollId, optionIndices: List<Int>): Result<Model.PollState> {
        return service.vote(owner, pollId, optionIndices)
            .onFailure {
                if (it !is VoteError.PollClosed && it !is VoteError.NotFound) ErrorUtils.handleError(it)
            }
    }
}
