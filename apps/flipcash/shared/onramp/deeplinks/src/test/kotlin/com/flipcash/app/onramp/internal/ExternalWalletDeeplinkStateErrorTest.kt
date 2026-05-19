package com.flipcash.app.onramp.internal

import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.PhantomWalletController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.solana.rpc.RpcConfig
import com.solana.networking.HttpNetworkDriver
import dev.bmcreations.phantom.connect.ConnectResult
import dev.bmcreations.phantom.connect.PhantomSdk
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.PhantomWalletException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalWalletDeeplinkStateErrorTest {

    private val userManager = mockk<UserManager>(relaxed = true)
    private val transactionController = mockk<TransactionOperations>(relaxed = true)
    private val networkDriver = mockk<HttpNetworkDriver>(relaxed = true)
    private val rpcConfig = RpcConfig(networkDriver = networkDriver, rpcUrl = "https://localhost")
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
    private val phantomSdk = mockk<PhantomSdk>(relaxed = true)
    private val phantomConnector = mockk<PhantomWalletConnector>(relaxed = true)

    private lateinit var controller: PhantomWalletController

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

    @Test
    fun `user cancellation during connect returns WalletProvidedError with UserRejectedRequest`() = runTest {
        coEvery { phantomSdk.connect(phantomConnector) } returns ConnectResult.Cancelled("User rejected the request")

        val result = controller.connectWallet()

        assertTrue(result.isFailure, "Expected failure")
        val error = result.exceptionOrNull()
        assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError, got $error")
        assertEquals(DeeplinkError.UserRejectedRequest, error.error)
    }

    @Test
    fun `PhantomWalletException during connect maps to WalletProvidedError`() = runTest {
        coEvery { phantomSdk.connect(phantomConnector) } returns ConnectResult.Error(
            PhantomWalletException(4001, "User rejected the request")
        )

        val result = controller.connectWallet()

        assertTrue(result.isFailure, "Expected failure")
        val error = result.exceptionOrNull()
        assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError")
        assertEquals(DeeplinkError.UserRejectedRequest, error.error)
    }

    @Test
    fun `generic error during connect maps to FailedToCreateTransaction`() = runTest {
        coEvery { phantomSdk.connect(phantomConnector) } returns ConnectResult.Error(
            RuntimeException("Something broke")
        )

        val result = controller.connectWallet()

        assertTrue(result.isFailure, "Expected failure")
        assertTrue(result.exceptionOrNull() is DeeplinkOnRampError.FailedToCreateTransaction, "Expected FailedToCreateTransaction")
    }

    @Test
    fun `successful connect returns success`() = runTest {
        coEvery { phantomSdk.connect(phantomConnector) } returns ConnectResult.Success(mockk(relaxed = true))

        val result = controller.connectWallet()

        assertTrue(result.isSuccess, "Expected success")
    }
}
