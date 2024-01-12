package com.getcode.network.client

import android.annotation.SuppressLint
import android.content.Context
import com.getcode.api.BuildConfig
import com.getcode.db.Database
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.manager.TopBarManager
import com.getcode.model.*
import com.getcode.model.intents.*
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.utils.catchSafely
import com.getcode.utils.flowInterval
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

fun Client.createAccounts(organizer: Organizer): Completable {
    return transactionRepository.createAccounts(organizer)
        .ignoreElement()
}

fun Client.transfer(
    context: Context,
    amount: KinAmount,
    fee: Kin = Kin.fromKin(0),
    organizer: Organizer,
    rendezvousKey: PublicKey,
    destination: PublicKey,
    isWithdrawal: Boolean
): Completable {
    return transferWithResult(
        context,
        amount,
        fee,
        organizer,
        rendezvousKey,
        destination,
        isWithdrawal
    ).flatMapCompletable {
        if (it.isSuccess) {
            Completable.complete()
        } else {
            Completable.error(it.exceptionOrNull() ?: Throwable("Failed to complete transfer"))
        }
    }
}

fun Client.transferWithResult(
    context: Context,
    amount: KinAmount,
    fee: Kin = Kin.fromKin(0),
    organizer: Organizer,
    rendezvousKey: PublicKey,
    destination: PublicKey,
    isWithdrawal: Boolean
): Single<Result<Unit>> {
    return getTransferPreflightAction(amount.kin)
        .andThen(Single.defer {
            transactionRepository.transfer(
                context, amount, fee, organizer, rendezvousKey, destination, isWithdrawal
            )
        })
        .map {
            if (it is IntentPrivateTransfer) {
                balanceController.setTray(organizer, it.resultTray)
            }
        }.map { Result.success(Unit) }
        .onErrorReturn { Result.failure(it) }
}

fun Client.sendRemotely(
    amount: KinAmount,
    rendezvousKey: PublicKey,
    giftCard: GiftCardAccount
): Completable {
    return Completable.defer {
        val organizer = SessionManager.getOrganizer()!!
        val truncatedAmount = amount.truncating()
        getTransferPreflightAction(truncatedAmount.kin)
            .andThen(
                sendRemotely(
                    context = context,
                    amount = truncatedAmount,
                    organizer = organizer,
                    rendezvousKey = rendezvousKey,
                    giftCard = giftCard
                )
                    .doOnComplete {
                        val giftCardItem = GiftCard(
                            key = giftCard.cluster.timelockAccounts.vault.publicKey.base58(),
                            entropy = giftCard.mnemonicPhrase.getBase58EncodedEntropy(context),
                            amount = truncatedAmount.kin.quarks,
                            date = System.currentTimeMillis()
                        )
                        Database.requireInstance().giftCardDao().insert(giftCardItem)
                    }
            )
    }
}

fun Client.receiveRemote(giftCard: GiftCardAccount): Single<KinAmount> {
    // Before we can receive from the gift card account
    // we have to determine the balance of the account
    return accountRepository.getTokenAccountInfos(giftCard.cluster.authority.keyPair)
        .flatMap { infos ->
            val info: AccountInfo = infos.values.firstOrNull()
                ?: return@flatMap Single.error(RemoteSendException.FailedToFetchGiftCardInfoException())
            val kinAmount = info.originalKinAmount
                ?: return@flatMap Single.error(RemoteSendException.GiftCardBalanceNotFoundException())

            if (info.claimState == AccountInfo.ClaimState.Claimed) {
                return@flatMap Single.error(RemoteSendException.GiftCardClaimedException())
            }

            if (info.claimState == AccountInfo.ClaimState.Claimed || info.claimState == AccountInfo.ClaimState.Unknown) {
                return@flatMap Single.error(RemoteSendException.GiftCardExpiredException())
            }

            val organizer = SessionManager.getOrganizer()!!

            transactionReceiver.receiveRemotely(
                giftCard = giftCard,
                amount = info.balance,
                organizer = organizer,
                isVoiding = false
            )
                .toSingleDefault(kinAmount)
        }
}

