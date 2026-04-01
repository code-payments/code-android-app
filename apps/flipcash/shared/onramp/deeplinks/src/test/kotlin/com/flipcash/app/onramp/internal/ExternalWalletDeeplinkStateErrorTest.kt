package com.flipcash.app.onramp.internal

import app.cash.turbine.test
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.onramp.deeplinks.ExternalWalletDeeplinkError
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.app.onramp.DeeplinkError
import com.flipcash.app.onramp.DeeplinkOnRampError
import com.flipcash.services.user.UserManager
import com.getcode.opencode.controllers.TransactionOperations
import com.flipcash.app.core.MainCoroutineRule
import com.ionspin.kotlin.crypto.box.Box
import com.ionspin.kotlin.crypto.box.BoxKeyPair
import com.solana.networking.HttpNetworkDriver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalWalletDeeplinkStateErrorTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val testScope = TestScope(mainCoroutineRule.dispatcher)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val transactionController = mockk<TransactionOperations>(relaxed = true)
    private val networkDriver = mockk<HttpNetworkDriver>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("com.getcode.utils.LoggingKt")
        every { com.getcode.utils.trace(any(), any(), any(), any(), any()) } returns Unit

        // Mock the native crypto keypair generation
        mockkObject(Box)
        every { Box.keypair() } returns mockk<BoxKeyPair>(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkObject(Box)
        unmockkStatic("com.getcode.utils.LoggingKt")
    }

    private fun createState(): ExternalWalletDeeplinkState {
        return ExternalWalletDeeplinkState(
            userManager = userManager,
            transactionController = transactionController,
            scope = testScope,
            rpcUrl = "https://localhost",
            networkDriver = networkDriver,
        )
    }

    @Test
    fun `wallet-provided error emits WalletProvidedError`() = runTest(mainCoroutineRule.dispatcher) {
        val state = createState()

        state.errors.test {
            state.handleWalletDeeplink(
                DeeplinkType.ExternalWalletConnection(
                    origin = OnRampDeeplinkOrigin.Menu,
                    result = null,
                    error = ExternalWalletDeeplinkError(
                        errorCode = "4001",
                        errorMessage = "User rejected the request"
                    )
                )
            )

            val error = awaitItem()
            assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError")
            val walletError = error as DeeplinkOnRampError.WalletProvidedError
            assertEquals(DeeplinkError.UserRejectedRequest, walletError.error)
            assertEquals("User rejected the request", walletError.message)
        }
    }

    @Test
    fun `wallet connection with null result and null error emits WalletProvidedError with Unknown`() = runTest(mainCoroutineRule.dispatcher) {
        val state = createState()

        state.errors.test {
            state.handleWalletDeeplink(
                DeeplinkType.ExternalWalletConnection(
                    origin = OnRampDeeplinkOrigin.Menu,
                    result = null,
                    error = null,
                )
            )

            val error = awaitItem()
            assertTrue(error is DeeplinkOnRampError.WalletProvidedError, "Expected WalletProvidedError")
            val walletError = error as DeeplinkOnRampError.WalletProvidedError
            assertEquals(DeeplinkError.Unknown, walletError.error)
            assertEquals("Something went wrong", walletError.message)
        }
    }
}
