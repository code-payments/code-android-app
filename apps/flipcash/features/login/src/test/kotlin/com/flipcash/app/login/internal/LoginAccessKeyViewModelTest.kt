package com.flipcash.app.login.internal

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.storage.MediaSaver
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.libs.qr.QRCodeGenerator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the onboarding provisioning GATE at the access-key step: the core OCP
 * account must be provisioned before the user advances (free path), and a failed
 * provisioning must block advancement instead of releasing to the scanner.
 * Uses [onWroteDownInstead] ("I wrote it down" / continue-without) which exercises
 * the identical gate code path as save-to-photos without bitmap rendering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginAccessKeyViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    // Mockito for the Result-returning suspend method (MockK double-boxes Result).
    private val authManager: AuthManager = mock()

    // MockK for the rest.
    private val resources = FakeResourceHelper()
    private val mnemonicManager: MnemonicManager = mockk(relaxed = true)
    private val qrCodeGenerator: QRCodeGenerator = mockk(relaxed = true)
    private val mediaSaver: MediaSaver = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val userFlags: UserFlagsCoordinator = mockk(relaxed = true)
    private val featureFlags: FeatureFlagController = mockk(relaxed = true)
    private val analytics: FlipcashAnalyticsService = mockk(relaxed = true)

    private lateinit var dispatchers: TestDispatchers

    @Before
    fun setUp() = BottomBarManager.clear()

    @After
    fun tearDown() = BottomBarManager.clear()

    private fun createViewModel() = LoginAccessKeyViewModel(
        resources = resources,
        mnemonicManager = mnemonicManager,
        qrCodeGenerator = qrCodeGenerator,
        mediaSaver = mediaSaver,
        userManager = userManager,
        dispatchers = dispatchers,
        userFlags = userFlags,
        featureFlags = featureFlags,
        authManager = authManager,
        analytics = analytics,
    )

    @Test
    fun `provisions and advances on success when not IAP`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue } returns false
        whenever(authManager.ensureCoreAccountProvisioned()).thenReturn(Result.success(Unit))
        val viewModel = createViewModel()

        val result = viewModel.onWroteDownInstead()

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow()) // requiresIap == false
        verifyBlocking(authManager) { ensureCoreAccountProvisioned() }
    }

    @Test
    fun `blocks with failure and error when provisioning is denied`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue } returns false
        whenever(authManager.ensureCoreAccountProvisioned())
            .thenReturn(Result.failure(RuntimeException("antispam denied")))
        val viewModel = createViewModel()

        val result = viewModel.onWroteDownInstead()

        assertTrue(result.isFailure) // gate: screen will NOT call onCompleted -> stays put
        assertTrue(BottomBarManager.messages.value.any { it.title == "error_title_accountSetupFailed" })
    }

    @Test
    fun `skips provisioning when IAP is required`() = runTest(mainCoroutineRule.dispatcher) {
        dispatchers = TestDispatchers(testScheduler)
        every { userFlags.resolvedFlags.value.requiresIapForRegistration.effectiveValue } returns true
        val viewModel = createViewModel()

        val result = viewModel.onWroteDownInstead()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow()) // requiresIap == true
        verifyBlocking(authManager, never()) { ensureCoreAccountProvisioned() }
    }
}
