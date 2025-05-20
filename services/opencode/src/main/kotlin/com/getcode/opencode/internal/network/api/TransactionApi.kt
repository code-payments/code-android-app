package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.transaction.v2.TransactionGrpcKt
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.TransactionService.AirdropRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.AirdropResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.CanWithdrawToAccountRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.CanWithdrawToAccountResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetIntentMetadataRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetIntentMetadataResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetLimitsRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetLimitsResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.VoidGiftCardRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.VoidGiftCardResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asIntentId
import com.getcode.opencode.internal.network.extensions.asProtobufTimestamp
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.solana.keys.PublicKey
import io.grpc.ManagedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TransactionApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = TransactionGrpcKt.TransactionCoroutineStub(managedChannel).withWaitForReady()

    /**
     * The mechanism for client and server to agree upon a set of
     * client actions to execute on the blockchain using the Code sequencer for
     * fulfillment.
     *
     * Transactions and virtual instructions are never exchanged between client and server.
     * Instead, the required accounts and arguments for instructions known to each actor are
     * exchanged to allow independent and local construction.
     *
     * Client and server are expected to fully validate the intent. Proofs will
     * be provided for any parameter requiring one. Signatures should only be
     * generated after approval.
     *
     * This RPC is not a traditional streaming endpoint. It bundles two unary calls
     * to enable DB-level transaction semantics.
     *
     * The high-level happy path flow for the RPC is as follows:
     * 1. Client initiates a stream and sends SubmitIntentRequest.SubmitActions
     * 2. Server validates the intent, its actions and metadata
     * 3a. If there are transactions or virtual instructions requiring the user's signature,
     * then server returns SubmitIntentResponse.ServerParameters
     * 3b. Otherwise, server returns SubmitIntentResponse.Success and closes the stream
     * 4. For each transaction or virtual instruction requiring the user's signature, the client
     * locally constructs it, performs validation and collects the signature
     * 5. Client sends SubmitIntentRequest.SubmitSignatures with the signature
     * list generated from 4
     * 6. Server validates all signatures are submitted and are the expected values
     * using locally constructed transactions or virtual instructions.
     * 7.  Server returns SubmitIntentResponse.Success and closes the stream
     *
     * In the error case:
     * * Server will return SubmitIntentResponse.Error and close the stream
     * * Client will close the stream
     */
    fun submitIntent(
        requestFlow: Flow<SubmitIntentRequest>,
    ): Flow<SubmitIntentResponse> {
        return api.submitIntent(requestFlow)
    }

    /**
     * Gets basic metadata on an intent. It can also be used
     * to fetch the status of submitted intents. Metadata exists only for intents
     * that have been successfully submitted.
     *
     * @param intentId The intent ID to query
     * @param owner The verified owner account public key when not signing with the rendezvous
     * key. Only owner accounts involved in the intent can access the metadata.
     */
    suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair,
    ): GetIntentMetadataResponse {
        val request = GetIntentMetadataRequest.newBuilder()
            .setIntentId(intentId.asIntentId())
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getIntentMetadata(request)
        }
    }

    /**
     * Gets limits for money moving intents for an owner account in an
     * identity-aware manner
     *
     * @param owner The owner account whose limits will be calculated. Any other owner accounts
     * linked with the same identity of the owner will also be applied.
     * @param consumedSince All transactions starting at this time will be incorporated into the consumed
     * limit calculation. Clients should set this to the start of the current day in
     * the client's current time zone (because server has no knowledge of this atm).
     */
    suspend fun getLimits(
        owner: KeyPair,
        consumedSince: Long,
    ): GetLimitsResponse {
        val request = GetLimitsRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .setConsumedSince(consumedSince.asProtobufTimestamp())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.getLimits(request)
        }
    }

    /**
     * Provides hints to clients for submitting withdraw intents.
     * The RPC indicates if a withdrawal is possible, and how it should be performed.
     */
    suspend fun canWithdrawToAccount(
        destination: PublicKey,
    ): CanWithdrawToAccountResponse {
        val request = CanWithdrawToAccountRequest.newBuilder()
            .setAccount(destination.asSolanaAccountId())
            .build()

        return withContext(Dispatchers.IO) {
            api.canWithdrawToAccount(request)
        }
    }

    /**
     * Airdrops Kin to the requesting account
     *
     * @param type The type of airdrop to claim
     * @param destination The owner account to airdrop Kin to
     */
    suspend fun airdrop(
        type: AirdropType,
        destination: KeyPair,
    ): AirdropResponse {
        val request = AirdropRequest.newBuilder()
            .setAirdropType(TransactionService.AirdropType.forNumber(type.ordinal))
            .setOwner(destination.asSolanaAccountId())
            .apply { setSignature(sign(destination)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.airdrop(request)
        }
    }

    /**
     * Voids a gift card account by returning the funds to the funds back
     * to the issuer via the auto-return action if it hasn't been claimed or already
     * returned.
     *
     * NOTE: The RPC is idempotent. If the user already claimed/voided the gift card, or
     * it is close to or is auto-returned, then OK will be returned.
     */
    suspend fun voidGiftCard(
        owner: KeyPair,
        giftCardVault: PublicKey,
    ): VoidGiftCardResponse {
        val request = VoidGiftCardRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .setGiftCardVault(giftCardVault.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return withContext(Dispatchers.IO) {
            api.voidGiftCard(request)
        }
    }
}