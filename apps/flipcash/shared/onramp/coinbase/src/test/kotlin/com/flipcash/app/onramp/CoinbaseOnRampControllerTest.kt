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
import com.getcode.opencode.model.financial.usdf
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

    private val jwtProvider = mockk<OnRampJwtProvider>(relaxed = true)
    private val api = mockk<CoinbaseApi>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val featureFlags = mockk<FeatureFlagController>(relaxed = true)

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
        val fakeToken = mockk<Token>(relaxed = true)
        every { Token.usdf } returns fakeToken
        every { fakeToken.timelockSwapAccounts(any()) } returns mockk(relaxed = true)

        controller = CoinbaseOnRampController(
            jwtProvider = jwtProvider,
            onRampApiEndpoint = onRampApiEndpoint,
            api = api,
            userManager = userManager,
            exchange = exchange,
            featureFlags = featureFlags,
            transactionController = mockk(relaxed = true),
            googlePayReadiness = mockk(relaxed = true),
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
}
