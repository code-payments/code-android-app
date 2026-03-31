package com.flipcash.app.onramp.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.encryption.boxOpen
import com.flipcash.app.core.encryption.toPublicKey
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletConnection
import com.flipcash.app.core.onramp.deeplinks.ExternallySignedTransaction
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkConnectionResult
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkSigningResult
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionController
import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.model.LiquidityPool
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.instructions.createAssociatedTokenAccountInstruction
import com.getcode.solana.instructions.createSplTransfer
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.solana.rpc.RpcException
import com.getcode.solana.rpc.SolanaConnection
import com.getcode.solana.rpc.doesAccountExist
import com.getcode.solana.rpc.sendTransaction
import com.getcode.solana.rpc.simulateTransaction
import com.getcode.solana.transactions.inspect
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base64
import com.getcode.utils.hexEncodedString
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import com.ionspin.kotlin.crypto.box.Box
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.toUnsignedTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ExternalWalletDeeplinkState(
    private val userManager: UserManager,
    private val transactionController: TransactionController,
    private val scope: CoroutineScope,
    rpcUrl: String,
    networkDriver: HttpNetworkDriver,
) {
    private val connection = SolanaConnection(rpcUrl)
    private val driver = Rpc20Driver(rpcUrl, networkDriver)

    /**
     * The origin of the flow for onramping via supported wallets using deeplinks (menu screen)
     */
    internal var origin by mutableStateOf<AppRoute?>(null)

    internal var provider: OnRampProvider.UsesDeeplinks? = null

    /**
     * Pending navigation route for screens to consume.
     * Used by inner (sheet) navigators that the handler can't access directly.
     */
    var pendingNavigation: AppRoute? by mutableStateOf(null)

    internal val keyPair = Box.keypair()
    val curvePublicKey: String?
        get() = keyPair.publicKey.toPublicKey().base58()

    /**
     * The state of the deeplink onramp
     */
    var deeplinkState by mutableStateOf(ExternalWalletState.IDLE)

    internal val errors = MutableSharedFlow<DeeplinkOnRampError>()

    /**
     * The amount to transfer selected by the user
     */
    var amount: LocalFiat? by mutableStateOf(null)

    var tokenToPurchase: Token? = null

    /**
     * The unsigned transaction to be signed by Phantom
     */
    internal var unsignedTransaction: List<Byte>? = null

    /**
     * The signed transaction from Phantom
     */
    internal var signedTransaction: String? = null
    internal var signature: Signature? = null

    var swapId: SwapId? = null
        internal set

    /**
     * The public key of the encryption key used by the external wallet
     */
    internal var phantomEncryptionPublicKey: List<Byte>? = null

    /**
     * The connection details from the external wallet containing the wallet's public key and session token
     */
    internal var walletConnection: ExternalWalletConnection? = null

    /**
     * The connection result from the external wallet's deeplink
     */
    private var connectionResult: WalletDeeplinkConnectionResult? = null

    /**
     * The signing result from the external wallet's deeplink
     */
    private var signingResult: WalletDeeplinkSigningResult? = null

    /**
     * Decrypt the connection result from the external wallet's deeplink
     */
    fun decrypt(connectionResult: WalletDeeplinkConnectionResult) {
        this.connectionResult = connectionResult
        this.phantomEncryptionPublicKey = connectionResult.encryptionPublicKey
        try {
            val data = connectionResult.encryptedData.boxOpen(
                privateKey = keyPair.secretKey.map { it.toByte() },
                publicKey = connectionResult.encryptionPublicKey,
                nonce = connectionResult.nonce,
            ).map { String(it.toByteArray()) }
                .fold(
                    onSuccess = {
                        runCatching { Json.decodeFromString<ExternalWalletConnection>(it) }
                            .onFailure { error ->
                                scope.launch {
                                    errors.emit(
                                        DeeplinkOnRampError.DeserializationError(
                                            error.message,
                                            cause = error
                                        )
                                    )
                                }
                            }
                    },
                    onFailure = {
                        Result.failure(it)
                    }
                ).getOrThrow()

            walletConnection = data
            deeplinkState = ExternalWalletState.CONNECTED
        } catch (e: Exception) {
            scope.launch {
                errors.emit(DeeplinkOnRampError.DecryptionError(e.message, cause = e))
            }
        }
    }

    /**
     * Decrypt the signing result from Phantom's deeplink
     */
    fun decrypt(signingResult: WalletDeeplinkSigningResult) {
        this.signingResult = signingResult
        try {
            val data = signingResult.encryptedData.boxOpen(
                privateKey = keyPair.secretKey.map { it.toByte() },
                publicKey = connectionResult!!.encryptionPublicKey,
                nonce = signingResult.nonce,
            ).map { String(it.toByteArray()) }
                .fold(
                    onSuccess = {
                        runCatching { Json.decodeFromString<ExternallySignedTransaction>(it) }
                            .onFailure { error ->
                                scope.launch {
                                    errors.emit(
                                        DeeplinkOnRampError.DeserializationError(
                                            error.message,
                                            cause = error
                                        )
                                    )
                                }
                            }
                    },
                    onFailure = { Result.failure(it) }
                ).getOrThrow()

            signedTransaction = data.serializedTransaction
            signature = firstSignature(data.serializedTransaction)
            deeplinkState = ExternalWalletState.SIGNED
        } catch (e: Exception) {
            scope.launch {
                errors.emit(
                    DeeplinkOnRampError.DecryptionError(e.message, cause = e)
                )
            }
        }
    }

    suspend fun sendTransaction() {
        val transaction = signedTransaction ?: return
        withContext(Dispatchers.IO) {
            deeplinkState = ExternalWalletState.TRANSACTING

            val sendIt = suspend {
                // build rpc request
                driver.sendTransaction(transaction)
                    .onSuccess {
                        deeplinkState = ExternalWalletState.TRANSACTED
                    }.onFailure { error ->
                        val code = (error as? RpcException)?.code?.toLong()
                        trace(
                            message = "Unexpected error sending solana transaction",
                            type = TraceType.Error,
                            metadata = {
                                if (error is RpcException) {
                                    "code" to error.code
                                }
                                "message" to error.message
                            }
                        )
                        ErrorUtils.handleError(error)
                        scope.launch {
                            errors.emit(
                                DeeplinkOnRampError.FailedToSendTransaction(
                                    code = code ?: -99L,
                                    message = error.message,
                                    cause = error
                                )
                            )
                        }
                    }
            }

            if (origin is AppRoute.Token.Info) {
                initiateBuy()
                    .fold(
                        onSuccess = {
                            sendIt()
                            Result.success(Unit)
                        },
                        onFailure = {
                            errors.emit(DeeplinkOnRampError.FailedToSubmitBuyToServer(message = it.message))
                            Result.failure(it)
                        }
                    )
            } else {
                sendIt()
            }
        }
    }

    /**
     * Set the origin of the flow for onramping via Phantom
     */
    fun setOrigin(origin: AppRoute?) {
        this.origin = origin
    }

    fun setProvider(provider: OnRampProvider.UsesDeeplinks) {
        this.provider = provider
    }

    /**
     * Start the deeplink process
     */
    fun start(origin: AppRoute?, provider: OnRampProvider.UsesDeeplinks) {
        reset()
        setOrigin(origin)
        setProvider(provider)
        deeplinkState = ExternalWalletState.STARTED
    }

    /**
     * Handle a pre-parsed external wallet deeplink type dispatched from the router.
     */
    fun handleWalletDeeplink(type: DeeplinkType) {
        when (type) {
            is DeeplinkType.ExternalWalletConnection -> {
                val result = type.result
                val error = type.error
                if (result != null) {
                    decrypt(connectionResult = result)
                } else {
                    val resolvedError = DeeplinkError.fromCode(error?.errorCode)
                    val message = error?.errorMessage ?: "Something went wrong"
                    errors.tryEmit(DeeplinkOnRampError.WalletProvidedError(resolvedError, message = message))
                }
            }
            is DeeplinkType.ExternalWalletSignedTransaction -> {
                val result = type.result
                val error = type.error
                if (result != null) {
                    decrypt(signingResult = result)
                } else {
                    val resolvedError = DeeplinkError.fromCode(error?.errorCode)
                    val message = error?.errorMessage ?: "Something went wrong"
                    errors.tryEmit(DeeplinkOnRampError.WalletProvidedError(resolvedError, message = message))
                }
            }
            else -> {}
        }
    }

    /**
     * Reset the state of the onramp flow
     */
    fun reset() {
        origin = null
        provider = null
        deeplinkState = ExternalWalletState.IDLE
        pendingNavigation = null
        phantomEncryptionPublicKey = null
        amount = null
        walletConnection = null
        connectionResult = null
        unsignedTransaction = null
        signedTransaction = null
        signingResult = null
    }

    suspend fun createAndValidateSwapTransaction() {
        createUsdcToUsdfSwapTransaction()
            .onFailure {
                errors.emit(DeeplinkOnRampError.FailedToCreateTransaction(message = it.message))
            }.fold(
                onSuccess = { transaction ->
                    withContext(Dispatchers.IO) {
                        driver.simulateTransaction(transaction.encode().base64)
                            .fold(
                                onSuccess = { Result.success(transaction) },
                                onFailure = { Result.failure(it) }
                            )
                    }
                },
                onFailure = { Result.failure(it) }
            )
            .onFailure {
                errors.emit(DeeplinkOnRampError.FailedToSimulateTransaction(message = it.message))
            }
            .onSuccess {
                unsignedTransaction = it.encode().toList()
                deeplinkState = ExternalWalletState.SIGNING
            }
    }

    suspend fun createAndValidateDepositTransaction() {
        createDepositTransaction()
            .onFailure {
                errors.emit(DeeplinkOnRampError.FailedToCreateTransaction(message = it.message))
            }
            .fold(
                onSuccess = { transaction ->
                    withContext(Dispatchers.IO) {
                        driver.simulateTransaction(transaction.serialize().base64)
                            .fold(
                                onSuccess = { Result.success(transaction) },
                                onFailure = { Result.failure(it) }
                            )
                    }
                },
                onFailure = { Result.failure(it) }
            )
            .onFailure {
                errors.emit(DeeplinkOnRampError.FailedToSimulateTransaction(message = it.message))
            }
            .onSuccess {
                unsignedTransaction = it.serialize().toList()
                deeplinkState = ExternalWalletState.SIGNING
            }
    }

    private suspend fun createUsdcToUsdfSwapTransaction(): Result<SolanaTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }
                val externalWallet = requireNotNull(walletConnection?.publicKey) { "Wallet connection is null" }
                val amountToSend = requireNotNull(amount) { "Amount is null" }
                val sender = PublicKey(externalWallet.bytes)

                swapId = SwapId.generate()
                val recentBlockhash = connection.getLatestBlockhash()
                val transaction = TransactionBuilder.usdcFundSwap(
                    owner = owner.authorityPublicKey,
                    sender = sender,
                    amount = amountToSend.underlyingTokenAmount.quarks,
                    pool = LiquidityPool.usdf,
                    blockhash = Hash(recentBlockhash),
                    swapId = swapId!!
                )

                unsignedTransaction = transaction.encode().toList()
                deeplinkState = ExternalWalletState.SIGNING

                Result.success(transaction)
            } catch (e: Exception) {
                trace("External swap failed", type = TraceType.Error, error = e)
                Result.failure(e)
            }
        }
    }

    private suspend fun initiateBuy(): Result<SwapId> {
        val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }
        val token = requireNotNull(tokenToPurchase) { "Token is null" }
        val amountToSend = requireNotNull(amount) { "Amount is null" }
        val transactionSignature = requireNotNull(signature) { "Transaction not signed" }

        return withContext(NonCancellable) {
            transactionController.buy(
                owner = owner,
                amount = amountToSend,
                of = token,
                swapId = swapId,
                source = SwapFundingSource.ExternalWallet(transactionSignature.bytes),
                fund = { Result.success(Unit) }
            )
        }
    }

    private suspend fun createDepositTransaction(): Result<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val usdfMint = Mint.usdf
                val quarks = requireNotNull(amount?.underlyingTokenAmount?.quarks) { "Amount is null" }
                val externalWallet = requireNotNull(walletConnection?.publicKey) { "Wallet connection is null" }
                val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }

                val sender = PublicKey(externalWallet.bytes)
                val senderTokenAccount = PublicKey.deriveAssociatedAccount(
                    owner = PublicKey(sender.base58()),
                    mint = PublicKey(usdfMint.base58())
                )
                val destinationTokenAccount = PublicKey.deriveAssociatedAccount(
                    owner = PublicKey(owner.usdfDepositAddress.base58()),
                    mint = PublicKey(usdfMint.base58())
                )

                // Check if ATAs exist with proper error handling
                val senderAtaExists = driver.doesAccountExist(senderTokenAccount.publicKey)
                    .onFailure { e ->
                        trace("Failed to check sender ATA", type = TraceType.Error, error = e)
                        return@withContext Result.failure(e)
                    }.map { true }.getOrElse { false }

                val recipientAtaExists = driver.doesAccountExist(destinationTokenAccount.publicKey)
                    .onFailure { e ->
                        trace("Failed to check recipient ATA", type = TraceType.Error, error = e)
                        return@withContext Result.failure(e)
                    }.map { true }.getOrElse { false }

                val instructions = buildList {
                    // Create sender ATA if it doesn't exist
                    if (!senderAtaExists) {
                        add(
                            createAssociatedTokenAccountInstruction(
                                payer = sender,
                                owner = sender,
                                ata = senderTokenAccount.publicKey,
                                mint = usdfMint
                            )
                        )
                    }

                    // Create recipient ATA if it doesn't exist
                    if (!recipientAtaExists) {
                        add(
                            createAssociatedTokenAccountInstruction(
                                payer = sender,
                                owner = owner.usdfDepositAddress,
                                ata = destinationTokenAccount.publicKey,
                                mint = usdfMint
                            )
                        )
                    }

                    add(
                        createSplTransfer(
                            sender = sender,
                            sourceTokenAccount = senderTokenAccount.publicKey,
                            destinationTokenAccount = destinationTokenAccount.publicKey,
                            quarks = quarks
                        )
                    )
                }

                // build transaction
                val message = Message.Builder()
                    .setRecentBlockhash(connection.getLatestBlockhash())
                    .apply { instructions.forEach { addInstruction(it) } }
                    .build()

                // inspect message
                message.inspect()

                val transaction = message.toUnsignedTransaction()

                Result.success(transaction)
            } catch (e: Exception) {
                trace("Transaction creation failed", type = TraceType.Error, error = e)
                Result.failure(e)
            }
        }
    }
}

enum class ExternalWalletState {
    // not in flight or errored
    IDLE,

    // states
    STARTING,
    STARTED,
    CONNECTING,
    CONNECTED,
    SIGNING,
    SIGNED,
    TRANSACTING,
    TRANSACTED,
}

private fun firstSignature(base58: String): Signature? {
    return SolanaTransaction.fromList(Base58.decode(base58).toList())?.identifier
}