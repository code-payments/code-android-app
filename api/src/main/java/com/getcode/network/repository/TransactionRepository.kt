package com.getcode.network.repository

import android.annotation.SuppressLint
import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.*
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.model.*
import com.getcode.model.intents.*
import com.getcode.network.api.TransactionApiV2
import com.getcode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.utils.ErrorUtils
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grpc.stub.StreamObserver
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.SingleSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransactionRepositoryV2"

@Singleton
class TransactionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionApi: TransactionApiV2
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    var sendLimit = mutableListOf<SendLimit>()

    var maxDeposit: Long = 0

    var transactionCache = mutableListOf<HistoricalTransaction>()
        private set

    fun setMaximumDeposit(deposit: Long) {
        maxDeposit = deposit
    }

    fun setSendLimits(limits: MutableList<SendLimit>) {
        sendLimit = limits
    }

    fun clear() {
        maxDeposit = 0
    }

    fun createAccounts(organizer: Organizer): Single<IntentType> {
        if (isMock()) return Single.just(
            IntentCreateAccounts(
                id = PublicKey(listOf()),
                actionGroup = ActionGroup(),
                organizer = organizer
            ) as IntentType
        )
            .delay(1, TimeUnit.SECONDS)

        val createAccounts = IntentCreateAccounts.newInstance(organizer)
        return submit(createAccounts, organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun transfer(
        context: Context,
        amount: KinAmount,
        fee: Kin,
        organizer: Organizer,
        rendezvousKey: PublicKey,
        destination: PublicKey,
        isWithdrawal: Boolean
    ): Single<IntentType> {
        if (isMock()) return Single.just(
            IntentPrivateTransfer(
                id = PublicKey(listOf()),
                actionGroup = ActionGroup(),
                organizer = organizer,
                destination = destination,
                amount = amount,
                fee = fee,
                resultTray = organizer.tray,
                isWithdrawal = isWithdrawal
            ) as IntentType
        )
            .delay(1, TimeUnit.SECONDS)

        val intent = IntentPrivateTransfer.newInstance(
            context = context,
            rendezvousKey = rendezvousKey,
            organizer = organizer,
            destination = destination,
            amount = amount.copy(kin = amount.kin.toKinTruncating()),
            fee = fee,
            isWithdrawal = isWithdrawal
        )

        return submit(intent = intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun receiveFromIncoming(
        context: Context,
        amount: Kin,
        organizer: Organizer
    ): Single<IntentType> {
        val intent = IntentReceive.newInstance(
            context = context,
            organizer = organizer,
            amount = amount.toKinTruncating()
        )
        return submit(intent = intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun receiveFromPrimary(amount: Kin, organizer: Organizer): Single<IntentType> {
        val intent = IntentDeposit.newInstance(
            organizer = organizer,
            amount = amount.toKinTruncating()
        )
        return submit(intent = intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun withdraw(
        amount: KinAmount,
        organizer: Organizer,
        destination: PublicKey
    ): Single<IntentType> {
        val intent = IntentPublicTransfer.newInstance(
            organizer = organizer,
            amount = amount,
            destination = destination
        )

        return submit(intent = intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun upgradePrivacy(
        context: Context,
        mnemonic: MnemonicPhrase,
        upgradeableIntent: UpgradeableIntent
    ): Single<IntentType> {
        val intent = IntentUpgradePrivacy.newInstance(
            context = context,
            mnemonic = mnemonic,
            upgradeableIntent = upgradeableIntent
        )
        return submit(intent, owner = mnemonic.getSolanaKeyPair(context))
    }

    fun sendRemotely(
        context: Context,
        amount: KinAmount,
        organizer: Organizer,
        rendezvousKey: PublicKey,
        giftCard: GiftCardAccount
    ): Single<IntentType> {
        val intent = IntentRemoteSend.newInstance(
            context = context,
            rendezvousKey = rendezvousKey,
            organizer = organizer,
            giftCard = giftCard,
            amount = amount,
        )
        return submit(intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun receiveRemotely(
        context: Context,
        amount: Kin,
        organizer: Organizer,
        giftCard: GiftCardAccount,
        isVoiding: Boolean
    ): Single<IntentType> {
        val intent = IntentRemoteReceive.newInstance(
            context = context,
            organizer = organizer,
            giftCard = giftCard,
            amount = amount,
            isVoidingGiftCard = isVoiding
        )
        return submit(intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun migrateToPrivacy(
        context: Context,
        amount: Kin,
        organizer: Organizer
    ): Single<IntentType> {
        val intent = IntentMigratePrivacy.newInstance(
            organizer = organizer,
            amount = amount,
            context = context
        )

        return submit(intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    private fun submit(intent: IntentType, owner: Ed25519.KeyPair): Single<IntentType> {
        Timber.i("Submit ${intent.id}")
        val subject = SingleSubject.create<IntentType>()

        var serverMessageStream: StreamObserver<TransactionService.SubmitIntentRequest>? = null
        val serverResponse = object : StreamObserver<TransactionService.SubmitIntentResponse> {
            override fun onNext(value: TransactionService.SubmitIntentResponse?) {
                Timber.i("Received: " + value?.responseCase?.name.orEmpty())

                when (value?.responseCase) {
                    // 2. Upon successful submission of intent action the server will
                    // respond with parameters that we'll need to apply to the intent
                    // before crafting and signing the transactions.
                    SERVER_PARAMETERS -> {
                        try {
                            intent.apply(
                                value.serverParameters.serverParametersList
                                    .map { p -> ServerParameter.newInstance(p) }
                            )

                            val submitSignatures = intent.requestToSubmitSignatures()
                            serverMessageStream?.onNext(submitSignatures)

                            Timber.i(
                                "Received ${value.serverParameters.serverParametersList.size} parameters. Submitting signatures..."
                            )
                        } catch (e: Exception) {
                            Timber.i(
                                "Received ${value.serverParameters.serverParametersList.size} parameters but failed to apply them: ${e.javaClass.simpleName} ${e.message})"
                            )
                            subject.onError(ErrorSubmitIntentException(ErrorSubmitIntent.Unknown))
                        }
                    }
                    // 3. If submitted transaction signatures are valid and match
                    // the server, we'll receive a success for the submitted intent.
                    SUCCESS -> {
                        Timber.i("Success.")
                        serverMessageStream?.onCompleted()
                        subject.onSuccess(intent)
                    }
                    // 3. If the submitted transaction signatures don't match, the
                    // intent is considered failed. Something must have gone wrong
                    // on the transaction creation or signing on our side.
                    ERROR -> {
                        val errorReason =
                            value.error.errorDetailsList.firstOrNull()?.reasonString?.reason.orEmpty()
                        value.error.errorDetailsList.forEach { error ->
                            Timber.e(
                                "Error: ${error.reasonString.reason} | ${error.typeCase.name}"
                            )
                        }

                        serverMessageStream?.onCompleted()
                        subject.onError(
                            ErrorSubmitIntentException(
                                ErrorSubmitIntent.fromValue(value.error.codeValue),
                                null,
                                errorReason
                            )
                        )
                    }

                    else -> {
                        Timber.i("Else case. ${value?.responseCase}")
                        serverMessageStream?.onCompleted()
                        subject.onError(ErrorSubmitIntentException(ErrorSubmitIntent.Unknown))
                    }
                }
            }

            override fun onError(t: Throwable?) {
                Timber.i("onError: " + t?.message.orEmpty())
                subject.onError(ErrorSubmitIntentException(ErrorSubmitIntent.Unknown, t))
            }

            override fun onCompleted() {
                Timber.i("onCompleted")
            }
        }
        serverMessageStream = transactionApi.submitIntent(serverResponse)

        // 1. Send `submitActions` request with
        // actions generated by the intent
        serverMessageStream.onNext(intent.requestToSubmitActions(owner))

        return subject
    }

    @SuppressLint("CheckResult")
    fun establishRelationship(organizer: Organizer, domain: Domain): Single<IntentEstablishRelationship> {
        val intent = IntentEstablishRelationship.newInstance(context, organizer, domain)

        return submit(intent = intent, organizer.tray.owner.getCluster().authority.keyPair)
            .map { intent }
            .doOnSuccess { Timber.d("established relationship") }
            .doOnError { Timber.e(t = it, message = "failed to establish relationship") }
    }

    // TODO: potentially make this more generic in the event we introduce more airdrop types
    //       that can be requested for
    fun requestFirstKinAirdrop(owner: Ed25519.KeyPair): Single<KinAmount> {
        val request = TransactionService.AirdropRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setAirdropType(TransactionService.AirdropType.GET_FIRST_KIN)
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return transactionApi.airdrop(request).flatMap {
            when (it.result) {
                TransactionService.AirdropResponse.Result.OK -> {
                    Single.just(KinAmount.fromProtoExchangeData(it.exchangeData))
                        ?: Single.error(IllegalStateException())
                }

                TransactionService.AirdropResponse.Result.ALREADY_CLAIMED -> {
                    Single.error(AirdropException.AlreadyClaimedException())
                }

                TransactionService.AirdropResponse.Result.UNAVAILABLE -> {
                    Single.error(AirdropException.UnavailableException())
                }

                else -> {
                    Single.error(AirdropException.UnknownException())
                }
            }
        }
    }

    fun fetchIntentMetadata(owner: Ed25519.KeyPair, intentId: PublicKey): Single<IntentMetadata> {
        val request = TransactionService.GetIntentMetadataRequest.newBuilder()
            .setIntentId(intentId.toIntentId())
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return transactionApi.getIntentMetadata(request)
            .flatMap {
                if (it.result != TransactionService.GetIntentMetadataResponse.Result.OK) {
                    Single.error(IllegalStateException())
                } else {
                    IntentMetadata.newInstance(it.metadata)?.let { metadata ->
                        Single.just(metadata)
                    } ?: Single.error(IllegalStateException())
                }
            }
    }

    fun fetchTransactionLimits(
        owner: Ed25519.KeyPair,
        timestamp: Long
    ): Flowable<Map<String, SendLimit>> {
        val request = TransactionService.GetLimitsRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setConsumedSince(Timestamp.newBuilder().setSeconds(timestamp))
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        transactionApi.getLimits(request)
            .flatMap {
                if (it.result != TransactionService.GetLimitsResponse.Result.OK) {
                    Single.error(IllegalStateException())
                } else {
                    val map = it.remainingSendLimitsByCurrencyMap
                        .mapValues { v -> v.value.nextTransaction.toDouble() }

                    Limits.newInstance(
                        map = map,
                        maxDeposit = Kin.fromQuarks(it.depositLimit.maxQuarks),
                    ).let { limits ->
                        Single.just(limits)
                    } ?: Single.error(IllegalStateException())
                }
            }
            .doOnSuccess {
                val newList = mutableListOf<SendLimit>()
                it.map.map { entry ->
                    newList.add(SendLimit(entry.key.name, entry.value))
                }
                setSendLimits(newList)
                setMaximumDeposit(it.maxDeposit.toKinTruncatingLong())
            }
            .subscribe({}, ErrorUtils::handleError)

        return Flowable.just(sendLimit)
            .map { it.associateBy { i -> i.id } }
            .distinctUntilChanged()
    }

    fun fetchPaymentHistoryDelta(
        owner: Ed25519.KeyPair,
        afterId: ByteArray? = null
    ): Single<List<HistoricalTransaction>> {
        return Single.create<List<HistoricalTransaction>> {
            val container = mutableListOf<HistoricalTransaction>()
            var after: ByteArray? = afterId

            while (true) {
                val response = fetchPaymentHistoryPage(owner, after).blockingGet()
                if (response.isEmpty()) break
                container.addAll(response)
                transactionCache = transactionCache
                    .filterNot { item -> response.contains(item) }
                    .plus(response)
                    .toMutableList()
                after = response.lastOrNull()?.id?.toByteArray()
            }
            container.reverse()
            it.onSuccess(container)
        }.subscribeOn(Schedulers.io())
    }

    private fun fetchPaymentHistoryPage(
        owner: Ed25519.KeyPair,
        afterId: ByteArray? = null,
        pageSize: Int = 100
    ): Single<List<HistoricalTransaction>> {
        val request = TransactionService.GetPaymentHistoryRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setDirection(TransactionService.GetPaymentHistoryRequest.Direction.ASC)
            .setPageSize(pageSize)
            .let {
                if (afterId != null) {
                    it.setCursor(
                        TransactionService.Cursor.newBuilder()
                            .setValue(ByteString.copyFrom(afterId))
                    )
                } else {
                    it
                }
            }
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return transactionApi.getPaymentHistory(request)
            .map { response ->
                if (response.result == TransactionService.GetPaymentHistoryResponse.Result.OK) {
                    response.itemsList.map Response@{ item ->
                        val currency =
                            CurrencyCode.tryValueOf(item.exchangeData.currency.uppercase())
                                ?: return@Response null

                        HistoricalTransaction(
                            id = item.cursor.value.toByteArray().toList(),
                            paymentType = PaymentType.tryValueOf(item.paymentType.name)
                                ?: PaymentType.Unknown,
                            date = item.timestamp.seconds,
                            transactionRateFx = item.exchangeData.exchangeRate,
                            transactionRateCurrency = currency.name,
                            transactionAmountQuarks = item.exchangeData.quarks,
                            nativeAmount = item.exchangeData.nativeAmount,
                            isDeposit = item.isDeposit,
                            isWithdrawal = item.isWithdraw,
                            isRemoteSend = item.isRemoteSend,
                            isReturned = item.isReturned,
                            airdropType = item.isAirdrop.ifElse(
                                AirdropType.getInstance(item.airdropType),
                                null
                            ),
                        )
                    }.filterNotNull()
                } else {
                    listOf()
                }
            }
    }

    fun fetchDestinationMetadata(destination: PublicKey): Single<DestinationMetadata> {
        val request = TransactionService.CanWithdrawToAccountRequest.newBuilder()
            .setAccount(destination.bytes.toSolanaAccount())
            .build()

        return transactionApi.canWithdrawToAccount(request).map {
            DestinationMetadata.newInstance(
                destination = destination,
                isValid = it.isValidPaymentDestination,
                kind = DestinationMetadata.Kind.tryValueOf(it.accountType.name)
                    ?: DestinationMetadata.Kind.Unknown
            )
        }
            .onErrorReturn {
                DestinationMetadata.newInstance(
                    destination = destination,
                    isValid = false,
                    kind = DestinationMetadata.Kind.Unknown
                )
            }
    }

    fun fetchUpgradeableIntents(owner: Ed25519.KeyPair): Single<List<UpgradeableIntent>> {
        val request = TransactionService.GetPrioritizedIntentsForPrivacyUpgradeRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setLimit(100) //TODO: implement paging
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return transactionApi.getPrioritizedIntentsForPrivacyUpgrade(request).flatMap {
            when (it.result) {
                TransactionService.GetPrioritizedIntentsForPrivacyUpgradeResponse.Result.OK -> {
                    try {
                        val items = it.itemsList.map { item -> UpgradeableIntent.newInstance(item) }
                        Single.just(items)
                    } catch (e: UpgradeablePrivateAction.UpgradeablePrivateActionException.DeserializationFailedException) {
                        Single.error(FetchUpgradeableIntentsException.DeserializationException())
                    } catch (e: Exception) {
                        Single.error(e)
                    }
                }

                TransactionService.GetPrioritizedIntentsForPrivacyUpgradeResponse.Result.NOT_FOUND -> {
                    Single.just(listOf())
                }

                else -> {
                    Single.error(FetchUpgradeableIntentsException.UnknownException())
                }
            }
        }
    }

    data class DestinationMetadata(
        val destination: PublicKey,
        val isValid: Boolean,
        val kind: Kind,

        val hasResolvedDestination: Boolean,
        val resolvedDestination: PublicKey
    ) {
        enum class Kind {
            Unknown,
            TokenAccount,
            OwnerAccount;

            companion object {
                fun tryValueOf(value: String): Kind? {
                    return try {
                        valueOf(value)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }

        companion object {
            fun newInstance(
                destination: PublicKey,
                isValid: Boolean,
                kind: Kind
            ): DestinationMetadata {
                val hasResolvedDestination: Boolean
                val resolvedDestination: PublicKey

                when (kind) {
                    Kind.Unknown, Kind.TokenAccount -> {
                        hasResolvedDestination = false
                        resolvedDestination = destination
                    }

                    Kind.OwnerAccount -> {
                        hasResolvedDestination = true
                        resolvedDestination =
                            AssociatedTokenAccount.newInstance(owner = destination).ata.publicKey
                    }
                }

                return DestinationMetadata(
                    destination = destination,
                    isValid = isValid,
                    kind = kind,
                    hasResolvedDestination = hasResolvedDestination,
                    resolvedDestination = resolvedDestination
                )
            }
        }
    }

    class ErrorSubmitIntentException(
        val errorSubmitIntent: ErrorSubmitIntent,
        cause: Throwable? = null,
        val messageString: String = ""
    ) : Exception(cause) {
        override val message: String
            get() = "${errorSubmitIntent.name} $messageString"
    }

    enum class ErrorSubmitIntent(val value: Int) {
        Denied(0),
        InvalidIntent(1),
        SignatureError(2),
        Unknown(-1);

        companion object {
            fun fromValue(value: Int): ErrorSubmitIntent {
                return values().firstOrNull { it.value == value } ?: Unknown
            }
        }
    }

    sealed class WithdrawException : Exception() {
        class InvalidFractionalKinAmountException : WithdrawException()
        class InsufficientFundsException : WithdrawException()
    }

    sealed class FetchUpgradeableIntentsException : Exception() {
        class DeserializationException : FetchUpgradeableIntentsException()
        class UnknownException : FetchUpgradeableIntentsException()
    }

    sealed class AirdropException : Exception() {
        class AlreadyClaimedException : AirdropException()
        class UnavailableException : AirdropException()
        class UnknownException : AirdropException()
    }
}
