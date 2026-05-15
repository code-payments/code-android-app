package com.flipcash.app.onramp.internal

import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletDeeplinkError
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.app.onramp.ExternalWalletOnRampController
import com.flipcash.app.onramp.ExternalWalletOnRampState
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.getcode.solana.rpc.RpcConfig
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.BoxKeyPair
import com.solana.networking.HttpNetworkDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
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

    private lateinit var controller: ExternalWalletOnRampController

    @Before
    fun setUp() {
        mockkObject(Box)
        every { Box.keypair() } returns mockk<BoxKeyPair>(relaxed = true)
        controller = ExternalWalletOnRampController(
            userManager = userManager,
            userFlags = userFlags,
            transactionController = transactionController,
            rpcConfig = rpcConfig,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(Box)
    }

    @Test
    fun `wallet-provided error transitions to Failed state`() {
        controller.handleWalletDeeplink(
            DeeplinkType.ExternalWalletConnection(
                origin = OnRampDeeplinkOrigin.Menu,
                result = null,
                error = ExternalWalletDeeplinkError(
                    errorCode = "4001",
                    errorMessage = "User rejected the request"
                )
            )
        )

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed state")
        val error = state.error
        assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError")
        assertEquals(DeeplinkError.UserRejectedRequest, error.error)
        assertEquals("User rejected the request", error.message)
    }

    @Test
    fun `wallet connection with null result and null error transitions to Failed with Unknown`() {
        controller.handleWalletDeeplink(
            DeeplinkType.ExternalWalletConnection(
                origin = OnRampDeeplinkOrigin.Menu,
                result = null,
                error = null,
            )
        )

        val state = controller.state.value
        assertTrue(state is ExternalWalletOnRampState.Failed, "Expected Failed state")
        val error = state.error
        assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError")
        assertEquals(DeeplinkError.Unknown, error.error)
        assertEquals("Something went wrong", error.message)
    }
}