suspend fun Client.receiveRemoteSuspend(giftCard: GiftCardAccount): KinAmount =
    withContext(Dispatchers.IO) {
        // Before we can receive from the gift card account
        // we have to determine the balance of the account

        val information =
            accountRepository.getTokenAccountInfosSuspend(giftCard.cluster.authority.keyPair)

        val info: AccountInfo = information.values.firstOrNull()
            ?: throw RemoteSendException.FailedToFetchGiftCardInfoException()

        val kinAmount = info.originalKinAmount
            ?: throw RemoteSendException.GiftCardBalanceNotFoundException()

        if (info.claimState == AccountInfo.ClaimState.Claimed) {
            throw RemoteSendException.GiftCardClaimedException()
        }

        if (info.claimState == AccountInfo.ClaimState.Claimed || info.claimState == AccountInfo.ClaimState.Unknown) {
            throw RemoteSendException.GiftCardExpiredException()
        }

        val organizer = SessionManager.getOrganizer()!!

        transactionReceiver.receiveRemotelySuspend(
            giftCard = giftCard,
            amount = info.balance,
            organizer = organizer,
            isVoiding = false
        )

        balanceController.fetchBalanceSuspend()

        return@withContext kinAmount
    }

fun Client.cancelRemoteSend(
    giftCard: GiftCardAccount,
    amount: Kin,
    organizer: Organizer
) {
    transactionReceiver.receiveRemotely(
        amount = amount,
        organizer = organizer,
        giftCard = giftCard,
        isVoiding = true
    )
}


sealed class RemoteSendException : Exception() {
    class FailedToFetchGiftCardInfoException : RemoteSendException()
    class GiftCardBalanceNotFoundException : RemoteSendException()
    class GiftCardClaimedException : RemoteSendException()
    class GiftCardExpiredException : RemoteSendException()
}

fun Client.withdrawExternally(
    amount: KinAmount,
    organizer: Organizer,
    destination: PublicKey
): Completable {
    if (amount.kin.fractionalQuarks().quarks != 0L) {
        throw TransactionRepository.WithdrawException.InvalidFractionalKinAmountException()
    }

    if (amount.kin > organizer.availableBalance) {
        throw TransactionRepository.WithdrawException.InsufficientFundsException()
    }

    val intent = PublicKey.generate()

    val primaryBalance = organizer.availableDepositBalance.toKinTruncating()

    // If the primary account has less Kin than the amount
    // requested for withdrawal, we'll need to execute a
    // private transfer to the primary account before we
    // can make a public transfer to destination
    return if (primaryBalance < amount.kin) {
        val missingBalance = amount.kin - primaryBalance

        // It's possible that there's funds still left in
        // an incoming account. If the amount requested for
        // withdrawal is greater than primary + buckets, we
        // have to receive from incoming first
        if (missingBalance > organizer.slotsBalance) {
            transactionReceiver.receiveFromIncoming(
                amount = organizer.availableIncomingBalance.toKinTruncating(),
                organizer = organizer
            )
        } else {
            Completable.complete()
        }
            .concatWith(
                // Move funds into primary from buckets
                transfer(
                    context = context,
                    amount = KinAmount.newInstance(kin = missingBalance, rate = Rate.oneToOne),
                    organizer = organizer,
                    rendezvousKey = intent,
                    destination = organizer.primaryVault,
                    isWithdrawal = true
                )
            )
            .concatWith(balanceController.fetchBalance())
    } else {
        Completable.complete()
    }
        .concatWith(
            withdraw(
                amount = amount,
                organizer = organizer,
                destination = destination
            )
        )
}

private fun Client.withdraw(
    amount: KinAmount,
    organizer: Organizer,
    destination: PublicKey
): Completable {
    return Completable.defer {
        transactionRepository.withdraw(
            amount, organizer, destination
        )
            .map {
                if (it is IntentPublicTransfer) {
                    balanceController.setTray(organizer, it.resultTray)
                }
            }
            .ignoreElement()
    }
}

fun Client.sendRemotely(
    context: Context,
    amount: KinAmount,
    organizer: Organizer,
    rendezvousKey: PublicKey,
    giftCard: GiftCardAccount
): Completable {
    return Completable.defer {
        transactionRepository.sendRemotely(
            context, amount, organizer, rendezvousKey, giftCard
        )
            .map {
                if (it is IntentRemoteSend) {
                    balanceController.setTray(organizer, it.resultTray)
                }
            }
            .ignoreElement()
    }
}


