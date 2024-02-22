package com.getcode.network.repository

import android.annotation.SuppressLint
import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.ERROR
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.SERVER_PARAMETERS
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.SUCCESS
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.*
import com.getcode.model.intents.ActionGroup
import com.getcode.model.intents.IntentCreateAccounts
import com.getcode.model.intents.IntentDeposit
import com.getcode.model.intents.IntentEstablishRelationship
import com.getcode.model.intents.IntentMigratePrivacy
import com.getcode.model.intents.IntentPrivateTransfer
import com.getcode.model.intents.IntentPublicTransfer
import com.getcode.model.intents.IntentReceive
import com.getcode.model.intents.IntentRemoteReceive
import com.getcode.model.intents.IntentRemoteSend
import com.getcode.model.intents.IntentType
import com.getcode.model.intents.IntentUpgradePrivacy
import com.getcode.model.intents.ServerParameter
import com.getcode.network.api.TransactionApiV2
import com.getcode.network.appcheck.AppCheck
import com.getcode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Relationship
import com.getcode.utils.ErrorUtils
import com.google.protobuf.Timestamp
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grpc.stub.StreamObserver
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.SingleSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransactionRepositoryV2"

@Singleton
class TransactionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionApi: TransactionApiV2,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    var sendLimit = mutableListOf<SendLimit>()

    var maxDeposit: Kin = Kin.fromKin(0)

    fun setMaximumDeposit(deposit: Kin) {
        maxDeposit = deposit
    }

    fun setSendLimits(limits: MutableList<SendLimit>) {
        sendLimit = limits
    }

    fun clear() {
        Timber.d("clearing transactions")
        maxDeposit = Kin.fromKin(0)
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

        return AppCheck.limitedUseTokenSingle()
            .flatMap { tokenResult ->
                submit(createAccounts, organizer.tray.owner.getCluster().authority.keyPair, tokenResult.token?.token)
            }
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
            source = AccountType.Primary,
            organizer = organizer,
            amount = amount.toKinTruncating()
        )
        return submit(intent = intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun receiveFromRelationship(
        relationship: Relationship,
        organizer: Organizer
    ): Single<IntentType> {
        val intent = IntentPublicTransfer.newInstance(
            source = AccountType.Relationship(relationship.domain),
            organizer = organizer,
            amount = KinAmount.newInstance(relationship.partialBalance, Rate.oneToOne),
            destination = IntentPublicTransfer.Destination.Local(AccountType.Primary)
        )
        return submit(intent, owner = organizer.tray.owner.getCluster().authority.keyPair)
    }

    fun withdraw(
        amount: KinAmount,
        organizer: Organizer,
        destination: PublicKey
    ): Single<IntentType> {
        val intent = IntentPublicTransfer.newInstance(
            organizer = organizer,
            amount = amount,
            destination = IntentPublicTransfer.Destination.External(destination),
            source = AccountType.Primary,
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

    private fun submit(intent: IntentType, owner: KeyPair, deviceToken: String? = null): Single<IntentType> {
        Timber.i("Submit ${intent.javaClass.simpleName}")
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
        serverMessageStream.onNext(intent.requestToSubmitActions(owner, deviceToken))

        return subject
    }

    fun establishRelationshipSingle(
        organizer: Organizer,
        domain: Domain,
    ) : Single<IntentEstablishRelationship> {
        val intent = IntentEstablishRelationship.newInstance(context, organizer, domain)

        return submit(intent = intent, organizer.tray.owner.getCluster().authority.keyPair)
            .map { intent }
            .doOnSuccess { Timber.d("established relationship") }
            .doOnError { Timber.e(t = it, message = "failed to establish relationship") }
    }

    @SuppressLint("CheckResult")
    suspend fun establishRelationship(
        organizer: Organizer,
        domain: Domain
    ): Result<IntentEstablishRelationship> {
        val intent = IntentEstablishRelationship.newInstance(context, organizer, domain)

        return runCatching {
            submit(intent = intent, organizer.tray.owner.getCluster().authority.keyPair)
                .map { intent }
                .toFlowable()
                .asFlow()
                .firstOrNull() ?: throw IllegalArgumentException()
        }.onSuccess {
            Timber.d("established relationship")
        }.onFailure {
            ErrorUtils.handleError(it)
            Timber.e(t = it, message = "failed to establish relationship")
        }
    }

    // TODO: potentially make this more generic in the event we introduce more airdrop types
    //       that can be requested for
    suspend fun requestFirstKinAirdrop(owner: KeyPair): Result<KinAmount> {
        val request = TransactionService.AirdropRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setAirdropType(TransactionService.AirdropType.GET_FIRST_KIN)
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return runCatching {
            transactionApi.airdrop(request)
                .toFlowable()
                .asFlow()
                .flowOn(Dispatchers.IO)
                .map {
                    when (it.result) {
                        TransactionService.AirdropResponse.Result.OK -> {
                            KinAmount.fromProtoExchangeData(it.exchangeData)
                        }

                        TransactionService.AirdropResponse.Result.ALREADY_CLAIMED -> {
                            throw AirdropException.AlreadyClaimedException()
                        }

                        TransactionService.AirdropResponse.Result.UNAVAILABLE -> {
                            throw AirdropException.UnavailableException()
                        }

                        else -> {
                            throw AirdropException.UnknownException()
                        }
                    }
                }.first()
        }
    }

    suspend fun fetchIntentMetadata(
        owner: KeyPair,
        intentId: PublicKey
    ): Result<IntentMetadata> {
        val request = TransactionService.GetIntentMetadataRequest.newBuilder()
            .setIntentId(intentId.toIntentId())
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .let {
                val bos = ByteArrayOutputStream()
                it.buildPartial().writeTo(bos)
                it.setSignature(Ed25519.sign(bos.toByteArray(), owner).toSignature())
            }
            .build()

        return runCatching {
            transactionApi.getIntentMetadata(request)
                .toFlowable()
                .asFlow()
                .flowOn(Dispatchers.IO)
                .map {
                    Timber.d("${it.result}")
                    if (it.result != TransactionService.GetIntentMetadataResponse.Result.OK) {
                        throw IllegalStateException()
                    } else {
                        IntentMetadata.newInstance(it.metadata) ?: throw IllegalStateException()
                    }
                }
                .firstOrNull() ?: throw IllegalStateException()
        }
    }

    @SuppressLint("CheckResult")
    fun fetchTransactionLimits(
        owner: KeyPair,
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
                    }
                }
            }
            .doOnSuccess {
                val newList = mutableListOf<SendLimit>()
                it.map.map { entry ->
                    newList.add(SendLimit(entry.key.name, entry.value))
                }
                setSendLimits(newList)
                setMaximumDeposit(it.maxDeposit)
            }
            .subscribe({}, ErrorUtils::handleError)

        return Flowable.just(sendLimit)
            .map { it.associateBy { i -> i.id } }
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

    fun fetchUpgradeableIntents(owner: KeyPair): Single<List<UpgradeableIntent>> {
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
