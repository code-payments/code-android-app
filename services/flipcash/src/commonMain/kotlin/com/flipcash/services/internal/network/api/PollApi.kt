package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.poll.v1.Model
import com.codeinc.flipcash.gen.poll.v1.PollGrpcKt
import com.codeinc.flipcash.gen.poll.v1.PollService
import com.codeinc.flipcash.gen.poll.v1.validate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.network.extensions.asChatId
import com.flipcash.services.internal.network.extensions.authenticate
import com.flipcash.services.models.chat.ChatId
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.internal.network.core.GrpcApi
import dev.bmcreations.protovalidate.orThrow
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PollApi @Inject constructor(
    @FlipcashManagedChannel
    managedChannel: ManagedChannel,
) : GrpcApi(managedChannel) {
    private val api = PollGrpcKt.PollCoroutineStub(managedChannel)
        .withWaitForReady()

    suspend fun createPoll(
        owner: Ed25519.KeyPair,
        chatId: ChatId,
        definition: Model.PollDefinition,
        idempotencyKey: Model.IdempotencyKey,
    ): PollService.CreatePollResponse {
        val request = PollService.CreatePollRequest.newBuilder()
            .setChatId(chatId.asChatId())
            .setDefinition(definition)
            .setIdempotencyKey(idempotencyKey)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.createPoll(request)
        }
    }

    suspend fun getPoll(owner: Ed25519.KeyPair, pollId: Model.PollId): PollService.GetPollResponse {
        val request = PollService.GetPollRequest.newBuilder()
            .setPollId(pollId)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getPoll(request)
        }
    }

    suspend fun getPolls(owner: Ed25519.KeyPair, pollIds: List<Model.PollId>): PollService.GetPollsResponse {
        val request = PollService.GetPollsRequest.newBuilder()
            .addAllPollIds(pollIds)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.getPolls(request)
        }
    }

    suspend fun vote(
        owner: Ed25519.KeyPair,
        pollId: Model.PollId,
        optionIndices: List<Int>,
    ): PollService.VoteResponse {
        val request = PollService.VoteRequest.newBuilder()
            .setPollId(pollId)
            .addAllOptionIndices(optionIndices)
            .apply { setAuth(authenticate(owner)) }
            .build()

        request.validate().orThrow()

        return withContext(Dispatchers.IO) {
            api.vote(request)
        }
    }
}