fun Client.requestFirstKinAirdrop(
    owner: KeyPair,
): Single<KinAmount> {
    return transactionRepository.requestFirstKinAirdrop(owner)
}

fun Client.pollIntentMetadata(
    owner: KeyPair,
    intentId: PublicKey,
    maxAttempts: Int = 50,
    debugLogs: Boolean = false,
): Flow<IntentMetadata> {
    val stopped = AtomicBoolean()
    val attemptCount = AtomicInteger()

    if (debugLogs) {
        Timber.tag("codescan").i("pollIntentMetadata: start polling")
    }

    return flowInterval({ 50L * (attemptCount.get() / 10) })
        .takeWhile { !stopped.get() && attemptCount.get() < maxAttempts }
        .map { attemptCount.incrementAndGet() }
        .onEach {
            if (debugLogs) {
                Timber.tag("codescan").i("pollIntentMetadata: [${it}] fetch data")
            }
        }
        .map { transactionRepository.fetchIntentMetadata(owner, intentId) }
        .filter { !stopped.get() }
        .mapNotNull { it.getOrNull() }
        .map {
            if (debugLogs) {
                Timber.tag("codescan")
                    .i("pollMatchingRendezvous: stop polling :: took ${attemptCount.get()} attempts")
            }
            stopped.set(true)
            it
        }
}

fun Client.fetchTransactionLimits(
    owner: KeyPair,
    isForce: Boolean = false
): Flowable<Map<String, SendLimit>> {
    val time = System.currentTimeMillis()

    val isStale = time - lastLimitsFetch > 1000 * 60 * 60 // Older than 1 hour

    if (!isStale && !isForce) {
        val sendLimitCopy = transactionRepository.sendLimit.toList()
        return Flowable.just(sendLimitCopy)
            .map { it.associateBy { i -> i.id } }
            .distinctUntilChanged()
    }

    Timber.i("fetchTransactionLimits")
    lastLimitsFetch = time

    val date: Calendar = GregorianCalendar()
    date.set(Calendar.HOUR_OF_DAY, 0)
    date.set(Calendar.MINUTE, 0)
    date.set(Calendar.SECOND, 0)
    date.set(Calendar.MILLISECOND, 0)

    val seconds = date.timeInMillis / 1000
    return transactionRepository.fetchTransactionLimits(owner, seconds)
}

fun Client.historicalTransactions() = transactionRepository.transactionCache.toList()
    .sortedByDescending { it.date }

@OptIn(ExperimentalCoroutinesApi::class)
fun Client.observeTransactions(
    owner: KeyPair,
): Flow<List<HistoricalTransaction>> {
    return flowOf(historicalTransactions())
        .flatMapConcat { initialList ->
            fetchPaymentHistoryDelta(owner, initialList.firstOrNull()?.id?.toByteArray())
                .toObservable().asFlow()
                .scan(initialList) { previous, update ->
                    previous
                        .filterNot { update.contains(it) }
                        .plus(update)
                        .sortedByDescending { it.date }
                }
        }
}

fun Client.fetchPaymentHistoryDelta(
    owner: KeyPair,
    afterId: ByteArray? = null
): Single<List<HistoricalTransaction>> {
    return transactionRepository.fetchPaymentHistoryDelta(owner, afterId)
}

fun Client.fetchDestinationMetadata(destination: PublicKey): Single<TransactionRepository.DestinationMetadata> {
    return transactionRepository.fetchDestinationMetadata(destination)
}

// -----
private var lastLimitsFetch: Long = 0L
private var lastReceive: Long = 0L

fun Client.fetchLimits(isForce: Boolean = false): Completable {
    val owner = SessionManager.getKeyPair() ?: return Completable.complete()
    if (!Database.isOpen()) return Completable.complete()
    return fetchTransactionLimits(owner, isForce).ignoreElements()
}

fun Client.receiveIfNeeded(): Completable {
    val organizer = SessionManager.getOrganizer() ?: return Completable.complete()

    return Completable.concatArray(
        receiveFromPrimaryIfWithinLimits(organizer),
        transactionReceiver.receiveFromIncomingIfRotationRequired(organizer)
    )
}

