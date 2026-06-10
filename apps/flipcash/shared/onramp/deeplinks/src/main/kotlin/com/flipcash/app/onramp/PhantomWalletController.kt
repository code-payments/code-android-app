package com.flipcash.app.onramp

import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.UsdcLiquidtyPool
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.opencode.exchange.VerifiedFiat
import com.getcode.opencode.internal.solana.extensions.deriveAssociatedAccount
import com.getcode.opencode.internal.solana.model.SwapId
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.transactions.FundSwapPool
import com.getcode.opencode.model.transactions.LiquidityPool
import com.getcode.opencode.model.transactions.SwapFundingSource
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.TransactionBuilder
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.base58
import com.getcode.solana.rpc.RpcConfig
import com.getcode.solana.rpc.RpcException
import com.getcode.solana.rpc.SolanaConnection
import com.getcode.solana.rpc.getBalance
import com.getcode.solana.rpc.getAccountData
import com.getcode.solana.rpc.getTokenAccountBalance
import com.getcode.solana.rpc.sendTransaction
import com.getcode.solana.rpc.simulateTransaction
import com.getcode.utils.ErrorUtils
import com.getcode.utils.TraceType
import com.getcode.utils.base64
import com.getcode.utils.trace
import com.getcode.vendor.Base58
import com.solana.networking.Rpc20Driver
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.PhantomSdk
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.PhantomWalletException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface PhantomSwapResult {
    data class WithSwapId(val swapId: SwapId) : PhantomSwapResult
    data class DepositCompleted(val amount: Long) : PhantomSwapResult
}

