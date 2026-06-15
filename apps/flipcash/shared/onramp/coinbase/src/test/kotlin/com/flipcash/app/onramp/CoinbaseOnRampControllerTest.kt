package com.flipcash.app.onramp

import com.coinbase.onramp.api.CoinbaseApi
import com.coinbase.onramp.data.OnRampApiConfig
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.solana.keys.Mint
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoinbaseOnRampControllerTest {

    private val jwtExecutor = mockk<CoinbaseJwtExecutor>(relaxed = true)
    private val api = mockk<CoinbaseApi>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val featureFlags = mockk<FeatureFlagController>(relaxed = true)
    private val googlePayReadiness = mockk<GooglePayReadiness>(relaxed = true)
    private val webViewChannelDetector = mockk<WebViewChannelDetector>(relaxed = true)
    private val buyOptionsCache = mockk<BuyOptionsCache>(relaxed = true)

    private val onRampApiEndpoint = OnRampApiConfig(
        scheme = "https",
        host = "api.example.com",
        path = "onramp/v1/buy",
        method = "POST",
    )

    private lateinit var controller: CoinbaseOnRampController

    @Before
    fun setUp() {
        // Token.usdf triggers Ed25519 native lib via deriveVirtualMachineAccount;
        // mock the extension property and timelockSwapAccounts to avoid the native call
        mockkStatic("com.getcode.opencode.model.financial.MintMetadataKt")
        mockkStatic("com.getcode.opencode.internal.solana.extensions.TokenKt")
        val fakeUsdf = mockk<Token>(relaxed = true) {
            every { symbol } returns "USDF"
            every { address } returns Mint.usdf
        }
        val fakeUsdc = mockk<Token>(relaxed = true) {
            every { symbol } returns "USDC"
            every { address } returns Mint.usdc
        }
        every { Token.usdf } returns fakeUsdf
        every { Token.usdc } returns fakeUsdc
        every { fakeUsdf.timelockSwapAccounts(any()) } returns mockk(relaxed = true)
        every { fakeUsdc.timelockSwapAccounts(any()) } returns mockk(relaxed = true)

        every { webViewChannelDetector.detect() } returns null

        controller = CoinbaseOnRampController(
            jwtExecutor = jwtExecutor,
            onRampApiEndpoint = onRampApiEndpoint,
            api = api,
            userManager = userManager,
            exchange = exchange,
            featureFlags = featureFlags,
            transactionController = mockk(relaxed = true),
            googlePayReadiness = googlePayReadiness,
            webViewChannelDetector = webViewChannelDetector,
            buyOptionsCache = buyOptionsCache,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("com.getcode.opencode.model.financial.MintMetadataKt")
        unmockkStatic("com.getcode.opencode.internal.solana.extensions.TokenKt")
    }

    private fun stubAccountCluster(present: Boolean = true) {
        if (present) {
            every { userManager.accountCluster } returns mockk<AccountCluster>(relaxed = true)
        } else {
            every { userManager.accountCluster } returns null
        }
    }

    private fun stubProfile(email: String? = "test@test.com", phone: String? = "+11234567890") {
        val profile = UserProfile(
            displayName = "Test",
            socialAccounts = emptyList(),
            verifiedEmailAddress = email,
            verifiedPhoneNumber = phone,
        )
        every { userManager.profile } returns profile
    }

    private fun stubValidUser() {
        stubAccountCluster()
        stubProfile()
    }

    // region placeOrderInclusiveOfFees validation

    @Test
    fun `placeOrderInclusiveOfFees fails when owner is null`() = runTest {
        stubAccountCluster(present = false)
        stubProfile()

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertEquals(result.exceptionOrNull()?.message?.contains("Owner"), true)
    }

    @Test
    fun `placeOrderInclusiveOfFees fails when email is null`() = runTest {
        stubAccountCluster()
        stubProfile(email = null, phone = "+11234567890")

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertIs<OnRampAuthError.VerificationRequired>(result.exceptionOrNull())
        assertTrue((result.exceptionOrNull() as OnRampAuthError.VerificationRequired).email)
    }

    @Test
    fun `placeOrderInclusiveOfFees fails when phone is null`() = runTest {
        stubAccountCluster()
        stubProfile(email = "test@test.com", phone = null)

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertIs<OnRampAuthError.VerificationRequired>(result.exceptionOrNull())
        assertTrue((result.exceptionOrNull() as OnRampAuthError.VerificationRequired).phone)
    }

    @Test
    fun `placeOrderInclusiveOfFees returns VerificationRequired with correct flags`() = runTest {
        stubAccountCluster()
        stubProfile(email = null, phone = null)

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<OnRampAuthError.VerificationRequired>(error)
        assertTrue(error.phone)
        assertTrue(error.email)
    }

    // endregion

    // region placeOrderExclusiveOfFees validation

    @Test
    fun `placeOrderExclusiveOfFees fails when exchange rate missing for non-USD`() = runTest {
        stubValidUser()
        every { exchange.rateToUsd(CurrencyCode.EUR) } returns null

        val result = controller.placeOrderExclusiveOfFees(Fiat(10, CurrencyCode.EUR))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Exchange rate") == true)
    }

    @Test
    fun `placeOrderExclusiveOfFees fails when email and phone both null`() = runTest {
        stubAccountCluster()
        stubProfile(email = null, phone = null)

        val result = controller.placeOrderExclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<OnRampAuthError.VerificationRequired>(error)
        assertTrue(error.phone)
        assertTrue(error.email)
    }

    // endregion

    // region checkPurchaseGates

    @Test
    fun `checkPurchaseGates succeeds when stable WebView and GPay ready`() = runTest {
        coEvery { googlePayReadiness.check() } returns GooglePayReadiness.Status.Ready
        every { webViewChannelDetector.detect() } returns null

        assertTrue(controller.checkPurchaseGates().isSuccess)
    }

    @Test
    fun `checkPurchaseGates fails with GooglePayNotSupported when GPay unavailable`() = runTest {
        coEvery { googlePayReadiness.check() } returns GooglePayReadiness.Status.NotSupported

        val result = controller.checkPurchaseGates()
        assertTrue(result.isFailure)
        assertIs<PurchaseGate.GooglePayNotSupported>(result.exceptionOrNull())
    }

    @Test
    fun `checkPurchaseGates fails with GooglePayNoPaymentMethod when no instrument enrolled`() = runTest {
        coEvery { googlePayReadiness.check() } returns GooglePayReadiness.Status.NoPaymentMethod

        val result = controller.checkPurchaseGates()
        assertTrue(result.isFailure)
        assertIs<PurchaseGate.GooglePayNoPaymentMethod>(result.exceptionOrNull())
    }

    @Test
    fun `checkPurchaseGates fails with WebViewWarning for non-stable channel`() = runTest {
        coEvery { googlePayReadiness.check() } returns GooglePayReadiness.Status.Ready
        every { webViewChannelDetector.detect() } returns WebViewChannel.Beta

        val result = controller.checkPurchaseGates()
        assertTrue(result.isFailure)
        val gate = result.exceptionOrNull()
        assertIs<PurchaseGate.WebViewWarning>(gate)
        assertEquals(WebViewChannel.Beta, gate.channel)
    }

    @Test
    fun `checkPurchaseGates prioritizes GPay block over WebView warning`() = runTest {
        coEvery { googlePayReadiness.check() } returns GooglePayReadiness.Status.NotSupported
        every { webViewChannelDetector.detect() } returns WebViewChannel.Beta

        val result = controller.checkPurchaseGates()
        assertTrue(result.isFailure)
        assertIs<PurchaseGate.GooglePayNotSupported>(result.exceptionOrNull())
    }

    @Test
    fun `placeOrderAndStartPayment does not invoke gate checks`() = runTest {
        stubValidUser()

        // placeOrderAndStartPayment will fail downstream (no JWT mock), but we only
        // care that it never calls detect() or check()
        runCatching {
            controller.placeOrderAndStartPayment(
                token = Token.usdf,
                verifiedFiat = mockk(relaxed = true),
            )
        }

        coVerify(exactly = 0) { googlePayReadiness.check() }
        coVerify(exactly = 0) { webViewChannelDetector.detect() }
    }

    // endregion

    // region placeOrderAndStartPayment token resolution

    @Test
    fun `placeOrderAndStartPayment resolves on-ramp token for USDF deposits`() = runTest {
        stubAccountCluster()
        stubProfile(phone = "+14155551234") // valid US number for region resolution
        stubBuyOptionsCacheMints(mintsWithUsdf)

        runCatching {
            controller.placeOrderAndStartPayment(
                token = Token.usdf,
                verifiedFiat = mockk(relaxed = true),
            )
        }

        coVerify { buyOptionsCache.getCached(any()) }
    }

    @Test
    fun `placeOrderAndStartPayment skips token resolution for launchpad tokens`() = runTest {
        stubValidUser()
        val launchpadToken = mockk<Token>(relaxed = true) {
            every { symbol } returns "JEFFY"
            every { address } returns Mint("54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25")
        }

        runCatching {
            controller.placeOrderAndStartPayment(
                token = launchpadToken,
                verifiedFiat = mockk(relaxed = true),
            )
        }

        coVerify(exactly = 0) { buyOptionsCache.getCached(any()) }
        coVerify(exactly = 0) { buyOptionsCache.prefetch(any()) }
    }

    // endregion

    // region resolveOnRampToken

    private val mintsWithUsdf = setOf(BuyOptionsMint("USDC"), BuyOptionsMint("USDF"))
    private val mintsWithoutUsdf = setOf(BuyOptionsMint("USDC"), BuyOptionsMint("BTC"))

    private fun stubBuyOptionsCacheMints(mints: Set<BuyOptionsMint>?) {
        coEvery { buyOptionsCache.getCached(any()) } returns mints
        coEvery { buyOptionsCache.prefetch(any()) } returns mints
    }

    @Test
    fun `resolveOnRampToken always returns USDF without fallback`() = runTest {
        stubProfile(phone = "+12125551234")
        stubBuyOptionsCacheMints(mintsWithoutUsdf)
        assertEquals(Token.usdf, controller.resolveOnRampToken(allowUsdcFallback = false))
    }

    @Test
    fun `resolveOnRampToken skips cache lookup without fallback`() = runTest {
        stubProfile(phone = "+12125551234")
        stubBuyOptionsCacheMints(mintsWithoutUsdf)

        controller.resolveOnRampToken(allowUsdcFallback = false)

        coVerify(exactly = 0) { buyOptionsCache.getCached(any()) }
        coVerify(exactly = 0) { buyOptionsCache.prefetch(any()) }
    }

    @Test
    fun `resolveOnRampToken with fallback returns USDF when USDF tradable`() = runTest {
        stubProfile(phone = "+14155551234") // San Francisco
        stubBuyOptionsCacheMints(mintsWithUsdf)
        assertEquals(Token.usdf, controller.resolveOnRampToken(allowUsdcFallback = true))
    }

    @Test
    fun `resolveOnRampToken with fallback returns USDF when phone is null`() = runTest {
        stubProfile(phone = null)
        assertEquals(Token.usdf, controller.resolveOnRampToken(allowUsdcFallback = true))
    }

    @Test
    fun `resolveOnRampToken with fallback returns USDC for NYC phone when USDF not tradable`() = runTest {
        stubProfile(phone = "+12125551234")
        stubBuyOptionsCacheMints(mintsWithoutUsdf)
        assertEquals(Token.usdc, controller.resolveOnRampToken(allowUsdcFallback = true))
    }

    @Test
    fun `resolveOnRampToken with fallback returns USDC for Canadian phone when USDF not tradable`() = runTest {
        stubProfile(phone = "+14165551234") // Toronto
        stubBuyOptionsCacheMints(mintsWithoutUsdf)
        assertEquals(Token.usdc, controller.resolveOnRampToken(allowUsdcFallback = true))
    }

    @Test
    fun `resolveOnRampToken with fallback returns USDF for international phone when USDF tradable`() = runTest {
        stubProfile(phone = "+442071234567") // UK
        stubBuyOptionsCacheMints(mintsWithUsdf)
        assertEquals(Token.usdf, controller.resolveOnRampToken(allowUsdcFallback = true))
    }

    @Test
    fun `resolveOnRampToken with fallback defaults to USDF when cache returns null`() = runTest {
        stubProfile(phone = "+12125551234")
        coEvery { buyOptionsCache.getCached(any()) } returns null
        coEvery { buyOptionsCache.prefetch(any()) } returns null
        assertEquals(Token.usdf, controller.resolveOnRampToken(allowUsdcFallback = true))
    }

    @Test
    fun `resolveOnRampToken with fallback uses cached result without calling prefetch`() = runTest {
        stubProfile(phone = "+12125551234")
        coEvery { buyOptionsCache.getCached(any()) } returns mintsWithoutUsdf

        controller.resolveOnRampToken(allowUsdcFallback = true)

        coVerify(exactly = 0) { buyOptionsCache.prefetch(any()) }
    }

    // endregion
}

class CoinbaseOnRampApiErrorParseTest {

    @Test
    fun `parse real Coinbase error response with errorType`() {
        val body = """{"correlationId":"9f42a272080a4bc3-IAD","errorLink":"https://docs.cdp.coinbase.com/api-reference/v2/errors","errorType":"guest_region_forbidden"}"""
        val error = CoinbaseOnRampApiError.parse(body)
        assertIs<CoinbaseOnRampApiError.GuestRegionForbidden>(error)
        assertEquals("9f42a272080a4bc3-IAD", error.correlationId)
        assertEquals("https://docs.cdp.coinbase.com/api-reference/v2/errors", error.errorLink)
    }

    @Test
    fun `parse all known error types`() {
        val expected = mapOf(
            "invalid_request" to CoinbaseOnRampApiError.InvalidRequest::class,
            "network_not_tradable" to CoinbaseOnRampApiError.NetworkNotTradable::class,
            "guest_permission_denied" to CoinbaseOnRampApiError.GuestPermissionDenied::class,
            "guest_region_forbidden" to CoinbaseOnRampApiError.GuestRegionForbidden::class,
            "guest_transaction_limit" to CoinbaseOnRampApiError.GuestTransactionLimit::class,
            "guest_transaction_count" to CoinbaseOnRampApiError.GuestTransactionCount::class,
            "phone_number_verification_expired" to CoinbaseOnRampApiError.PhoneNumberVerificationExpired::class,
        )
        for ((errorType, expectedClass) in expected) {
            val body = """{"errorType":"$errorType","correlationId":"abc"}"""
            val error = CoinbaseOnRampApiError.parse(body)
            assertTrue(expectedClass.isInstance(error), "Failed for errorType: $errorType")
        }
    }

    @Test
    fun `parse unknown error type returns Unknown`() {
        val body = """{"errorType":"some_future_error","correlationId":"abc"}"""
        val error = CoinbaseOnRampApiError.parse(body)
        assertIs<CoinbaseOnRampApiError.Unknown>(error)
        assertEquals("some_future_error", error.errorType)
    }

    @Test
    fun `parse with message field`() {
        val body = """{"errorType":"guest_transaction_limit","message":"limit exceeded","correlationId":"abc"}"""
        val error = CoinbaseOnRampApiError.parse(body)
        assertIs<CoinbaseOnRampApiError.GuestTransactionLimit>(error)
        assertEquals("limit exceeded", error.message)
    }

    @Test
    fun `parse invalid JSON returns null`() {
        val error = CoinbaseOnRampApiError.parse("not json")
        assertEquals(null, error)
    }

    @Test
    fun `parse empty JSON object returns Unknown`() {
        val error = CoinbaseOnRampApiError.parse("{}")
        assertIs<CoinbaseOnRampApiError.Unknown>(error)
    }
}
