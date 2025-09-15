package com.flipcash.app.onramp.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.encryption.boxOpen
import com.flipcash.app.core.encryption.toPublicKey
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletConnection
import com.flipcash.app.core.onramp.deeplinks.ExternallySignedTransaction
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkConnectionResult
import com.flipcash.app.core.onramp.deeplinks.WalletDeeplinkSigningResult
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.instructions.createAssociatedTokenAccountInstruction
import com.getcode.solana.instructions.createSplTransfer
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.solana.rpc.RpcException
import com.getcode.solana.rpc.SolanaConnection
import com.getcode.solana.rpc.doesAccountExist
import com.getcode.solana.rpc.sendTransaction
import com.getcode.solana.rpc.simulateTransaction
import com.getcode.solana.transactions.buildPatched
import com.getcode.solana.transactions.inspect
import com.getcode.utils.TraceType
import com.getcode.utils.base64
import com.getcode.utils.trace
import com.ionspin.kotlin.crypto.box.Box
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.toUnsignedTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ExternalWalletDeeplinkState(
    private val userManager: UserManager,
    private val scope: CoroutineScope,
    rpcUrl: String,
    networkDriver: HttpNetworkDriver,
) {
    private val connection = SolanaConnection(rpcUrl)
    private val driver = Rpc20Driver(rpcUrl, networkDriver)

    /**
     * The origin of the flow for onramping via supported wallets using deeplinks (menu screen, pool)
     */
    internal var origin by mutableStateOf<AppRoute?>(null)

    internal var provider: OnRampProvider.UsesDeeplinks? = null

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
    var amount: Fiat? by mutableStateOf(null)

    /**
     * The unsigned transaction to be signed by Phantom
     */
    internal var unsignedTransaction: Transaction? = null

    /**
     * The signed transaction from Phantom
     */
    internal var signedTransaction: String? = null

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
            // build rpc request
            driver.sendTransaction(transaction)
                .onSuccess {
                    deeplinkState = ExternalWalletState.TRANSACTED
                }.onFailure { error ->
                    val code = (error as? RpcException)?.code
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
                    scope.launch {
                        errors.emit(
                            DeeplinkOnRampError.FailedToSendTransaction(
                                code = code ?: -99,
                                message = error.message,
                                cause = error
                            )
                        )
                    }
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
     * Reset the state of the onramp flow
     */
    fun reset() {
        origin = null
        provider = null
        deeplinkState = ExternalWalletState.IDLE
        phantomEncryptionPublicKey = null
        amount = null
        walletConnection = null
        connectionResult = null
        unsignedTransaction = null
        signedTransaction = null
        signingResult = null
    }

    suspend fun createAndSendTransaction() {
        createTransaction()
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
                unsignedTransaction = it
                println("unsignedTransaction: ${it.serialize().base64}")
                deeplinkState = ExternalWalletState.SIGNING
            }
    }

    private suspend fun createTransaction(): Result<Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val usdcMint = Mint.usdc
                val quarks = requireNotNull(amount?.quarks) { "Amount is null" }
                val externalWallet = requireNotNull(walletConnection?.publicKey) { "Wallet connection is null" }
                val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }

                val sender = PublicKey(externalWallet.bytes)
                val senderTokenAccount = PublicKey.deriveAssociatedAccount(
                    owner = PublicKey(sender.base58()),
                    mint = PublicKey(usdcMint.base58())
                )
                val destinationTokenAccount = PublicKey.deriveAssociatedAccount(
                    owner = PublicKey(owner.depositAddress.base58()),
                    mint = PublicKey(usdcMint.base58())
                )

                // Check if ATAs exist with proper error handling
                val senderAtaExists = driver.doesAccountExist(senderTokenAccount.publicKey)
                    .onFailure { e ->
                        println("Failed to check sender ATA: ${e.message}")
                        return@withContext Result.failure(e)
                    }.map { true }.getOrElse { false }

                val recipientAtaExists = driver.doesAccountExist(destinationTokenAccount.publicKey)
                    .onFailure { e ->
                        println("Failed to check recipient ATA: ${e.message}")
                        return@withContext Result.failure(e)
                    }.map { true }.getOrElse { false }

                println("sender: ${sender.base58()}")
                println("owner: ${owner.depositAddress.base58()}")
                println("senderTokenAccount(exists: $senderAtaExists): ${senderTokenAccount.publicKey.base58()}")
                println("destinationTokenAccount(exists: $recipientAtaExists): ${destinationTokenAccount.publicKey.base58()}")

                val instructions = buildList {
                    // Create sender ATA if it doesn't exist
                    if (!senderAtaExists) {
                        add(
                            createAssociatedTokenAccountInstruction(
                                payer = sender,
                                owner = sender,
                                ata = senderTokenAccount.publicKey,
                                mint = usdcMint
                            )
                        )
                    }

                    // Create recipient ATA if it doesn't exist
                    if (!recipientAtaExists) {
                        add(
                            createAssociatedTokenAccountInstruction(
                                payer = sender,
                                owner = owner.depositAddress,
                                ata = destinationTokenAccount.publicKey,
                                mint = usdcMint
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
                println("Serialized transaction size: ${transaction.serialize().size} bytes")

                Result.success(transaction)
            } catch (e: Exception) {
                println("Transaction creation failed: ${e.message}")
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

private fun PublicKey.asSolanaPublicKey() = SolanaPublicKey(byteArray)