package com.flipcash.app.onramp.internal

import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.PhantomWalletController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.rpc.RpcConfig
import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import dev.bmcreations.phantom.connect.PhantomSdk
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class ExternalWalletBalanceCheckTest {

    private val userManager = mockk<UserManager>(relaxed = true)
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
    private val transactionController = mockk<TransactionOperations>(relaxed = true)
    private val networkDriver = mockk<HttpNetworkDriver>()
    private val rpcConfig = RpcConfig(networkDriver = networkDriver, rpcUrl = "https://localhost")
    private val phantomSdk = mockk<PhantomSdk>(relaxed = true)
    private val phantomConnector = mockk<PhantomWalletConnector>(relaxed = true)

    private lateinit var controller: PhantomWalletController

    private val testPublicKey = PublicKey("11111111111111111111111111111111")

    @Before
    fun setUp() {
        controller = PhantomWalletController(
            userManager = userManager,
            userFlags = userFlags,
            transactionController = transactionController,
            rpcConfig = rpcConfig,
            phantomSdk = phantomSdk,
            phantomConnector = phantomConnector,
        )
    }

    private fun mockRpcResponses(solLamports: Long, usdcQuarks: Long) {
        coEvery { networkDriver.makeHttpRequest(any()) } answers {
            val body = firstArg<HttpRequest>().body.orEmpty()
            when {
                "getBalance" in body -> balanceJson(solLamports)
                "getTokenAccountBalance" in body -> tokenBalanceJson(usdcQuarks)
                else -> errorJson(-1, "unexpected method")
            }
        }
    }

    // ── Insufficient SOL ──

    @Test
    fun `insufficient SOL returns failure with InsufficientSol`() = runTest {
        mockRpcResponses(solLamports = 1_000L, usdcQuarks = 100_000_000L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure, "Expected failure")
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.InsufficientSol, "Expected InsufficientSol")
    }

    @Test
    fun `zero SOL returns failure with InsufficientSol`() = runTest {
        mockRpcResponses(solLamports = 0L, usdcQuarks = 100_000_000L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.InsufficientSol)
    }

    // ── Insufficient USDC ──

    @Test
    fun `insufficient USDC returns failure with InsufficientUsdc`() = runTest {
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 1_000L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure, "Expected failure")
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.InsufficientUsdc, "Expected InsufficientUsdc")
    }

    @Test
    fun `zero USDC returns failure with InsufficientUsdc`() = runTest {
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 0L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.InsufficientUsdc)
    }

    // ── RPC errors ──

    @Test
    fun `RPC error on getBalance returns failure`() = runTest {
        coEvery { networkDriver.makeHttpRequest(any()) } returns errorJson(-1, "Internal error")

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure, "Expected failure")
    }

    // ── Boundary: exact threshold passes ──

    @Test
    fun `exact SOL threshold passes balance check`() = runTest {
        mockRpcResponses(solLamports = PhantomWalletController.MINIMUM_SOL_LAMPORTS, usdcQuarks = 5_000_000L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 5_000_000L)

        assertTrue(result.isSuccess, "Expected success at exact SOL threshold")
    }

    @Test
    fun `SOL just below threshold fails with InsufficientSol`() = runTest {
        mockRpcResponses(solLamports = PhantomWalletController.MINIMUM_SOL_LAMPORTS - 1, usdcQuarks = 100_000_000L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.InsufficientSol)
    }

    @Test
    fun `USDC exactly matching required amount passes balance check`() = runTest {
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 10_000_000L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isSuccess, "Expected success with exact USDC amount")
    }

    @Test
    fun `USDC one quark below required fails with InsufficientUsdc`() = runTest {
        mockRpcResponses(solLamports = 10_000_000L, usdcQuarks = 9_999_999L)

        val result = controller.checkBalances(testPublicKey, requiredUsdcQuarks = 10_000_000L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.InsufficientUsdc)
    }

    companion object {
        private fun balanceJson(lamports: Long) =
            """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":$lamports},"id":"1"}"""

        private fun tokenBalanceJson(quarks: Long) =
            """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":{"amount":"$quarks","decimals":6,"uiAmount":${quarks / 1_000_000.0},"uiAmountString":"${quarks / 1_000_000.0}"}},"id":"1"}"""

        private fun errorJson(code: Int, message: String) =
            """{"jsonrpc":"2.0","error":{"code":$code,"message":"$message"},"id":"1"}"""
    }
}
