package com.flipcash.app.onramp

import com.coinbase.onramp.api.CoinbaseApi
import com.coinbase.onramp.data.OnRampApiConfig
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OnRampControllerTest {

    private val jwtProvider = mockk<OnRampJwtProvider>(relaxed = true)
    private val api = mockk<CoinbaseApi>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)

    private val onRampApiEndpoint = OnRampApiConfig(
        scheme = "https",
        host = "api.example.com",
        path = "onramp/v1/buy",
        method = "POST",
        useSandbox = false,
    )

    private lateinit var controller: OnRampController

    @Before
    fun setUp() {
        controller = OnRampController(
            jwtProvider = jwtProvider,
            onRampApiEndpoint = onRampApiEndpoint,
            api = api,
            userManager = userManager,
            exchange = exchange,
        )
    }

    private fun stubAccountId(present: Boolean = true) {
        if (present) {
            every { userManager.accountId } returns listOf(1, 2, 3, 4).map { it.toByte() }
        } else {
            every { userManager.accountId } returns null
        }
    }

    private fun stubDepositAddress(present: Boolean = true) {
        if (present) {
            val address = PublicKey(ByteArray(32).toList())
            val cluster = mockk<AccountCluster>(relaxed = true) {
                every { usdfDepositAddress } returns address
            }
            every { userManager.accountCluster } returns cluster
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
        stubAccountId()
        stubDepositAddress()
        stubProfile()
    }

    // region placeOrderInclusiveOfFees validation

    @Test
    fun `placeOrderInclusiveOfFees fails when accountId is null`() = runTest {
        stubAccountId(present = false)
        stubDepositAddress()
        stubProfile()

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("User ID") == true)
    }

    @Test
    fun `placeOrderInclusiveOfFees fails when deposit address is null`() = runTest {
        stubAccountId()
        stubDepositAddress(present = false)
        stubProfile()

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Deposit address") == true)
    }

    @Test
    fun `placeOrderInclusiveOfFees fails when email is null`() = runTest {
        stubAccountId()
        stubDepositAddress()
        stubProfile(email = null, phone = "+11234567890")

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertIs<OnRampAuthError.VerificationRequired>(result.exceptionOrNull())
        assertTrue((result.exceptionOrNull() as OnRampAuthError.VerificationRequired).email)
    }

    @Test
    fun `placeOrderInclusiveOfFees fails when phone is null`() = runTest {
        stubAccountId()
        stubDepositAddress()
        stubProfile(email = "test@test.com", phone = null)

        val result = controller.placeOrderInclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertIs<OnRampAuthError.VerificationRequired>(result.exceptionOrNull())
        assertTrue((result.exceptionOrNull() as OnRampAuthError.VerificationRequired).phone)
    }

    @Test
    fun `placeOrderInclusiveOfFees returns VerificationRequired with correct flags`() = runTest {
        stubAccountId()
        stubDepositAddress()
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
    fun `placeOrderExclusiveOfFees fails when accountId is null`() = runTest {
        stubAccountId(present = false)
        stubDepositAddress()
        stubProfile()

        val result = controller.placeOrderExclusiveOfFees(Fiat(10, CurrencyCode.USD))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("User ID") == true)
    }

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
        stubAccountId()
        stubDepositAddress()
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
