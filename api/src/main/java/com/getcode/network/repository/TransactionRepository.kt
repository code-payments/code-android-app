package com.getcode.network.repository

import android.annotation.SuppressLint
import android.content.Context
import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.DeclareFiatOnrampPurchaseAttemptResponse
import com.codeinc.gen.transaction.v2.TransactionService.ExchangeDataWithoutRate
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.ERROR
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.SERVER_PARAMETERS
import com.codeinc.gen.transaction.v2.TransactionService.SubmitIntentResponse.ResponseCase.SUCCESS
import com.getcode.api.BuildConfig
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.manager.SessionManager
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
import com.getcode.network.integrity.DeviceCheck
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.diff
import com.getcode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.GiftCardAccount
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Relationship
import com.getcode.utils.ErrorUtils
import com.getcode.utils.bytes
import com.getcode.utils.trace
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
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

private const val TAG = "TransactionRepositoryV2"

@Singleton
class TransactionRepository @Inject constructor(
    @ApplicationContext val context: Context,
    val transactionApi: TransactionApiV2,
) : CoroutineScope by CoroutineScope(Dispatchers.IO) {

    var limits: Limits? = null
        private set

    val areLimitsState: Boolean
        get() = limits == null || limits?.isStale == true

    private var lastSwap: Long = 0L

    var maxDeposit: Kin = Kin.fromKin(0)

    fun setMaximumDeposit(deposit: Kin) {
        maxDeposit = deposit
    }

    fun buyLimitFor(currencyCode: CurrencyCode): BuyLimit? {
        return limits?.buyLimitFor(currencyCode)
    }

    fun sendLimitFor(currencyCode: CurrencyCode): SendLimit? {
        return limits?.sendLimitFor(currencyCode)
    }

    fun hasAvailableTransactionLimit(amount: KinAmount): Boolean {
        return (sendLimitFor(amount.rate.currency)?.nextTransaction ?: 0.0) >= amount.fiat
    }

    fun hasAvailableDailyLimit(): Boolean {
        return (sendLimitFor(currencyCode = CurrencyCode.USD)?.nextTransaction ?: 0.0) > 0
    }

    private fun setLimits(limits: Limits) {
        trace("updating limits")
        this.limits = limits
    }

    fun clear() {
        Timber.d("clearing transactions")
        maxDeposit = Kin.fromKin(0)
    }

    fun createAccounts(organizer: Organizer): Single<IntentType> {
        if (isMock()) return Single.just(
            IntentCreateAccounts(
                id = PublicKey(bytes = listOf()),
                actionGroup = ActionGroup(),
                organizer = organizer
            ) as IntentType
        )
            .delay(1, TimeUnit.SECONDS)

        val createAccounts = IntentCreateAccounts.newInstance(organizer)

        return DeviceCheck.integrityResponseSingle()
            .flatMap { tokenResult ->
                submit(createAccounts, organizer.tray.owner.getCluster().authority.keyPair, tokenResult.token)
            }
    }

    fun transfer(
        amount: KinAmount,
        fee: Kin,
        additionalFees: List<Fee>,
        organizer: Organizer,
        rendezvousKey: PublicKey,
        destination: PublicKey,
        isWithdrawal: Boolean,
        tipMetadata: TipMetadata? = null,
    ): Single<IntentType> {
        if (isMock()) return Single.just(
            IntentPrivateTransfer(
                id = PublicKey(bytes = listOf()),
                actionGroup = ActionGroup(),
                organizer = organizer,
                destination = destination,
                grossAmount = amount,
                netAmount = amount,
                fee = fee,
                additionalFees = emptyList(),
                resultTray = organizer.tray,
                isWithdrawal = isWithdrawal,
                tipMetadata = tipMetadata
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
            additionalFees = additionalFees,
            isWithdrawal = isWithdrawal,
            tipMetadata = tipMetadata
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

    suspend fun swapIfNeeded(organizer: Organizer) {
        // We need to check and see if the USDC account has a balance,
        // if so, we'll initiate a swap to Kin. The nuance here is that
        // the balance of the USDC account is reported as `Kin`, where the
        // quarks represent the lamport balance of the account.
        val info = organizer.info(AccountType.Swap) ?: return
        if (info.balance.quarks <= 0) return

        val timeout = 45.seconds

        // Ensure that it's been at least `timeout` seconds since we try
        // another swap if one is already in-flight.
        if (System.currentTimeMillis() - lastSwap < timeout.inWholeMilliseconds) return

        lastSwap = System.currentTimeMillis()

        initiateSwap(organizer)
            .onFailure { ErrorUtils.handleError(it) }
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
        mnemonic: MnemonicPhrase,
        upgradeableIntent: UpgradeableIntent
    ): Single<IntentType> {
        val intent = IntentUpgradePrivacy.newInstance(
            mnemonic = mnemonic,
            upgradeableIntent = upgradeableIntent
        )
        return submit(intent, owner = mnemonic.getSolanaKeyPair())
    }

    fun sendRemotely(
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
        amount: Kin,
        organizer: Organizer
    ): Single<IntentType> {
        val intent = IntentMigratePrivacy.newInstance(
            organizer = organizer,
            amount = amount,
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
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace()
                            }
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
                        val errors = mutableListOf<String>()

                        value.error.errorDetailsList.forEach { error ->
                            when (error.typeCase) {
                                TransactionService.ErrorDetails.TypeCase.REASON_STRING -> {
                                    errors.add("Reason: ${error.reasonString}")
                                }
                                TransactionService.ErrorDetails.TypeCase.INVALID_SIGNATURE -> {
                                    val expected = SolanaTransaction.fromList(error.invalidSignature.expectedTransaction.value.toByteArray().toList())
                                    val produced = intent.transaction()
                                    errors.addAll(
                                        listOf(
                                            "Action index: ${error.invalidSignature.actionId}",
                                            "Invalid signature: ${Signature(error.invalidSignature.providedSignature.value.toByteArray().toList()).base58()}",
                                            "Transaction bytes: ${error.invalidSignature.expectedTransaction.value}",
                                            "Transaction expected: $expected",
                                            "Android produced: $produced"
                                        )
                                    )

                                    expected?.diff(produced)
                                }
                                TransactionService.ErrorDetails.TypeCase.INTENT_DENIED -> {
                                    errors.add("Denied: ${error.intentDenied.reason.name}")
                                }
                                else -> Unit
                            }

                            Timber.e(
                                "Error: ${errors.joinToString("\n")}"
                            )
                        }

                        serverMessageStream?.onCompleted()
                        subject.onError(
                            ErrorSubmitIntentException(
                                ErrorSubmitIntent.invoke(value.error),
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
        val intent = IntentEstablishRelationship.newInstance(organizer, domain)

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
        val intent = IntentEstablishRelationship.newInstance(organizer, domain)

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

    suspend fun declareFiatPurchase(owner: KeyPair, amount: KinAmount, nonce: UUID): Result<Unit> {
        val request = TransactionService.DeclareFiatOnrampPurchaseAttemptRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setPurchaseAmount(ExchangeDataWithoutRate.newBuilder()
                .setCurrency(amount.rate.currency.name.lowercase())
                .setNativeAmount(amount.fiat)
            ).setNonce(
                Model.UUID.newBuilder().setValue(nonce.bytes.toByteString())
            ).apply { setSignature(sign(owner)) }.build()

        return runCatching {
            transactionApi.declareFiatPurchase(request)
                .firstOrNull() ?: throw IllegalArgumentException()
        }.map { response ->
            when (response.result) {
                DeclareFiatOnrampPurchaseAttemptResponse.Result.OK -> Result.success(Unit)
                DeclareFiatOnrampPurchaseAttemptResponse.Result.INVALID_OWNER -> {
                    val error = Throwable("Error: INVALID_OWNER")
                    ErrorUtils.handleError(error)
                    Result.failure(error)
                }
                DeclareFiatOnrampPurchaseAttemptResponse.Result.UNSUPPORTED_CURRENCY -> {
                    val error = Throwable("Error: UNSUPPORTED_CURRENCY")
                    ErrorUtils.handleError(error)
                    Result.failure(error)
                }
                DeclareFiatOnrampPurchaseAttemptResponse.Result.AMOUNT_EXCEEDS_MAXIMUM -> {
                    val error = Throwable("Error: AMOUNT_EXCEEDS_MAXIMUM")
                    ErrorUtils.handleError(error)
                    Result.failure(error)
                }
                DeclareFiatOnrampPurchaseAttemptResponse.Result.UNRECOGNIZED -> {
                    val error = Throwable("Error: UNRECOGNIZED")
                    ErrorUtils.handleError(error)
                    Result.failure(error)
                }
                else -> {
                    val error = Throwable("Error: Unknown Error")
                    ErrorUtils.handleError(error)
                    Result.failure(error)
                }
            }
        }
    }

    // TODO: potentially make this more generic in the event we introduce more airdrop types
    //       that can be requested for
    suspend fun requestFirstKinAirdrop(owner: KeyPair): Result<KinAmount> {
        val request = TransactionService.AirdropRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setAirdropType(TransactionService.AirdropType.GET_FIRST_KIN)
            .apply { setSignature(sign(owner)) }
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
            .apply { setSignature(sign(owner)) }
            .build()

        return runCatching {
            transactionApi.getIntentMetadata(request)
                .toFlowable()
                .asFlow()
                .flowOn(Dispatchers.IO)
                .map {
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
    fun fetchLimits(
        owner: KeyPair,
        timestamp: Long
    ): Flowable<Limits> {
        val request = TransactionService.GetLimitsRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setConsumedSince(Timestamp.newBuilder().setSeconds(timestamp))
            .apply { setSignature(sign(owner)) }
            .build()

        return transactionApi.getLimits(request)
            .flatMap {
                if (it.result != TransactionService.GetLimitsResponse.Result.OK) {
                    Single.error(IllegalStateException())
                } else {
                    Limits.newInstance(
                        sinceDate = timestamp,
                        fetchDate = System.currentTimeMillis(),
                        sendLimits = it.sendLimitsByCurrencyMap,
                        buyLimits = it.buyModuleLimitsByCurrencyMap,
                        deposits = it.depositLimit
                    ).let { limits ->
                        Single.just(limits)
                    }
                }
            }
            .doOnSuccess {
                setLimits(it)
                setMaximumDeposit(it.maxDeposit)
            }
            .doOnError(ErrorUtils::handleError)
            .toFlowable()
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
            .apply { setSignature(sign(owner)) }
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
                            AssociatedTokenAccount.newInstance(owner = destination, mint = Mint.kin).ata.publicKey
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
            get() = "${errorSubmitIntent.javaClass.simpleName} $messageString"
    }

    enum class DeniedReason {
        Unspecified,
        TooManyFreeAccountsForPhoneNumber,
        TooManyFreeAccountsForDevice,
        UnsupportedCountry,
        UnsupportedDevice;

        companion object {
            fun fromValue(value: Int): DeniedReason {
                return entries.firstOrNull { it.ordinal == value } ?: Unspecified
            }
        }
    }

    sealed class ErrorSubmitIntent(val value: Int) {
        data class Denied(val reasons: List<DeniedReason> = emptyList()): ErrorSubmitIntent(0)
        data object InvalidIntent: ErrorSubmitIntent(1)
        data object SignatureError: ErrorSubmitIntent(2)
        data object StaleState: ErrorSubmitIntent(3)
        data object Unknown: ErrorSubmitIntent(-1)
        data object DeviceTokenUnavailable: ErrorSubmitIntent(-2)

        companion object {
            operator fun invoke(proto: SubmitIntentResponse.Error): ErrorSubmitIntent {
                return when (proto.code) {
                    SubmitIntentResponse.Error.Code.DENIED -> {
                        val reasons = proto.errorDetailsList.mapNotNull {
                            if (!it.hasIntentDenied()) return@mapNotNull null
                            DeniedReason.fromValue(it.intentDenied.reasonValue)
                        }

                        Denied(reasons)
                    }
                    SubmitIntentResponse.Error.Code.INVALID_INTENT -> InvalidIntent
                    SubmitIntentResponse.Error.Code.SIGNATURE_ERROR -> SignatureError
                    SubmitIntentResponse.Error.Code.STALE_STATE -> StaleState
                    SubmitIntentResponse.Error.Code.UNRECOGNIZED -> Unknown
                    else -> return Unknown
                }
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
