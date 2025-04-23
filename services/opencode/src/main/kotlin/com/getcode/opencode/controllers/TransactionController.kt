package com.getcode.opencode.controllers

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.opencode.events.Events
import com.getcode.opencode.internal.network.api.intents.IntentCancelRemoteSend
import com.getcode.opencode.internal.network.api.intents.IntentRemoteSend
import com.getcode.opencode.internal.network.api.intents.IntentTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.Limits
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.AirdropType
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.repositories.TransactionRepository
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.flowInterval
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.hoc081098.channeleventbus.ChannelEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.datetime.Clock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionController @Inject constructor(
    private val repository: TransactionRepository,
    private val eventBus: ChannelEventBus,
) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _limits = MutableStateFlow<Limits?>(Limits.Empty)
    val limits: StateFlow<Limits?>
        get() = _limits.asStateFlow()

    val areLimitsStale: Boolean
        get() = _limits.value == null || _limits.value?.isStale == true

    suspend fun updateLimits(owner: AccountCluster, force: Boolean = false) {
        if (areLimitsStale || force) {
            val since = Clock.System.now()
            trace(
                tag = "TRX",
                message = "updating limits from $since",
                type = TraceType.Process
            )
            repository.getLimits(
                owner = owner.authority.keyPair,
                consumedSince = since
            ).onSuccess {
                _limits.value = it
            }.onFailure {
                trace(
                    tag = "TRX",
                    message = "Failed to update limits",
                    error = it
                )
            }
        }
    }

    suspend fun airdrop(
        destination: KeyPair,
        type: AirdropType
    ): Result<LocalFiat> {
        return repository.airdrop(
            type = type,
            destination = destination
        ).onSuccess {
            trace(
                tag = "TRX",
                message = "Airdrop was successful.",
                type = TraceType.Process
            )
            eventBus.send(Events.FetchBalance())
        }.map { LocalFiat(it) }
    }

    suspend fun transfer(
        amount: LocalFiat,
        owner: AccountCluster,
        destination: PublicKey,
        rendezvous: PublicKey,
        scope: CoroutineScope = this.scope,
    ): Result<IntentType> {
        val intent = IntentTransfer.create(
            amount = amount,
            sourceCluster = owner,
            destination = destination,
            rendezvous = rendezvous,

        )

        return submitIntent(scope, intent, owner.authority.keyPair)
    }

    suspend fun remoteSend(
        amount: LocalFiat,
        owner: AccountCluster,
        rendezvous: PublicKey,
        scope: CoroutineScope = this.scope,
    ): Result<IntentType> {
        val giftCard = GiftCardAccount.create()
        val intent = IntentRemoteSend.create(
            amount = amount,
            sourceCluster = owner,
            giftCard = giftCard,
            rendezvous = rendezvous
        )

        return submitIntent(scope, intent, owner.authority.keyPair)
    }

    suspend fun cancelRemoteSend(
        amount: LocalFiat,
        giftCard: GiftCardAccount,
        owner: AccountCluster,
        scope: CoroutineScope = this.scope,
    ): Result<IntentType> {
        val intent = IntentCancelRemoteSend.create(giftCard, amount, owner)

        return submitIntent(scope, intent, owner.authority.keyPair)
    }

    internal suspend fun submitIntent(
        scope: CoroutineScope,
        intent: IntentType,
        owner: KeyPair,
    ): Result<IntentType> = repository.submitIntent(scope, intent, owner)

    suspend fun getIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair,
    ): Result<TransactionMetadata> = repository.getIntentMetadata(intentId, owner)

    suspend fun <T: TransactionMetadata> pollIntentMetadata(
        intentId: PublicKey,
        owner: KeyPair,
        maxAttempts: Int = 10,
        debugLogs: Boolean = false,
    ): Result<T> {
        val stopped = AtomicBoolean()
        val attemptCount = AtomicInteger()

        if (debugLogs) {
            trace(
                tag = "opencodescan",
                message = "pollIntentMetadata: start",
                type = TraceType.Process
            )
        }

        return flowInterval({ 50L * (attemptCount.get() / 10) })
            .takeWhile { !stopped.get() && attemptCount.get() < maxAttempts }
            .map { attemptCount.incrementAndGet() }
            .onEach {
                if (debugLogs) {
                    trace(
                        tag = "opencodescan",
                        message = "pollIntentMetadata: [$it] fetch data",
                        type = TraceType.Process
                    )
                }
            }
            .map {
                try {
                    val result = repository.getIntentMetadata(intentId, owner)
                    Result.success(result.getOrNull() ?: throw IllegalStateException("No metadata received"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            .filter { !stopped.get() }
            .mapNotNull { result ->
                result.fold(
                    onSuccess = { metadata ->
                        if (debugLogs) {
                            trace(
                                tag = "opencodescan",
                                message = "pollIntentMetadata: took ${attemptCount.get()} attempts",
                                type = TraceType.Process
                            )
                        }
                        metadata
                    },
                    onFailure = { null }
                )
            }.mapNotNull { it as? T }
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
            .firstOrNull() ?: Result.failure(IllegalStateException("Never received the desired metadata"))
    }
}