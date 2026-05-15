package com.flipcash.app.onramp

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletConnection
import com.flipcash.app.core.tokens.SwapPurpose
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.internal.model.thirdparty.UsdcLiquidtyPool
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.FundSwapPool
import com.getcode.opencode.model.transactions.LiquidityPool
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
import com.getcode.solana.rpc.RpcConfig
import com.getcode.solana.rpc.RpcException
import com.getcode.solana.rpc.SolanaConnection
import com.getcode.solana.rpc.doesAccountExist
import com.getcode.solana.rpc.getBalance
import com.getcode.solana.rpc.getAccountData
import com.getcode.solana.rpc.getTokenAccountBalance
import com.getcode.solana.rpc.sendTransaction
import com.getcode.solana.rpc.simulateTransaction
import com.getcode.solana.transactions.inspect
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base64
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import com.solana.networking.Rpc20Driver
import com.solana.transaction.Message
import com.solana.transaction.toUnsignedTransaction
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dev.bmcreations.phantom.connect.WalletConnectorResult
import dev.bmcreations.phantom.connect.WalletSignResult
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.PhantomWalletException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ActivityRetainedScoped
class ExternalWalletOnRampController @Inject constructor(
    private val userManager: UserManager,
    private val userFlags: UserFlagsCoordinator,
    private val transactionController: TransactionOperations,
    private val rpcConfig: RpcConfig,
    private val phantomConnector: PhantomWalletConnector,
) {
    private val _state = MutableStateFlow<ExternalWalletOnRampState>(ExternalWalletOnRampState.Idle)
    val state: StateFlow<ExternalWalletOnRampState> = _state.asStateFlow()

    private val _pendingNavigation = MutableSharedFlow<AppRoute>(extraBufferCapacity = 1)
    val pendingNavigation: SharedFlow<AppRoute> = _pendingNavigation.asSharedFlow()

    private val _flowExitRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flowExitRequests: SharedFlow<Unit> = _flowExitRequests.asSharedFlow()

    private val _amount = MutableStateFlow<VerifiedFiat?>(null)
    val amount: StateFlow<VerifiedFiat?> = _amount.asStateFlow()

    private val _feeAmount = MutableStateFlow<LocalFiat?>(null)
    val feeAmount: StateFlow<LocalFiat?> = _feeAmount.asStateFlow()

    private val _tokenToPurchase = MutableStateFlow<Token?>(null)
    val tokenToPurchase: StateFlow<Token?> = _tokenToPurchase.asStateFlow()

    private val connection by lazy { SolanaConnection(rpcConfig.rpcUrl) }
    private val driver by lazy { Rpc20Driver(rpcConfig.rpcUrl, rpcConfig.networkDriver) }

    fun start(origin: AppRoute, provider: OnRampProvider.UsesDeeplinks) {
        reset()
        _state.value = ExternalWalletOnRampState.Started(origin, provider)
    }

    fun setAmount(amount: VerifiedFiat?, feeAmount: LocalFiat? = null) {
        _amount.value = amount
        _feeAmount.value = feeAmount
    }

    fun setTokenToPurchase(token: Token?) {
        _tokenToPurchase.value = token
    }

    fun emitPendingNavigation(route: AppRoute) {
        _pendingNavigation.tryEmit(route)
    }

    fun requestFlowExit() {
        _flowExitRequests.tryEmit(Unit)
    }

    fun reset() {
        _state.value = ExternalWalletOnRampState.Idle
        _amount.value = null
        _feeAmount.value = null
        _tokenToPurchase.value = null
    }

    fun transitionTo(state: ExternalWalletOnRampState) {
        _state.value = state
    }

    suspend fun connectAndSign(origin: AppRoute) {
        val currentState = _state.value
        val provider = currentState.provider ?: return

        phantomConnector.beginCeremony()
        try {
            // 1. Connect
            transitionTo(
                ExternalWalletOnRampState.Connecting(origin = origin, provider = provider)
            )

            when (val connectResult = phantomConnector.connect()) {
                is WalletConnectorResult.Connected -> {
                    val publicKey = PublicKey(Base58.decode(connectResult.publicKeyBase58).toList())
                    val walletConnection = ExternalWalletConnection(
                        publicKey = publicKey,
                        session = connectResult.publicKeyBase58,
                    )
                    transitionTo(
                        ExternalWalletOnRampState.Connected(
                            origin = origin,
                            provider = provider,
                            connection = walletConnection,
                        )
                    )
                }

                is WalletConnectorResult.Cancelled -> {
                    fail(
                        DeeplinkOnRampError.WalletProvidedError(
                            DeeplinkError.UserRejectedRequest,
                            message = connectResult.reason ?: "User cancelled connection"
                        ),
                        currentState
                    )
                    return
                }

                is WalletConnectorResult.Error -> {
                    fail(mapConnectorError(connectResult.cause), currentState)
                    return
                }
            }

            // 2. Build transaction (waits for amount if needed)
            val amount = _amount.value
            if (amount != null) {
                when (origin) {
                    is AppRoute.Token.Info,
                    is AppRoute.Token.CurrencyCreator -> createAndValidateSwapTransaction()
                    else -> createAndValidateDepositTransaction()
                }
            } else {
                // Amount not yet set — handler will navigate to amount entry.
                // connectAndSign returns here; the handler re-triggers tx building
                // once amount is set via the Connected state observation.
                return
            }

            // 3. Sign + Send
            signAndSend()
        } catch (e: Exception) {
            fail(
                DeeplinkOnRampError.FailedToCreateTransaction(message = e.message, cause = e),
                _state.value
            )
        } finally {
            phantomConnector.endCeremony()
        }
    }

    suspend fun signAndSend() {
        val signingState = _state.value
        if (signingState !is ExternalWalletOnRampState.Signing) return

        val txBase58 = Base58.encode(signingState.unsignedTransaction.toByteArray())

        when (val signResult = phantomConnector.signTransaction(txBase58)) {
            is WalletSignResult.Success -> {
                val signedTxBase58 = signResult.signature
                val signature = firstSignature(signedTxBase58)
                    ?: error("Could not extract signature from signed transaction")

                transitionTo(
                    ExternalWalletOnRampState.Signed(
                        origin = signingState.origin,
                        provider = signingState.provider,
                        connection = signingState.connection,
                        signedTransaction = signedTxBase58,
                        signature = signature,
                        amount = signingState.amount,
                        fee = signingState.fee,
                        token = signingState.token,
                        swapId = signingState.swapId,
                    )
                )
            }

            is WalletSignResult.Error -> {
                fail(mapConnectorError(signResult.cause), signingState)
                return
            }
        }

        sendTransaction()
    }

    suspend fun createAndValidateSwapTransaction() {
        val state = _state.value
        if (state !is ExternalWalletOnRampState.Connected) return
        val amount = _amount.value ?: return
        val fee = _feeAmount.value ?: LocalFiat.Zero

        val sender = PublicKey(state.connection.publicKey.bytes)
        val balanceCheck = checkExternalWalletBalances(
            sender = sender,
            requiredUsdcQuarks = amount.localFiat.underlyingTokenAmount.quarks,
        )
        if (balanceCheck.isFailure) {
            val throwable = balanceCheck.exceptionOrNull() ?: return
            val error = throwable as? DeeplinkOnRampError
                ?: DeeplinkOnRampError.FailedToCreateTransaction(message = throwable.message, cause = throwable)
            fail(error, state)
            return
        }

        createUsdcToUsdfSwapTransaction(state, amount.localFiat)
            .onFailure { fail(DeeplinkOnRampError.FailedToCreateTransaction(message = it.message, cause = it), state) }
            .fold(
                onSuccess = { (transaction, swapId) ->
                    withContext(Dispatchers.IO) {
                        driver.simulateTransaction(transaction.encode().base64)
                            .fold(
                                onSuccess = { Result.success(transaction to swapId) },
                                onFailure = { Result.failure(it) }
                            )
                    }
                },
                onFailure = { Result.failure(it) }
            )
            .onFailure { fail(DeeplinkOnRampError.FailedToSimulateTransaction(message = it.message, cause = it), state) }
            .onSuccess { (transaction, swapId) ->
                transitionTo(
                    ExternalWalletOnRampState.Signing(
                        origin = state.origin,
                        provider = state.provider,
                        connection = state.connection,
                        unsignedTransaction = transaction.encode().toList(),
                        amount = amount,
                        fee = fee,
                        token = _tokenToPurchase.value,
                        swapId = swapId,
                    )
                )
            }
    }

    suspend fun createAndValidateDepositTransaction() {
        val state = _state.value
        if (state !is ExternalWalletOnRampState.Connected) return
        val amount = _amount.value ?: return
        val fee = _feeAmount.value ?: LocalFiat.Zero

        createDepositTransaction(state, amount.localFiat)
            .onFailure { fail(DeeplinkOnRampError.FailedToCreateTransaction(message = it.message, cause = it), state) }
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
            .onFailure { fail(DeeplinkOnRampError.FailedToSimulateTransaction(message = it.message, cause = it), state) }
            .onSuccess { transaction ->
                transitionTo(
                    ExternalWalletOnRampState.Signing(
                        origin = state.origin,
                        provider = state.provider,
                        connection = state.connection,
                        unsignedTransaction = transaction.serialize().toList(),
                        amount = amount,
                        fee = fee,
                        token = _tokenToPurchase.value,
                        swapId = null,
                    )
                )
            }
    }

    suspend fun sendTransaction() {
        val state = _state.value
        if (state !is ExternalWalletOnRampState.Signed) return

        transitionTo(
            ExternalWalletOnRampState.Transacting(
                origin = state.origin,
                provider = state.provider,
                token = state.token,
                swapId = state.swapId,
            )
        )

        withContext(Dispatchers.IO) {
            val sendIt = suspend {
                driver.sendTransaction(state.signedTransaction)
                    .onSuccess {
                        transitionTo(
                            ExternalWalletOnRampState.Transacted(
                                origin = state.origin,
                                provider = state.provider,
                                token = state.token,
                                swapId = state.swapId,
                            )
                        )
                    }.onFailure { error ->
                        val code = (error as? RpcException)?.code?.toLong()
                        val errorMessage = error.message.orEmpty()
                        val programErrorMatch = PROGRAM_ERROR_REGEX.find(errorMessage)
                        trace(
                            message = "Unexpected error sending solana transaction",
                            type = TraceType.Error,
                            metadata = {
                                if (error is RpcException) {
                                    "code" to error.code
                                }
                                "message" to errorMessage
                                if (programErrorMatch != null) {
                                    "instructionIndex" to programErrorMatch.groupValues[1]
                                    "programError" to "0x${programErrorMatch.groupValues[2]}"
                                }
                            }
                        )
                        ErrorUtils.handleError(error)
                        fail(
                            DeeplinkOnRampError.FailedToSendTransaction(
                                code = code ?: -99L,
                                message = error.message,
                                cause = error
                            ),
                            state
                        )
                    }
            }

            if (state.origin is AppRoute.Token.Info || state.origin is AppRoute.Token.CurrencyCreator) {
                initiateBuy(state)
                    .fold(
                        onSuccess = {
                            sendIt()
                            Result.success(Unit)
                        },
                        onFailure = {
                            fail(DeeplinkOnRampError.FailedToSubmitBuyToServer(message = it.message), state)
                            Result.failure(it)
                        }
                    )
            } else {
                sendIt()
            }
        }
    }

    private suspend fun checkExternalWalletBalances(
        sender: PublicKey,
        requiredUsdcQuarks: Long,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val solBalance = driver.getBalance(sender).getOrElse { return@withContext Result.failure(it) }
            if (solBalance < MINIMUM_SOL_LAMPORTS) {
                return@withContext Result.failure(
                    DeeplinkOnRampError.InsufficientSol(
                        requiredLamports = MINIMUM_SOL_LAMPORTS,
                        actualLamports = solBalance,
                    )
                )
            }

            val usdcAta = PublicKey.deriveAssociatedAccount(
                owner = PublicKey(sender.base58()),
                mint = PublicKey(Mint.usdc.base58()),
            )
            val usdcBalance = driver.getTokenAccountBalance(usdcAta.publicKey).getOrElse { return@withContext Result.failure(it) }
            if (usdcBalance < requiredUsdcQuarks) {
                return@withContext Result.failure(
                    DeeplinkOnRampError.InsufficientUsdc(
                        requiredQuarks = requiredUsdcQuarks,
                        actualQuarks = usdcBalance,
                    )
                )
            }

            Result.success(Unit)
        }
    }

    private suspend fun createUsdcToUsdfSwapTransaction(
        state: ExternalWalletOnRampState.Connected,
        amount: LocalFiat,
    ): Result<Pair<SolanaTransaction, SwapId>> {
        return withContext(Dispatchers.IO) {
            try {
                val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }
                val externalWallet = requireNotNull(state.connection.publicKey) { "Wallet connection is null" }
                val sender = PublicKey(externalWallet.bytes)

                val swapId = SwapId.generate()
                val recentBlockhash = connection.getLatestBlockhash()

                val liquidityPool = userFlags.resolvedFlags.value.usdcOnRampLiquidityPool.effectiveValue
                val swapPool = when (liquidityPool) {
                    UsdcLiquidtyPool.CoinbaseStableSwapper -> {
                        val poolAddress = FundSwapPool.CoinbaseStableSwapper.poolAddress
                        val poolData = driver.getAccountData(poolAddress).getOrThrow()
                        FundSwapPool.CoinbaseStableSwapper.fromAccountData(poolData)
                    }
                    UsdcLiquidtyPool.Unknown,
                    UsdcLiquidtyPool.Flipcash -> FundSwapPool.Usdf(LiquidityPool.usdf)
                }

                trace("Building USDC -> USDF fund swap using $liquidityPool LP")

                val transaction = TransactionBuilder.usdcFundSwap(
                    owner = owner.authorityPublicKey,
                    sender = sender,
                    amount = amount.underlyingTokenAmount.quarks,
                    pool = swapPool,
                    blockhash = Hash(recentBlockhash),
                    swapId = swapId,
                )

                Result.success(transaction to swapId)
            } catch (e: Exception) {
                trace("External swap failed", type = TraceType.Error, error = e)
                Result.failure(e)
            }
        }
    }

    private suspend fun initiateBuy(state: ExternalWalletOnRampState.Signed): Result<SwapId> {
        val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }
        val token = requireNotNull(state.token) { "Token is null" }

        return withContext(NonCancellable) {
            transactionController.buy(
                owner = owner,
                amount = state.amount,
                feeAmount = state.fee,
                of = token,
                swapId = state.swapId,
                source = SwapFundingSource.ExternalWallet(state.signature.bytes),
                fund = { Result.success(Unit) }
            )
        }
    }

    private suspend fun createDepositTransaction(
        state: ExternalWalletOnRampState.Connected,
        amount: LocalFiat,
    ): Result<com.solana.transaction.Transaction> {
        return withContext(Dispatchers.IO) {
            try {
                val usdfMint = Mint.usdf
                val quarks = amount.underlyingTokenAmount.quarks
                val externalWallet = requireNotNull(state.connection.publicKey) { "Wallet connection is null" }
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

                val message = Message.Builder()
                    .setRecentBlockhash(connection.getLatestBlockhash())
                    .apply { instructions.forEach { addInstruction(it) } }
                    .build()

                message.inspect()

                val transaction = message.toUnsignedTransaction()

                Result.success(transaction)
            } catch (e: Exception) {
                trace("Transaction creation failed", type = TraceType.Error, error = e)
                Result.failure(e)
            }
        }
    }

    private fun fail(error: DeeplinkOnRampError, currentState: ExternalWalletOnRampState) {
        transitionTo(
            ExternalWalletOnRampState.Failed(
                error = error,
                origin = currentState.origin,
                provider = currentState.provider,
            )
        )
    }

    private fun mapConnectorError(cause: Throwable): DeeplinkOnRampError {
        if (cause is PhantomWalletException) {
            val deeplinkError = DeeplinkError.fromCode(cause.code.toLong())
            return DeeplinkOnRampError.WalletProvidedError(deeplinkError, message = cause.message)
        }
        return DeeplinkOnRampError.FailedToCreateTransaction(message = cause.message, cause = cause)
    }
}