fun Client.receiveFromPrimaryIfWithinLimits(organizer: Organizer): Completable {
    val depositBalance = organizer.availableDepositBalance.toKinTruncating()

    // Nothing to deposit
    if (!depositBalance.hasWholeKin()) return Completable.complete()

    // We want to deposit the smaller of the two: balance in the
    // primary account or the max allowed amount provided by server
    return Single.just(transactionRepository.maxDeposit)
        .map { maxDeposit ->
            Pair(
                Kin.fromKin(min(depositBalance.toKinValueDouble(), maxDeposit.toDouble())),
                Kin.fromKin(maxDeposit)
            )
        }
        .filter { pair ->
            val (depositAmount, _) = pair
            depositAmount.hasWholeKin()
        }
        .flatMapSingle { pair ->
            val (depositAmount, maxDeposit) = pair
            Timber.i(
                "Receiving from primary: ${depositAmount.toKin()}, Max allowed deposit: ${maxDeposit.toKin()}"
            )
            transactionRepository.receiveFromPrimary(depositAmount, organizer)
        }
        .map { intent ->
            if (intent is IntentDeposit) {
                balanceController.setTray(organizer, intent.resultTray)
            }
        }
        .ignoreElement()
        .andThen { fetchLimits(true) }
}

fun Client.fetchPrivacyUpgrades(): Completable {
    val owner = SessionManager.getKeyPair() ?: return Completable.complete()
    val organizer = SessionManager.getOrganizer() ?: return Completable.complete()

    return transactionRepository.fetchUpgradeableIntents(owner)
        .flatMapCompletable { intents ->
            Timber.w("Fetch Privacy size: ${intents.size}")
            val completableList = mutableListOf<Completable>()

            intents.forEachIndexed { index, intent ->
                val completable =
                    transactionRepository.upgradePrivacy(
                        organizer.context,
                        organizer.mnemonic,
                        intent
                    )
                        .doOnSuccess {
                            analyticsManager.upgradePrivacy(
                                successful = true,
                                intentId = intent.id,
                                actionCount = intent.actions.size
                            )
                            Timber.i("Privacy Upgrade - success")

                            if (BuildConfig.DEBUG) {
                                TopBarManager.showMessage(
                                    "Privacy Upgrade",
                                    "Success. Index: $index, Count: ${intents.size}",
                                    TopBarManager.TopBarMessageType.NOTIFICATION
                                )
                            }
                        }
                        .doOnError {
                            analyticsManager.upgradePrivacy(
                                successful = false,
                                intentId = intent.id,
                                actionCount = intent.actions.size
                            )
                            Timber.i("Privacy Upgrade - failure")
                            if (BuildConfig.DEBUG) {
                                TopBarManager.showMessage("Privacy Upgrade", "Failure")
                            }
                        }
                        .ignoreElement()

                completableList.add(completable)
            }

            Completable.mergeArray(*completableList.toTypedArray())
        }
}

fun Client.getTransferPreflightAction(amount: Kin): Completable {
    val organizer = SessionManager.getOrganizer() ?: return Completable.complete()
    val neededKin = if (amount > organizer.slotsBalance) amount - organizer.slotsBalance else Kin.fromKin(0)

    // If the there's insufficient funds in the slots
    // we'll need to top them up from incoming, relationship
    // and primary accounts, in that order.
    return if (neededKin > 0) {
        // 1. Receive funds from incoming accounts as those
        // will rotate more frequently than other types of accounts
        val receivedKin = transactionReceiver.receiveFromIncoming(organizer)

        // 2. Pull funds from relationships if there's still funds
        // missing in buckets after the receiving from primary
        if (receivedKin < neededKin) {
            transactionReceiver.receiveFromRelationship(organizer, limit = neededKin - receivedKin)
        }

        // 3. If the amount is still larger than what's available
        // in the slots, we'll need to move funds from primary
        // deposits into slots after receiving
        if (amount > organizer.slotsBalance) {
            receiveFromPrimaryIfWithinLimits(organizer)
        } else {
            Completable.complete()
        }
    } else {
        Completable.complete()
    }
}

fun Client.receiveFromRelationships(domain: Domain, amount: Kin, organizer: Organizer) {
    transactionRepository.receiveFromRelationship(domain, amount, organizer)
}

@SuppressLint("CheckResult")
@Throws
fun Client.establishRelationship(organizer: Organizer, domain: Domain) {
    transactionRepository.establishRelationship(organizer, domain).ignoreElement()
}
