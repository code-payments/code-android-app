package com.flipcash.app.onramp.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.ExternalWalletOnRampController
import com.flipcash.app.onramp.ExternalWalletOnRampState
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.solana.rpc.RpcConfig
import com.solana.networking.HttpNetworkDriver
import dev.bmcreations.phantom.connect.WalletConnectorResult
import dev.bmcreations.phantom.connect.WalletSignResult
import dev.bmcreations.phantom.connect.wallet.PhantomWalletConnector
import dev.bmcreations.phantom.connect.wallet.PhantomWalletException
import io.mockk.coEvery
import io.mockk.coVerify
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
    private val phantomConnector = mockk<PhantomWalletConnector>(relaxed = true)

    private lateinit var controller: ExternalWalletOnRampController

    @Before
    fun setUp() {
        controller = ExternalWalletOnRampController(
            userManager = userManager,
            userFlags = userFlags,
            transactionController = transactionController,
            rpcConfig = rpcConfig,
            phantomConnector = phantomConnector,
        )
    }

    @Test
    fun `user cancellation during connect transitions to Failed with UserRejectedRequest`() = runTest {
        coEvery { phantomConnector.connect() } returns WalletConnectorResult.Cancelled("User rejected the request")

        controller.start(AppRoute.Sheets.Menu, OnRampProvider.Phantom)
        controller.connectAndSign(AppRoute.Sheets.Menu)

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed state, got $state")
        val error = state.error
        assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError, got $error")
        assertEquals(DeeplinkError.UserRejectedRequest, error.error)
    }

    @Test
    fun `PhantomWalletException during connect maps to WalletProvidedError`() = runTest {
        coEvery { phantomConnector.connect() } returns WalletConnectorResult.Error(
            PhantomWalletException(4001, "User rejected the request")
        )

        controller.start(AppRoute.Sheets.Menu, OnRampProvider.Phantom)
        controller.connectAndSign(AppRoute.Sheets.Menu)

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed state")
        val error = state.error
        assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError")
        assertEquals(DeeplinkError.UserRejectedRequest, error.error)
    }

    @Test
    fun `generic error during connect maps to FailedToCreateTransaction`() = runTest {
        coEvery { phantomConnector.connect() } returns WalletConnectorResult.Error(
            RuntimeException("Something broke")
        )

        controller.start(AppRoute.Sheets.Menu, OnRampProvider.Phantom)
        controller.connectAndSign(AppRoute.Sheets.Menu)

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed state")
        assertTrue(state.error is DeeplinkOnRampError.FailedToCreateTransaction, "Expected FailedToCreateTransaction")
    }

    @Test
    fun `endCeremony is always called even on connect failure`() = runTest {
        coEvery { phantomConnector.connect() } returns WalletConnectorResult.Cancelled("cancelled")

        controller.start(AppRoute.Sheets.Menu, OnRampProvider.Phantom)
        controller.connectAndSign(AppRoute.Sheets.Menu)

        coVerify(exactly = 1) { phantomConnector.beginCeremony() }
        coVerify(exactly = 1) { phantomConnector.endCeremony() }
    }
}
