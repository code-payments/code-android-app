package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.SettledClaim
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class CashLinkDelegateTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val billController = mockk<BillController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)

    private val accountCluster = mockk<AccountCluster>(relaxed = true)

    private fun createDelegate(): CashLinkDelegate {
        return CashLinkDelegate(
            stateHolder = SessionStateHolder(),
            billController = billController,
            tokenCoordinator = tokenCoordinator,
            analytics = analytics,
            resources = resources,
            userManager = userManager,
        )
    }

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { userManager.accountCluster } returns accountCluster
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    @Test
    fun `openCashLink with null input does not claim`() = runTest {
        val delegate = createDelegate()
        delegate.openCashLink(null)

        verify(exactly = 0) {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        }
        verify {
            analytics.deeplinkRouted(any(), error = any<IllegalArgumentException>())
        }
    }

    @Test
    fun `openCashLink with empty string does not claim`() = runTest {
        val delegate = createDelegate()
        delegate.openCashLink("")

        verify(exactly = 0) {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `openCashLink with blank or newline-only input does not claim`() = runTest {
        val delegate = createDelegate()
        delegate.openCashLink("  \n  ")

        verify(exactly = 0) {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `openCashLink without account cluster does not claim`() = runTest {
        every { userManager.accountCluster } returns null

        val delegate = createDelegate()
        delegate.openCashLink("validEntropy123")

        verify(exactly = 0) {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        }
        verify {
            analytics.deeplinkRouted(any(), error = any<IllegalStateException>())
        }
    }

    @Test
    fun `openCashLink with valid entropy calls receiveGiftCard`() = runTest {
        val delegate = createDelegate()
        delegate.openCashLink("validEntropy123")

        verify(exactly = 1) {
            billController.receiveGiftCard(
                entropy = "validEntropy123",
                owner = accountCluster,
                claimIfOwned = false,
                onReceived = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `openCashLink deduplicates concurrent claims`() = runTest {
        val delegate = createDelegate()

        // First call starts the claim
        delegate.openCashLink("sameEntropy")

        // Second call while claim is in progress should be ignored
        delegate.openCashLink("sameEntropy")

        verify(exactly = 1) {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `openCashLink trims whitespace and newlines from entropy`() = runTest {
        val delegate = createDelegate()
        delegate.openCashLink("  some\nentropy  ")

        verify {
            billController.receiveGiftCard(
                entropy = "someentropy",
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `a claim that lands settles as collected`() = runTest {
        val delegate = createDelegate()
        val settled = mutableListOf<SettledClaim>()
        backgroundScope.launch { delegate.settledClaims.collect { settled += it } }
        runCurrent()

        val onReceived = slot<suspend (Token, LocalFiat) -> Unit>()
        delegate.openCashLink("validEntropy123")
        verify {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = capture(onReceived),
                onError = any(),
            )
        }
        onReceived.captured.invoke(mockk(relaxed = true), mockk(relaxed = true))
        runCurrent()

        assertEquals(listOf(SettledClaim("validEntropy123", collected = true)), settled)
    }

    @Test
    fun `a claim that fails settles as not collected`() = runTest {
        val delegate = createDelegate()
        val settled = mutableListOf<SettledClaim>()
        backgroundScope.launch { delegate.settledClaims.collect { settled += it } }
        runCurrent()

        // Every failure reports the same way, which is what lets a listener treat "already claimed"
        // and "your own link" as the non-collections they are without naming either.
        val onError = slot<(Throwable) -> Unit>()
        delegate.openCashLink("validEntropy123")
        verify {
            billController.receiveGiftCard(
                entropy = any(),
                owner = any(),
                claimIfOwned = any(),
                onReceived = any(),
                onError = capture(onError),
            )
        }
        onError.captured.invoke(IllegalStateException("already claimed"))
        runCurrent()

        assertEquals(listOf(SettledClaim("validEntropy123", collected = false)), settled)
    }

    @Test
    fun `openCashLink clears bottom bar before processing`() = runTest {
        // Add a message to BottomBarManager first
        BottomBarManager.showInfo(title = "existing", message = "message")
        assertFalse(BottomBarManager.messages.value.isEmpty())

        val delegate = createDelegate()
        delegate.openCashLink(null) // even on early return, bottom bar should be cleared

        assert(BottomBarManager.messages.value.isEmpty())
    }
}
