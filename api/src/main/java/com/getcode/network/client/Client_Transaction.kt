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
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
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

@SuppressLint("CheckResult")
suspend fun Client.cancelRemoteSend(
    giftCard: GiftCardAccount,
    amount: Kin,
    organizer: Organizer
): Double {
    transactionReceiver.receiveRemotely(
        amount = amount,
        organizer = organizer,
        giftCard = giftCard,
        isVoiding = true
    ).blockingAwait()

    balanceController.fetchBalanceSuspend()
    return balanceController.balance
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

    val steps = mutableListOf<String>()
    steps.add("Attempting withdrawal...")
    val primaryBalance = organizer.availableDepositBalance.toKinTruncating()

    // If the primary account has less Kin than the amount
    // requested for withdrawal, we'll need to execute a
    // private transfer to the primary account before we
    // can make a public transfer to destination
    return if (primaryBalance < amount.kin) {
        var missingBalance = amount.kin - primaryBalance
        steps.add("Amount exceeds primary balance.")
        steps.add("Missing balance: $missingBalance")

        // 1. If we're missing funds, we'll pull funds
        // from relationship accounts first.
        if (missingBalance > 0) {
            val receivedFromRelationships =
                transactionReceiver.receiveFromRelationship(organizer, limit = missingBalance)
            missingBalance -= receivedFromRelationships

            steps.add("Pulled from relationships: $receivedFromRelationships")
            steps.add("Missing balance: $missingBalance")
        }

        // 2. It's possible that there's funds still left in
        // an incoming account. If we're still missing funds
        // for withdrawal, we'll pull from incoming.
        if (missingBalance > 0) {
            val receivedFromIncoming = transactionReceiver.receiveFromIncoming(
                organizer = organizer
            )
            missingBalance -= receivedFromIncoming

            steps.add("Pulled from incoming: $receivedFromIncoming")
            steps.add("Missing balance: $missingBalance")
        }


        // 3. In the event that it's a full withdrawal or if
        // more funds are required, we'll need to do a private
        // transfer from bucket accounts.
        if (missingBalance > 0) {
            // Move funds into primary from buckets
            transfer(
                context = context,
                amount = KinAmount.newInstance(kin = missingBalance, rate = Rate.oneToOne),
                organizer = organizer,
                rendezvousKey = intent,
                destination = organizer.primaryVault,
                isWithdrawal = true
            ).doOnComplete {
                steps.add("Pulled from buckets: $missingBalance")
            }.concatWith(fetchLimits()).concatWith(balanceController.fetchBalance())
        } else {
            // 4. Update balances and limits after the withdrawal since
            // it's likely that this withdrawal affected both but at the
            // very least, we need updated balances for all accounts.
            balanceController.fetchBalance()
        }
    } else {
        Completable.complete()
    }.doOnComplete {
        Timber.d(steps.joinToString("\n"))
    }
        // 5. Execute withdrawal
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

fun Client.historicalTransactions() = transactionRepository.transactionCache

@OptIn(ExperimentalCoroutinesApi::class)
fun Client.observeTransactions(
): Flow<List<HistoricalTransaction>> {
    return SessionManager.authState
        .map { it.keyPair }
        .flatMapLatest { owner ->
            transactionRepository.transactionCache
                .map { it to owner }
        }.map { (trx, owner) -> trx.orEmpty().sortedByDescending { it.date } to owner }
        .flatMapConcat { (initialList, owner) ->
            owner ?: return@flatMapConcat emptyFlow()
            fetchPaymentHistoryDelta(owner, initialList.firstOrNull()?.id?.toByteArray())
                .toObservable().asFlow()
                .filter { it.isNotEmpty() }
                .scan(initialList) { previous, update ->
                    Timber.d("prev=${previous.count()}, update=${update.count()}")
                    previous
                        .filterNot { update.contains(it) }
                        .plus(update)
                        .sortedByDescending { it.date }
                        .also { Timber.d("now ${it.count()}") }
                }
        }
}

fun Client.fetchPaymentHistoryDelta(
    owner: KeyPair,
    afterId: ByteArray? = transactionRepository.transactionCache.value?.firstOrNull()?.id?.toByteArray()
): Single<List<HistoricalTransaction>> {
    return transactionRepository.fetchPaymentHistoryDelta(owner, afterId)
}

fun Client.fetchDestinationMetadata(destination: PublicKey): Single<TransactionRepository.DestinationMetadata> {
    return transactionRepository.fetchDestinationMetadata(destination)
}

// -----
private var lastLimitsFetch: Long = 0L

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
    val neededKin =
        if (amount > organizer.slotsBalance) amount - organizer.slotsBalance else Kin.fromKin(0)

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
fun Client.establishRelationship(organizer: Organizer, domain: Domain): Completable {
    return transactionRepository.establishRelationship(organizer, domain).ignoreElement()
}

@Suppress("RedundantSuspendModifier")
@SuppressLint("CheckResult")
@Throws
suspend fun Client.awaitEstablishRelationship(organizer: Organizer, domain: Domain) {
    establishRelationship(organizer, domain)
        .blockingAwait()
}
