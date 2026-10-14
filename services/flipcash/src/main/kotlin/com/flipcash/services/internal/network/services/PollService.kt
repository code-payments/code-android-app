package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.poll.v1.Model
import com.codeinc.flipcash.gen.poll.v1.PollService
import com.flipcash.services.internal.network.api.PollApi
import com.flipcash.services.internal.network.extensions.toFlaggedCategory
import com.flipcash.services.models.CreatePollError
import com.flipcash.services.models.GetPollError
import com.flipcash.services.models.GetPollsError
import com.flipcash.services.models.VoteError
import com.flipcash.services.models.chat.ChatId
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.utils.toValidationOrElse
import javax.inject.Inject

internal class PollService @Inject constructor(
    private val api: PollApi,
) {
    suspend fun createPoll(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        definition: Model.PollDefinition,
        idempotencyKey: Model.IdempotencyKey,
    ): Result<Model.PollMetadata> {
        return runCatching {
            api.createPoll(owner, chatId, definition, idempotencyKey)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PollService.CreatePollResponse.Result.OK -> Result.success(response.poll)
                    PollService.CreatePollResponse.Result.DENIED -> Result.failure(CreatePollError.Denied())
                    PollService.CreatePollResponse.Result.CHAT_NOT_SUPPORTED -> Result.failure(CreatePollError.ChatNotSupported())
                    PollService.CreatePollResponse.Result.ENCRYPTION_NOT_ALLOWED -> Result.failure(CreatePollError.EncryptionNotAllowed())
                    PollService.CreatePollResponse.Result.ENCRYPTION_REQUIRED -> Result.failure(CreatePollError.EncryptionRequired())
                    PollService.CreatePollResponse.Result.OPTION_COUNT_MISMATCH -> Result.failure(CreatePollError.OptionCountMismatch())
                    PollService.CreatePollResponse.Result.INVALID_ENDS_AT -> Result.failure(CreatePollError.InvalidEndsAt())
                    PollService.CreatePollResponse.Result.INVALID_BALANCE_PARAMETERS -> Result.failure(CreatePollError.InvalidBalanceParameters())
                    PollService.CreatePollResponse.Result.TEXT_MODERATED -> Result.failure(CreatePollError.TextModerated(response.flaggedCategory.toFlaggedCategory()))
                    PollService.CreatePollResponse.Result.UNRECOGNIZED -> Result.failure(CreatePollError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { CreatePollError.Other(cause = it) })
            }
        )
    }

    suspend fun getPoll(owner: Ed25519.KeyPair, pollId: Model.PollId): Result<Model.PollMetadata> {
        return runCatching {
            api.getPoll(owner, pollId)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PollService.GetPollResponse.Result.OK -> Result.success(response.poll)
                    PollService.GetPollResponse.Result.DENIED -> Result.failure(GetPollError.Denied())
                    PollService.GetPollResponse.Result.NOT_FOUND -> Result.failure(GetPollError.NotFound())
                    PollService.GetPollResponse.Result.UNRECOGNIZED -> Result.failure(GetPollError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetPollError.Other(cause = it) })
            }
        )
    }

    suspend fun getPolls(owner: Ed25519.KeyPair, pollIds: List<Model.PollId>): Result<List<Model.PollMetadata>> {
        return runCatching {
            api.getPolls(owner, pollIds)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PollService.GetPollsResponse.Result.OK -> Result.success(response.pollsList)
                    PollService.GetPollsResponse.Result.DENIED -> Result.failure(GetPollsError.Denied())
                    PollService.GetPollsResponse.Result.UNRECOGNIZED -> Result.failure(GetPollsError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetPollsError.Other(cause = it) })
            }
        )
    }

    suspend fun vote(
        owner: Ed25519.KeyPair,
        pollId: Model.PollId,
        optionIndices: List<Int>,
    ): Result<Model.PollState> {
        return runCatching {
            api.vote(owner, pollId, optionIndices)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    PollService.VoteResponse.Result.OK -> Result.success(response.state)
                    PollService.VoteResponse.Result.DENIED -> Result.failure(VoteError.Denied())
                    PollService.VoteResponse.Result.NOT_FOUND -> Result.failure(VoteError.NotFound())
                    PollService.VoteResponse.Result.POLL_CLOSED -> Result.failure(VoteError.PollClosed())
                    PollService.VoteResponse.Result.INVALID_SELECTION -> Result.failure(VoteError.InvalidSelection())
                    PollService.VoteResponse.Result.UNRECOGNIZED -> Result.failure(VoteError.Unrecognized())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { VoteError.Other(cause = it) })
            }
        )
    }
}
