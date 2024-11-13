package com.getcode.network.client

import android.annotation.SuppressLint
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
import com.getcode.model.AccountInfo
import com.getcode.model.Domain
import com.getcode.model.Fee
import com.getcode.model.ID
import com.getcode.model.IntentMetadata
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Limits
import com.getcode.model.Rate
import com.getcode.model.generate
import com.getcode.model.intents.IntentDeposit
import com.getcode.model.intents.IntentEstablishRelationship
import com.getcode.model.intents.IntentPrivateTransfer
import com.getcode.model.intents.IntentPublicTransfer
import com.getcode.model.intents.IntentRemoteSend
import com.getcode.model.intents.PrivateTransferMetadata
import com.getcode.model.intents.SwapIntent
import com.getcode.network.repository.TransactionRepository
import com.getcode.network.repository.WithdrawException
import com.getcode.network.repository.initiateSwap
import com.getcode.services.utils.flowInterval
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Relationship
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

fun Client.createAccounts(organizer: Organizer): Completable {
    return transactionRepository.createAccounts(organizer)
        .ignoreElement()
}

fun Client.transfer(
    amount: KinAmount,
    fee: Kin,
    additionalFees: List<Fee>,
    organizer: Organizer,
    rendezvousKey: PublicKey,
    destination: PublicKey,
    isWithdrawal: Boolean,
    metadata: PrivateTransferMetadata? = null,
): Completable {
    return transferWithResultSingle(
        amount,
        fee,
        additionalFees,
        organizer,
        rendezvousKey,
        destination,
        isWithdrawal
    ).flatMapCompletable {
        if (it.isSuccess) {
            Timber.d("transfer successful")
            Completable.complete()
        } else {
            Completable.error(it.exceptionOrNull() ?: Throwable("Failed to complete transfer"))
        }
    }
}

fun Client.transferWithResultSingle(
    amount: KinAmount,
    fee: Kin,
    additionalFees: List<Fee>,
    organizer: Organizer,
    rendezvousKey: PublicKey,
    destination: PublicKey,
    isWithdrawal: Boolean,
    metadata: PrivateTransferMetadata? = null,
): Single<Result<ID>> {
    return getTransferPreflightAction(amount.kin)
        .andThen(Single.defer {
            transactionRepository.transfer(
                amount, fee, additionalFees, organizer, rendezvousKey, destination, isWithdrawal, metadata
            )
        })
        .map {
            if (it is IntentPrivateTransfer) {
                balanceController.setTray(organizer, it.resultTray)
            }
            it
        }.map { Result.success(it.id.bytes) }
        .onErrorReturn { Result.failure(it) }
}

fun Client.transferWithResult(
    amount: KinAmount,
    fee: Kin,
    additionalFees: List<Fee>,
    organizer: Organizer,
    rendezvousKey: PublicKey,
    destination: PublicKey,
    isWithdrawal: Boolean,
    metadata: PrivateTransferMetadata? = null,
): Result<ID> {
    return transferWithResultSingle(
        amount = amount,
        fee = fee,
        additionalFees = additionalFees,
        organizer = organizer,
        rendezvousKey = rendezvousKey,
        destination = destination,
        isWithdrawal = isWithdrawal,
        metadata = metadata,
    ).blockingGet()
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
                    amount = truncatedAmount,
                    organizer = organizer,
                    rendezvousKey = rendezvousKey,
                    giftCard = giftCard
                )
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

        balanceController.getBalance()

        return@withContext kinAmount
    }

@SuppressLint("CheckResult")
suspend fun Client.cancelRemoteSend(
    giftCard: GiftCardAccount,
    amount: Kin,
    organizer: Organizer
): Result<Double> = runCatching {
    transactionReceiver.receiveRemotely(
        amount = amount,
        organizer = organizer,
        giftCard = giftCard,
        isVoiding = true
    ).blockingAwait()

    balanceController.getBalance()

    balanceController.rawBalance
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
        throw WithdrawException.InvalidFractionalKinAmountException()
    }

    if (amount.kin > organizer.availableBalance) {
        throw WithdrawException.InsufficientFundsException()
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

        // 2. If we still need funds to fulfill the withdrawal
        // it's likely that they are stuck in incoming and bucket
        // accounts. We'll need to pull those out into primary.
        if (missingBalance > 0) {

            // 3. It's possible that there's funds still left in
            // an incoming account. If we're still missing funds
            // for withdrawal, we'll pull from incoming.
            if (transactionReceiver.availableIncomingAmount(organizer) > 0) {
                val receivedFromIncoming = transactionReceiver.receiveFromIncoming(
                    organizer = organizer
                )
                missingBalance -= receivedFromIncoming

                steps.add("Pulled from incoming: $receivedFromIncoming")
                steps.add("Missing balance: $missingBalance")
            }
        }


        // 4. In the event that it's a full withdrawal or if
        // more funds are required, we'll need to do a private
        // transfer from bucket accounts.
        if (missingBalance > 0) {
            // Move funds into primary from buckets
            transfer(
                amount = KinAmount.newInstance(kin = missingBalance, rate = Rate.oneToOne),
                fee = Kin.fromKin(0),
                additionalFees = emptyList(),
                organizer = organizer,
                rendezvousKey = intent,
                destination = organizer.primaryVault,
                isWithdrawal = true
            ).doOnComplete {
                steps.add("Pulled from buckets: $missingBalance")
            }.concatWith(fetchLimits()).concatWith(balanceController.getBalance())
        } else {
            // 5. Update balances and limits after the withdrawal since
            // it's likely that this withdrawal affected both but at the
            // very least, we need updated balances for all accounts.
            balanceController.getBalance()
        }
    } else {
        Completable.complete()
    }.doOnComplete {
        Timber.d(steps.joinToString("\n"))
    }.concatWith(
        // 6. Execute withdrawal
        withdraw(
            amount = amount,
            organizer = organizer,
            destination = destination
        )
    ).doOnComplete {
        trace(
            tag = "Trx",
            message = "Withdraw completed",
            type = TraceType.Process
        )
    }
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
    amount: KinAmount,
    organizer: Organizer,
    rendezvousKey: PublicKey,
    giftCard: GiftCardAccount
): Completable {
    return Completable.defer {
        transactionRepository.sendRemotely(
            amount, organizer, rendezvousKey, giftCard
        )
            .map {
                if (it is IntentRemoteSend) {
                    balanceController.setTray(organizer, it.resultTray)
                }
            }
            .ignoreElement()
    }
}