class PhantomWalletController @Inject constructor(
    private val userManager: UserManager,
    private val userFlags: UserFlagsCoordinator,
    private val transactionController: TransactionOperations,
    private val rpcConfig: RpcConfig,
    private val phantomSdk: PhantomSdk,
    private val phantomConnector: PhantomWalletConnector,
) {
    private val connection by lazy { SolanaConnection(rpcConfig.rpcUrl) }
    private val driver by lazy { Rpc20Driver(rpcConfig.rpcUrl, rpcConfig.networkDriver) }

    suspend fun connectWallet(
    ): Result<Unit> {
        return doConnect()
    }

    /**
     * Connect and swap in a single ceremony. The transparent overlay stays alive
     * across both the connect and sign deeplink round-trips so the app never
     * flashes between hops.
     */
    suspend fun connectAndSwap(
        amount: VerifiedFiat,
        fee: LocalFiat,
        token: Token,
        onConnectLaunching: () -> Unit = {},
        onSignLaunching: (SwapId) -> Unit = {},
    ): Result<PhantomSwapResult> {
        phantomConnector.beginCeremony()
        return try {
            onConnectLaunching()
            doConnect().getOrElse { return Result.failure(it) }

            executeSwap(
                amount = amount,
                fee = fee,
                token = token,
                onBeforeSign = { swapId ->
                    onSignLaunching(swapId)
                },
            )
        } finally {
            phantomConnector.endCeremony()
        }
    }

    private suspend fun doConnect(): Result<Unit> {
        return when (val result = phantomSdk.connect(phantomConnector)) {
            is ConnectResult.Success -> Result.success(Unit)
            is ConnectResult.Cancelled -> Result.failure(
                DeeplinkOnRampError.WalletProvidedError(
                    DeeplinkError.UserRejectedRequest,
                    message = result.reason ?: "User cancelled connection"
                )
            )
            is ConnectResult.Error -> Result.failure(mapConnectorError(result.cause))
        }
    }

    suspend fun disconnect() {
        try { phantomSdk.logout() } catch (_: Exception) { }
    }

    private suspend fun getSolanaAddress(): PublicKey {
        val address = phantomSdk.solana.getAddress()
            ?: error("Phantom wallet not connected")
        return PublicKey(Base58.decode(address).toList())
    }

    suspend fun executeSwap(
        amount: VerifiedFiat,
        fee: LocalFiat,
        token: Token,
        onBeforeSign: (suspend (SwapId) -> Unit)? = null,
    ): Result<PhantomSwapResult> {
        val sender = getSolanaAddress()
        val quarks = amount.localFiat.underlyingTokenAmount.quarks

        checkBalances(sender, quarks)
            .getOrElse { return Result.failure(it) }

        val isUsdfDeposit = token.address == Mint.usdf
        if (isUsdfDeposit) {
            return executeUsdfDeposit(sender, amount)
        }

        // Launchpad token buy — existing flow
        val (tx, swapId) = buildSwapTransaction(sender, amount)
            .getOrElse { return Result.failure(it) }

        onBeforeSign?.invoke(swapId)

        val signedTxBase58 = try {
            phantomSdk.solana.signTransaction(tx.encode().base64)
        } catch (e: Exception) {
            return Result.failure(mapConnectorError(e))
        }

        val signature = firstSignature(signedTxBase58)
            ?: return Result.failure(
                DeeplinkOnRampError.FailedToCreateTransaction(
                    message = "Could not extract signature from signed transaction"
                )
            )

        sendSwapTransaction(signedTxBase58, signature, amount, fee, token, swapId)
            .getOrElse { return Result.failure(it) }

        return Result.success(PhantomSwapResult.WithSwapId(swapId))
    }

    private suspend fun executeUsdfDeposit(
        sender: PublicKey,
        amount: VerifiedFiat,
    ): Result<PhantomSwapResult> {
        val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }
        val quarks = amount.localFiat.underlyingTokenAmount.quarks

        val tx = buildDepositTransaction(sender, owner.authorityPublicKey, quarks)
            .getOrElse { return Result.failure(it) }

        val signedTxBase58 = try {
            phantomSdk.solana.signTransaction(tx.encode().base64)
        } catch (e: Exception) {
            return Result.failure(mapConnectorError(e))
        }

        sendUsdfDepositTransaction(signedTxBase58)
            .getOrElse { return Result.failure(it) }

        return Result.success(PhantomSwapResult.DepositCompleted(quarks))
    }

    internal suspend fun checkBalances(
        sender: PublicKey,
        requiredUsdcQuarks: Long,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val solBalance = driver.getBalance(sender)
                .getOrElse { return@withContext Result.failure(it) }
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
            val usdcBalance = driver.getTokenAccountBalance(usdcAta.publicKey)
                .getOrElse { return@withContext Result.failure(it) }
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

    private suspend fun buildSwapTransaction(
        sender: PublicKey,
        amount: VerifiedFiat,
    ): Result<Pair<SolanaTransaction, SwapId>> {
        return withContext(Dispatchers.IO) {
            try {
                val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }

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
                    amount = amount.localFiat.underlyingTokenAmount.quarks,
                    pool = swapPool,
                    blockhash = Hash(recentBlockhash),
                    swapId = swapId,
                )

                // Simulate
                driver.simulateTransaction(transaction.encode().base64)
                    .getOrElse { cause ->
                        return@withContext Result.failure(
                            DeeplinkOnRampError.FailedToSimulateTransaction(
                                message = cause.message, cause = cause
                            )
                        )
                    }

                Result.success(transaction to swapId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trace("External swap failed", type = TraceType.Error, error = e)
                Result.failure(
                    DeeplinkOnRampError.FailedToCreateTransaction(message = e.message, cause = e)
                )
            }
        }
    }

    private suspend fun buildDepositTransaction(
        sender: PublicKey,
        owner: PublicKey,
        amount: Long,
    ): Result<SolanaTransaction> {
        return withContext(Dispatchers.IO) {
            try {
                val recentBlockhash = connection.getLatestBlockhash()

                val poolAddress = FundSwapPool.CoinbaseStableSwapper.poolAddress
                val poolData = driver.getAccountData(poolAddress).getOrThrow()
                val pool = FundSwapPool.CoinbaseStableSwapper.fromAccountData(poolData)

                val transaction = TransactionBuilder.usdfDeposit(
                    owner = owner,
                    sender = sender,
                    amount = amount,
                    feeRecipient = pool.feeRecipient,
                    blockhash = Hash(recentBlockhash),
                )

                driver.simulateTransaction(transaction.encode().base64)
                    .getOrElse { cause ->
                        return@withContext Result.failure(
                            DeeplinkOnRampError.FailedToSimulateTransaction(
                                message = cause.message, cause = cause
                            )
                        )
                    }

                Result.success(transaction)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trace("USDF deposit tx build failed", type = TraceType.Error, error = e)
                Result.failure(
                    DeeplinkOnRampError.FailedToCreateTransaction(message = e.message, cause = e)
                )
            }
        }
    }

    private suspend fun sendUsdfDepositTransaction(
        signedTxBase58: String,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Submit USDC→USDF swap to Solana — USDF lands directly in the
            // deposit PDA ATA, so no server-side USDC sweep is needed.
            driver.sendTransaction(signedTxBase58)
                .map { }
                .onFailure { error ->
                    val code = (error as? RpcException)?.code?.toLong()
                    when {
                        error is RpcException && error.isBlockhashNotFound -> {
                            ErrorUtils.handleError(error)
                            return@withContext Result.failure(
                                DeeplinkOnRampError.TransactionExpired(
                                    message = error.message, cause = error,
                                )
                            )
                        }
                        else -> {
                            ErrorUtils.handleError(error)
                            return@withContext Result.failure(
                                DeeplinkOnRampError.FailedToSendTransaction(
                                    code = code ?: -99L, message = error.message, cause = error,
                                )
                            )
                        }
                    }
                }

            Result.success(Unit)
        }
    }

    private suspend fun sendSwapTransaction(
        signedTxBase58: String,
        signature: Signature,
        amount: VerifiedFiat,
        fee: LocalFiat,
        token: Token,
        swapId: SwapId,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // 1. Server-side buy intent
            initiateBuy(
                amount = amount,
                fee = fee,
                token = token,
                swapId = swapId,
                signature = signature,
            ).getOrElse { cause ->
                return@withContext Result.failure(
                    DeeplinkOnRampError.FailedToSubmitBuyToServer(message = cause.message, cause = cause)
                )
            }

            // 2. Submit to Solana
            driver.sendTransaction(signedTxBase58)
                .map { }
                .onFailure { error ->
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

                    when {
                        // Detect expired blockhash — occurs when the Phantom wallet
                        // round-trip exceeds ~60 seconds (Solana's blockhash lifetime).
                        error is RpcException && error.isBlockhashNotFound -> {
                            ErrorUtils.handleError(error)
                            return@withContext Result.failure(
                                DeeplinkOnRampError.TransactionExpired(
                                    message = error.message,
                                    cause = error,
                                )
                            )
                        }
                        else -> {
                            ErrorUtils.handleError(error)
                            return@withContext Result.failure(
                                DeeplinkOnRampError.FailedToSendTransaction(
                                    code = code ?: -99L,
                                    message = error.message,
                                    cause = error,
                                )
                            )
                        }
                    }
                }

            Result.success(Unit)
        }
    }

    private suspend fun initiateBuy(
        amount: VerifiedFiat,
        fee: LocalFiat,
        token: Token,
        swapId: SwapId?,
        signature: Signature,
    ): Result<SwapId> {
        val owner = requireNotNull(userManager.accountCluster) { "Owner is null" }
        return withContext(NonCancellable) {
            transactionController.buy(
                owner = owner,
                amount = amount,
                feeAmount = fee,
                of = token,
                swapId = swapId,
                source = SwapFundingSource.ExternalWallet(signature.bytes),
                fund = { Result.success(Unit) }
            )
        }
    }

    companion object {
        // ~0.0065 SOL: covers up to 3 ATA rent exemptions + priority fee + base fee
        internal const val MINIMUM_SOL_LAMPORTS = 6_500_000L
    }
}

private val PROGRAM_ERROR_REGEX =
    Regex("""Error processing Instruction (\d+): custom program error: 0x([0-9a-fA-F]+)""")

private fun firstSignature(base58: String): Signature? {
    return SolanaTransaction.fromList(Base58.decode(base58).toList())?.identifier
}

private fun mapConnectorError(cause: Throwable): DeeplinkOnRampError {
    if (cause is PhantomWalletException) {
        val deeplinkError = DeeplinkError.fromCode(cause.code.toLong())
        return DeeplinkOnRampError.WalletProvidedError(deeplinkError, message = cause.message)
    }
    return DeeplinkOnRampError.FailedToCreateTransaction(message = cause.message, cause = cause)
}
