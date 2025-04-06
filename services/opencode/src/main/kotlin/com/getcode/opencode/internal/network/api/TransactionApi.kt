package com.getcode.opencode.internal.network.api

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.transaction.v2.TransactionGrpc
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.TransactionService.AirdropRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.AirdropResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.CanWithdrawToAccountRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.CanWithdrawToAccountResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.DeclareFiatOnrampPurchaseAttemptRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.DeclareFiatOnrampPurchaseAttemptResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetIntentMetadataRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetIntentMetadataResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetLimitsRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.GetLimitsResponse
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentRequest
import com.codeinc.opencode.gen.transaction.v2.TransactionService.SubmitIntentResponse
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.network.core.GrpcApi
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.toIntentId
import com.getcode.opencode.internal.network.extensions.toProtobufExchangeData
import com.getcode.opencode.internal.network.extensions.toProtobufTimestamp
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.core.bytes
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.utils.toByteString
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject

class TransactionApi @Inject constructor(
    @OpenCodeManagedChannel
    managedChannel: ManagedChannel,
): GrpcApi(managedChannel) {

    private val api = TransactionGrpc.newStub(managedChannel).withWaitForReady()

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
        observer: StreamObserver<SubmitIntentResponse>
    ): StreamObserver<SubmitIntentRequest> {
        return api.submitIntent(observer)
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
    fun getIntentMetadata(
        intentId: ID,
        owner: KeyPair,
    ): Flow<GetIntentMetadataResponse> {
        val request = GetIntentMetadataRequest.newBuilder()
            .setIntentId(intentId.toIntentId())
            .setOwner(owner.asSolanaAccountId())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getIntentMetadata
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
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
    fun getLimits(
        owner: KeyPair,
        consumedSince: Long,
    ): Flow<GetLimitsResponse> {
        val request = GetLimitsRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .setConsumedSince(consumedSince.toProtobufTimestamp())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getLimits
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Provides hints to clients for submitting withdraw intents.
     * The RPC indicates if a withdrawal is possible, and how it should be performed.
     */
    fun canWithdrawToAccount(
        account: KeyPair
    ): Flow<CanWithdrawToAccountResponse> {
        val request = CanWithdrawToAccountRequest.newBuilder()
            .setAccount(account.asSolanaAccountId())
            .build()

        return api::canWithdrawToAccount
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Airdrops Kin to the requesting account
     *
     * @param type The type of airdrop to claim
     * @param destination The owner account to airdrop Kin to
     */
    fun airdrop(
        type: AirdropType,
        destination: KeyPair,
    ): Flow<AirdropResponse> {
        val request = AirdropRequest.newBuilder()
            .setAirdropType(TransactionService.AirdropType.forNumber(type.ordinal))
            .setOwner(destination.asSolanaAccountId())
            .apply { setSignature(sign(destination)) }
            .build()

        return api::airdrop
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Performs an on-chain swap. The high-level flow mirrors [submitIntent] closely. However,
     * due to the time-sensitive nature and unreliability of swaps, they do not fit within the
     * broader intent system. This results in a few key differences:
     *
     * - Transactions are submitted on a best-effort basis outside of the Code Sequencer within
     * the RPC handler
     * - Balance changes are applied after the transaction has finalized
     * - Transactions use recent blockhashes over a nonce
     *
     * [submitIntent] also operates on VM virtual instructions, whereas [swap] uses Solana transactions.
     *
     * The transaction will have the following instruction format:
     * 1. ComputeBudget::SetComputeUnitLimit
     * 2. ComputeBudget::SetComputeUnitPrice
     * 3. SwapValidator::PreSwap
     * 4. Dynamic swap instruction
     * 5. SwapValidator::PostSwap
     *
     * > NOTE: Currently limited to swapping USDC to Kin.
     * > Kin is deposited into the token account derived from the VM deposit PDA of the owner account.
     *
     */
    fun swap(
        observer: StreamObserver<TransactionService.SwapResponse>
    ): StreamObserver<TransactionService.SwapRequest> {
        return api.swap(observer)
    }

    /**
     * Called whenever a user attempts to use a fiat
     * onramp to purchase crypto for use in Code.
     *
     * @param owner The owner account invoking the buy module
     * @param purchaseAmount The amount being purchased
     * @param nonce A nonce value unique to the purchase. If it's included in a memo for the
     * transaction for the deposit to the owner, then purchase_amount will be used
     * for display values. Otherwise, the amount will be inferred from the transaction.
     */
    fun declareFiatOnrampPurchaseAttempt(
        owner: KeyPair,
        purchaseAmount: ExchangeData.WithoutRate,
        nonce: UUID
    ): Flow<DeclareFiatOnrampPurchaseAttemptResponse> {
        val request = DeclareFiatOnrampPurchaseAttemptRequest.newBuilder()
            .setOwner(owner.asSolanaAccountId())
            .setPurchaseAmount(purchaseAmount.toProtobufExchangeData())
            .setNonce(Model.UUID.newBuilder().setValue(nonce.bytes.toByteString()))
            .apply { setSignature(sign(owner)) }
            .build()

        return api::declareFiatOnrampPurchaseAttempt
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }
}