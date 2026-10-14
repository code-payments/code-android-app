package com.flipcash.services.controllers

import com.codeinc.flipcash.gen.poll.v1.Model
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.repository.PollRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject

class PollController @Inject constructor(
    private val repository: PollRepository,
    private val userManager: UserManager,
) {
    suspend fun createPoll(
        chatId: ChatId,
        definition: Model.PollDefinition,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PollMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.createPoll(owner, chatId, definition, idempotencyKey)
    }

    suspend fun getPoll(pollId: Model.PollId): Result<Model.PollMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.getPoll(owner, pollId)
    }

    suspend fun getPolls(pollIds: List<Model.PollId>): Result<List<Model.PollMetadata>> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.getPolls(owner, pollIds)
    }

    suspend fun vote(pollId: Model.PollId, optionIndices: List<Int>): Result<Model.PollState> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))
        return repository.vote(owner, pollId, optionIndices)
    }
}