suspend fun Client.requestFirstKinAirdrop(
    owner: KeyPair,
): Result<KinAmount> {
    Timber.d("requesting airdrop")
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
): Limits? {
    val time = System.currentTimeMillis()

    val isStale = transactionRepository.areLimitsState

    if (!isStale && !isForce) {
        return transactionRepository.limits
    }

    Timber.i("fetchTransactionLimits")
    lastLimitsFetch = time

    val date: Calendar = GregorianCalendar()
    date.set(Calendar.HOUR_OF_DAY, 0)
    date.set(Calendar.MINUTE, 0)
    date.set(Calendar.SECOND, 0)
    date.set(Calendar.MILLISECOND, 0)

    val seconds = date.timeInMillis / 1000
    return transactionRepository.fetchLimits(owner, seconds)
        .subscribeOn(Schedulers.io())
        .blockingFirst()
}

fun Client.fetchDestinationMetadata(destination: PublicKey): Single<TransactionRepository.DestinationMetadata> {
    return transactionRepository.fetchDestinationMetadata(destination)
}

// -----
private var lastLimitsFetch: Long = 0L

fun Client.fetchLimits(isForce: Boolean = false): Completable {
    val owner = SessionManager.getKeyPair() ?: return Completable.complete()
    fetchTransactionLimits(owner, isForce)
    return Completable.complete()
}

fun Client.receiveIfNeeded(): Completable {
    val organizer = SessionManager.getOrganizer() ?: return Completable.complete()

    if (organizer.slotsBalance < transactionRepository.maxDeposit) {
        receiveFromRelationships(organizer, upTo = transactionRepository.maxDeposit)
    }

    return Completable.concatArray(
        receiveFromPrimaryIfWithinLimits(organizer),
        transactionReceiver.receiveFromIncomingCompletable(organizer)
    )
}

fun Client.receiveFromPrimaryIfWithinLimits(organizer: Organizer): Completable {
    Timber.d("receive within limits")
    val depositBalance = organizer.availableDepositBalance.toKinTruncating()

    // Nothing to deposit
    if (!depositBalance.hasWholeKin()) {
        Timber.d("nothing to deposit ($depositBalance)")
        return Completable.complete()
    }

    // We want to deposit the smaller of the two: balance in the
    // primary account or the max allowed amount provided by server
    return Single.just(transactionRepository.maxDeposit.toKinTruncatingLong())
        .map { maxDeposit ->
            Pair(
                Kin.fromKin(min(depositBalance.toKinValueDouble(), maxDeposit.toDouble())),
                Kin.fromKin(maxDeposit)
            )
        }
        .filter { pair ->
            val (depositAmount, _) = pair
            depositAmount.hasWholeKin().also { Timber.d("hasWholeKin=$it") }
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
        .andThen {
            trace(
                tag = "Trx",
                message = "Received from primary",
                type = TraceType.Process
            )
        }
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
                        organizer.mnemonic,
                        intent
                    ).ignoreElement()

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
        Timber.d("received ${receivedKin.quarks} from incoming")
        // 2. Pull funds from relationships if there's still funds
        // missing in buckets after the receiving from primary
        if (receivedKin < neededKin) {
            Timber.d("attempt to pull funds from relationship to get to ${neededKin.quarks}")
            val result = transactionReceiver.receiveFromRelationship(organizer, limit = neededKin - receivedKin)
            Timber.d("received ${result.quarks} from relationships")
        }

        // 3. If the amount is still larger than what's available
        // in the slots, we'll need to move funds from primary
        // deposits into slots after receiving
        if (amount > organizer.slotsBalance) {
            Timber.d("receive from primary")
            receiveFromPrimaryIfWithinLimits(organizer)
        } else {
            Completable.complete()
        }
    } else {
        Completable.complete()
    }
}

fun Client.receiveFromRelationships(organizer: Organizer, upTo: Kin? = null): Kin {
    return transactionReceiver.receiveFromRelationship(organizer, upTo)
}

@SuppressLint("CheckResult")
@Throws
fun Client.establishRelationshipSingle(organizer: Organizer, domain: Domain): Single<IntentEstablishRelationship> {
    return transactionRepository.establishRelationshipSingle(organizer, domain)
}

@Suppress("RedundantSuspendModifier")
@SuppressLint("CheckResult")
@Throws
suspend fun Client.awaitEstablishRelationship(
    organizer: Organizer,
    domain: Domain
): Result<Relationship> {
    return transactionRepository.establishRelationship(organizer, domain)
        .map { it.relationship }
}

suspend fun Client.initiateSwap(organizer: Organizer): Result<SwapIntent> {
    return transactionRepository.initiateSwap(organizer)
}

suspend fun Client.declareFiatPurchase(owner: KeyPair, amount: KinAmount, nonce: UUID) : Result<Unit> {
    return transactionRepository.declareFiatPurchase(owner, amount, nonce)
}