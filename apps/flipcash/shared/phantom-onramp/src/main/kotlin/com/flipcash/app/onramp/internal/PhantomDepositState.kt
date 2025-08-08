package com.flipcash.app.onramp.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.encryption.boxOpen
import com.flipcash.app.core.encryption.toPublicKey
import com.flipcash.app.core.phantom.PhantomConnectionResult
import com.flipcash.app.core.phantom.PhantomSignedTransaction
import com.flipcash.app.core.phantom.PhantomSigningResult
import com.flipcash.app.core.phantom.PhantomWalletConnection
import com.flipcash.app.onramp.PhantomOnRampError
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
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.ionspin.kotlin.crypto.box.Box
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.Rpc20Driver
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.TransactionInstruction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PhantomDepositState(
    private val userManager: UserManager,
    private val scope: CoroutineScope,
    rpcUrl: String,
    networkDriver: HttpNetworkDriver,
) {
    private val connection = SolanaConnection(rpcUrl)
    private val driver = Rpc20Driver(rpcUrl, networkDriver)

    /**
     * The origin of the flow for onramping via Phantom (menu screen, pool)
     */
    internal var origin by mutableStateOf<NavScreenProvider?>(null)

    internal val keyPair = Box.keypair()
    val curvePublicKey: String?
        get() = keyPair.publicKey.toPublicKey().base58()

    /**
     * The state of the deeplink onramp
     */
    var deeplinkState by mutableStateOf(PhantomDeeplinkState.IDLE)

    internal val errors = MutableSharedFlow<PhantomOnRampError>()

    /**
     * The amount to transfer selected by the user
     */
    var amount: Fiat? = null
        set(value) {
            field = value
            if (value != null) {
                scope.launch {
                    createTransaction()
                        .onFailure {
                            errors.emit(PhantomOnRampError.FailedToCreateTransaction(message = it.message))
                        }.onSuccess {
                            unsignedTransaction = it
                            deeplinkState = PhantomDeeplinkState.SIGNING
                        }
                }
            }
        }

    /**
     * The unsigned transaction to be signed by Phantom
     */
    internal var unsignedTransaction: Transaction? = null

    /**
     * The signed transaction from Phantom
     */
    internal var signedTransaction: String? = null

    /**
     * The public key of the encryption key used by Phantom
     */
    internal var phantomEncryptionPublicKey: List<Byte>? = null

    /**
     * The connection details from Phantom containing the wallet's public key and session token
     */
    internal var walletConnection: PhantomWalletConnection? = null

    /**
     * The connection result from Phantom's deeplink
     */
    private var connectionResult: PhantomConnectionResult? = null

    /**
     * The signing result from Phantom's deeplink
     */
    private var signingResult: PhantomSigningResult? = null

    /**
     * Decrypt the connection result from Phantom's deeplink
     */
    fun decrypt(connectionResult: PhantomConnectionResult) {
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
                        runCatching { Json.decodeFromString<PhantomWalletConnection>(it) }
                            .onFailure { error ->
                                scope.launch {
                                    errors.emit(
                                        PhantomOnRampError.DeserializationError(
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
            deeplinkState = PhantomDeeplinkState.CONNECTED
        } catch (e: Exception) {
            scope.launch {
                errors.emit(PhantomOnRampError.DecryptionError(e.message, cause = e))
            }
        }
    }

    /**
     * Decrypt the signing result from Phantom's deeplink
     */
    fun decrypt(signingResult: PhantomSigningResult) {
        this.signingResult = signingResult
        try {
            val data = signingResult.encryptedData.boxOpen(
                privateKey = keyPair.secretKey.map { it.toByte() },
                publicKey = connectionResult!!.encryptionPublicKey,
                nonce = signingResult.nonce,
            ).map { String(it.toByteArray()) }
                .fold(
                    onSuccess = {
                        runCatching { Json.decodeFromString<PhantomSignedTransaction>(it) }
                            .onFailure { error ->
                                scope.launch {
                                    errors.emit(
                                        PhantomOnRampError.DeserializationError(
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
            deeplinkState = PhantomDeeplinkState.SIGNED
        } catch (e: Exception) {
            scope.launch {
                errors.emit(
                    PhantomOnRampError.DecryptionError(e.message, cause = e)
                )
            }
        }
    }

    suspend fun sendTransaction() {
        val transaction = signedTransaction ?: return
        withContext(Dispatchers.IO) {
            deeplinkState = PhantomDeeplinkState.TRANSACTING
            // build rpc request
            driver.sendTransaction(transaction)
                .onSuccess {
                    deeplinkState = PhantomDeeplinkState.TRANSACTED
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
                    errors.emit(
                        PhantomOnRampError.FailedToSendTransaction(
                            code = code ?: -99,
                            message = error.message,
                            cause = error
                        )
                    )
                }
        }
    }

    /**
     * Set the origin of the flow for onramping via Phantom
     */
    fun setOrigin(provider: NavScreenProvider?) {
        origin = provider
    }

    /**
     * Start the deeplink process
     */
    fun start() {
        deeplinkState = if (walletConnection != null) {
            PhantomDeeplinkState.CONNECTED
        } else {
            PhantomDeeplinkState.STARTED
        }
    }

    /**
     * Reset the state of the onramp flow
     */
    fun reset() {
        deeplinkState = PhantomDeeplinkState.IDLE
        amount = null
        unsignedTransaction = null
        signedTransaction = null
        signingResult = null
    }

    private suspend fun createTransaction(): Result<Transaction> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val usdcMint = Mint.usdc

                // selected USDC to request/transfer
                val quarks = requireNotNull(amount?.quarks)
                // connected phantom wallet
                val phantomWallet = requireNotNull(walletConnection?.publicKey)

                // user's owner cluster
                val owner = requireNotNull(userManager.accountCluster)

                val sender = PublicKey(phantomWallet.bytes)

                // ATAs for sender (phantom) and recipient (user in Flipcash)
                val senderTokenAccount = PublicKey.deriveAssociatedAccount(
                    owner = PublicKey(sender.base58()),
                    mint = PublicKey(usdcMint.base58())
                )


                val destinationTokenAccount = PublicKey.deriveAssociatedAccount(
                    owner = PublicKey(owner.depositAddress.base58()),
                    mint = PublicKey(usdcMint.base58())
                )

                val senderAtaExists = driver.doesAccountExist(senderTokenAccount.publicKey)
                    .onFailure {
                        it.printStackTrace()
                    }.getOrNull() != null
                val recipientAtaExists = driver.doesAccountExist(destinationTokenAccount.publicKey)
                    .onFailure {
                        it.printStackTrace()
                    }.getOrNull() != null

                println("sender: ${sender.base58()}")
                println("owner: ${owner.depositAddress.base58()}")
                println("senderTokenAccount(exists: $senderAtaExists): ${senderTokenAccount.publicKey.base58()}")
                println("destinationTokenAccount(exists: $recipientAtaExists): ${destinationTokenAccount.publicKey.base58()}")

                val instructions = mutableListOf<TransactionInstruction>()

                if (!senderAtaExists) {
                    instructions.add(
                        createAssociatedTokenAccountInstruction(
                            payer = sender,
                            walletAddress = sender,
                            ata = senderTokenAccount.publicKey,
                            mint = usdcMint
                        )
                    )
                }

                if (!recipientAtaExists) {
                    instructions.add(
                        createAssociatedTokenAccountInstruction(
                            payer = sender,
                            walletAddress = owner.depositAddress,
                            ata = destinationTokenAccount.publicKey,
                            mint = usdcMint
                        )
                    )
                }

                // Add the SPL Token Transfer instruction
                instructions.add(
                    createSplTransfer(
                        sender = sender,
                        sourceTokenAccount = senderTokenAccount.publicKey,
                        destinationTokenAccount = destinationTokenAccount.publicKey,
                        quarks = quarks,
                    )
                )

                // build transaction
                val message = Message.Builder()
                    .apply { instructions.forEach { addInstruction(it) } }
                    .setRecentBlockhash(connection.getLatestBlockhash())
                    .build()

                Transaction(message)
            }
        }
    }
}

enum class PhantomDeeplinkState {
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