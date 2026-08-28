package com.flipcash.app.session.internal.delegates

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.internal.bill.BillController
import com.flipcash.app.session.internal.SessionStateHolder
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.WalletRevealCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.OpenCodePayload
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.model.core.PayloadKind
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.util.vibration.Vibrator
import com.kik.kikx.models.ScannableKikCode
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CodeScanDelegateTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private val billController = mockk<BillController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true)
    private val analytics = mockk<FlipcashAnalyticsService>(relaxed = true)
    private val vibrator = mockk<Vibrator>(relaxed = true)
    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val walletReveal = mockk<WalletRevealCoordinator>(relaxed = true)
    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())

    private val stateHolder = SessionStateHolder()

    private val mockPayload = mockk<OpenCodePayload>(relaxed = true) {
        every { kind } returns PayloadKind.Cash
        every { rendezvous.publicKey } returns "test-rendezvous-key"
    }

    private fun createDelegate(): CodeScanDelegate {
        return CodeScanDelegate(
            stateHolder = stateHolder,
            billController = billController,
            tokenCoordinator = tokenCoordinator,
            walletReveal = walletReveal,
            analytics = analytics,
            vibrator = vibrator,
            userManager = userManager,
            dispatchers = dispatchers,
        )
    }

    @Before
    fun setUp() {
        every { userManager.accountCluster } returns mockk(relaxed = true)
        every { billController.state } returns mockk {
            every { value } returns BillState.Default
        }
        mockkObject(OpenCodePayload.Companion)
        every { OpenCodePayload.fromList(any()) } returns mockPayload
    }

    @After
    fun tearDown() {
        unmockkObject(OpenCodePayload.Companion)
    }

    private fun remoteKikCode(payloadId: ByteArray = ByteArray(20)): ScannableKikCode {
        return ScannableKikCode.RemoteKikCode(payloadId = payloadId, colorIndex = 0)
    }

    // --- onCameraScanning ---

    @Test
    fun `onCameraScanning updates state holder`() = runTest {
        val delegate = createDelegate()
        assertNull(stateHolder.current.isCameraUp)

        delegate.onCameraScanning(true)
        assertEquals(true, stateHolder.current.isCameraUp)

        delegate.onCameraScanning(false)
        assertEquals(false, stateHolder.current.isCameraUp)
    }

    // --- onCodeScan guards ---

    @Test
    fun `onCodeScan ignores non-RemoteKikCode`() = runTest {
        val delegate = createDelegate()
        val nonRemote = mockk<ScannableKikCode.UsernameKikCode>(relaxed = true)
        delegate.onCodeScan(nonRemote)

        verify(exactly = 0) {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `onCodeScan ignores scan when bill is already showing`() = runTest {
        every { billController.state } returns mockk {
            every { value } returns BillState.Default.copy(bill = mockk(relaxed = true))
        }

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        verify(exactly = 0) {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `onCodeScan deduplicates same rendezvous key`() = runTest {
        val delegate = createDelegate()

        delegate.onCodeScan(remoteKikCode())
        delegate.onCodeScan(remoteKikCode()) // same rendezvous key

        verify(exactly = 1) {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `onCodeScan ignores Unknown payload kind`() = runTest {
        every { mockPayload.kind } returns PayloadKind.Unknown

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        verify(exactly = 0) {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = any(),
                onError = any(),
            )
        }
    }

    // --- vibrateOnScan ---

    @Test
    fun `onCodeScan vibrates when vibrateOnScan is enabled`() = runTest {
        stateHolder.update { it.copy(vibrateOnScan = true) }

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        verify { vibrator.tick() }
    }

    @Test
    fun `onCodeScan does not vibrate when vibrateOnScan is disabled`() = runTest {
        stateHolder.update { it.copy(vibrateOnScan = false) }

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        verify(exactly = 0) { vibrator.tick() }
    }

    // --- Cash scan triggers attemptGrab ---

    @Test
    fun `onCodeScan with Cash kind calls attemptGrab`() = runTest {
        every { mockPayload.kind } returns PayloadKind.Cash

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        verify(exactly = 1) {
            billController.attemptGrab(
                owner = any(),
                payload = mockPayload,
                onGrabbed = any(),
                onError = any(),
            )
        }
    }

    @Test
    fun `onCodeScan with MultiMintCash kind calls attemptGrab`() = runTest {
        every { mockPayload.kind } returns PayloadKind.MultiMintCash

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        verify(exactly = 1) {
            billController.attemptGrab(
                owner = any(),
                payload = mockPayload,
                onGrabbed = any(),
                onError = any(),
            )
        }
    }

    // --- the grab callback ---

    @Test
    fun `a grabbed bill is snapshotted for the wallet before it is credited`() = runTest {
        val onGrabbed = slot<suspend (Token, LocalFiat, VerifiedState?) -> Unit>()
        every {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = capture(onGrabbed),
                onError = any(),
            )
        } answers {}

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        val token = mockk<Token>(relaxed = true)
        val amount = mockk<LocalFiat>(relaxed = true)
        onGrabbed.captured.invoke(token, amount, null)

        // Order is the whole feature: the credit lands here, at grab time, but the wallet is not
        // shown until the user taps "Put in Wallet". Capture after it and there is nothing left
        // for the balance to tick up from.
        coVerifyOrder {
            walletReveal.capture(token.address)
            tokenCoordinator.add(token, amount)
        }
    }

    // --- Error clears rendezvous so re-scan is possible ---

    @Test
    fun `attemptGrab error clears rendezvous allowing re-scan`() = runTest {
        val onErrorSlot = mutableListOf<(Throwable) -> Unit>()
        every {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = any(),
                onError = capture(onErrorSlot),
            )
        } answers {}

        val delegate = createDelegate()
        delegate.onCodeScan(remoteKikCode())

        // Invoke the error callback
        assertTrue(onErrorSlot.isNotEmpty())
        onErrorSlot.first().invoke(RuntimeException("grab failed"))

        // Should be able to scan the same code again
        delegate.onCodeScan(remoteKikCode())

        verify(exactly = 2) {
            billController.attemptGrab(
                owner = any(),
                payload = any(),
                onGrabbed = any(),
                onError = any(),
            )
        }
    }
}