// ~0.0065 SOL: covers up to 3 ATA rent exemptions + priority fee + base fee
private const val MINIMUM_SOL_LAMPORTS = 6_500_000L

private val PROGRAM_ERROR_REGEX =
    Regex("""Error processing Instruction (\d+): custom program error: 0x([0-9a-fA-F]+)""")

private fun firstSignature(base58: String): Signature? {
    return SolanaTransaction.fromList(Base58.decode(base58).toList())?.identifier
}

private val ExternalWalletOnRampState.origin: AppRoute?
    get() = when (this) {
        is ExternalWalletOnRampState.Idle -> null
        is ExternalWalletOnRampState.Started -> origin
        is ExternalWalletOnRampState.Connecting -> origin
        is ExternalWalletOnRampState.Connected -> origin
        is ExternalWalletOnRampState.Signing -> origin
        is ExternalWalletOnRampState.Signed -> origin
        is ExternalWalletOnRampState.Transacting -> origin
        is ExternalWalletOnRampState.Transacted -> origin
        is ExternalWalletOnRampState.Failed -> origin
    }

private val ExternalWalletOnRampState.provider: OnRampProvider.UsesDeeplinks?
    get() = when (this) {
        is ExternalWalletOnRampState.Idle -> null
        is ExternalWalletOnRampState.Started -> provider
        is ExternalWalletOnRampState.Connecting -> provider
        is ExternalWalletOnRampState.Connected -> provider
        is ExternalWalletOnRampState.Signing -> provider
        is ExternalWalletOnRampState.Signed -> provider
        is ExternalWalletOnRampState.Transacting -> provider
        is ExternalWalletOnRampState.Transacted -> provider
        is ExternalWalletOnRampState.Failed -> provider
    }
