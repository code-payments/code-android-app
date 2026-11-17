package com.flipcash.app.auth

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.auth.internal.accounts.BlockStoreAccountStore
import com.flipcash.app.auth.internal.accounts.BlockStoreBytes
import com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.models.UserFlags
import com.flipcash.services.user.UserManager
import com.getcode.utils.network.NetworkConnectivityListener
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Group Money onboarding flow skips the access key screen, so [AuthManager.completeAccessKeyStep]
 * is the only backup of the key a new user gets there. These run the real create sequence through a
 * real [PassphraseCredentialManager] and account store, for both registration paths, because the
 * order of `onAccountPurchased` differs between them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HeadlessAccessKeyStepTest {

    private class FakeBlockStoreBytes : BlockStoreBytes {
        private var stored: ByteArray = ByteArray(0)
        override suspend fun read(): ByteArray = stored
        override suspend fun write(bytes: ByteArray): Boolean {
            stored = bytes
            return true
        }

        override suspend fun delete() {
            stored = ByteArray(0)
        }
    }

    private val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher())
    private val userId = List(32) { it.toByte() }

    private val accountStore = BlockStoreAccountStore(FakeBlockStoreBytes())
    private val accountController: AccountController = mockk(relaxed = true)
    private val ocpAccountController: com.getcode.opencode.controllers.AccountController =
        mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val userFlagsCoordinator: UserFlagsCoordinator = mockk(relaxed = true)
    private val networkObserver: NetworkConnectivityListener = mockk(relaxed = true)
    private val featureFlags: FeatureFlagController = mockk(relaxed = true)

    private lateinit var credentialManager: PassphraseCredentialManager
    private lateinit var authManager: AuthManager

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatchers.testDispatcher)
        every { userManager.state } returns MutableStateFlow(UserManager.State())
        every { userManager.userFlags } returns null
        every { networkObserver.isConnected } returns true
        coEvery { accountController.createAccount() } returns Result.success(userId)
        coEvery { ocpAccountController.ensureCoreAccount(any()) } returns Result.success(Unit)

        credentialManager = PassphraseCredentialManager(
            context = RuntimeEnvironment.getApplication(),
            accountController = accountController,
            userManager = userManager,
            accountStore = accountStore,
            dispatchers = dispatchers,
        )
        authManager = newAuthManager()
    }

    private fun newAuthManager() = AuthManager(
        credentialManager = credentialManager,
        accountStore = accountStore,
        accountProfileCache = mockk(relaxed = true),
        userManager = userManager,
        notificationManager = mockk(relaxed = true),
        accountController = accountController,
        ocpAccountController = ocpAccountController,
        profileController = mockk(relaxed = true),
        pushController = mockk(relaxed = true),
        pushTokenProvider = mockk(relaxed = true),
        pushRegistrar = mockk(relaxed = true),
        tokenCoordinator = mockk(relaxed = true),
        persistence = mockk(relaxed = true),
        featureFlags = featureFlags,
        appSettings = mockk(relaxed = true),
        userFlags = userFlagsCoordinator,
        profileCoordinator = mockk(relaxed = true),
        contactCoordinator = mockk(relaxed = true),
        chatCoordinator = mockk(relaxed = true),
        wrongKeyChats = mockk(relaxed = true),
        dispatchers = dispatchers,
        networkObserver = networkObserver,
        analytics = RecordingAnalytics(),
    )

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun registrationRequiresPurchase(required: Boolean) {
        coEvery { accountController.getUserFlags() } returns
            Result.success(UserFlags.Default.copy(requiresIapForRegistration = required))
        val resolved = mockk<ResolvedUserFlags>(relaxed = true) {
            every { requiresIapForRegistration } returns ResolvedFlag(required, FieldOverride.None)
        }
        every { userFlagsCoordinator.resolvedFlags } returns MutableStateFlow(resolved)
    }

    @Test
    fun `with no purchase needed the key is saved, marked seen and the core account provisioned`() =
        runTest(dispatchers.testDispatcher) {
            registrationRequiresPurchase(false)
            authManager.createAccount().getOrThrow()
            assertFalse(credentialManager.hasSeenAccessKey())

            val requiresPurchase = authManager.completeAccessKeyStep().getOrThrow()

            assertFalse(requiresPurchase)
            assertEquals(1, accountStore.all().size)
            assertTrue(credentialManager.hasSeenAccessKey())
            coVerify(exactly = 1) { ocpAccountController.ensureCoreAccount(any()) }
        }

    @Test
    fun `with a purchase needed the key is saved and marked seen before the purchase`() =
        runTest(dispatchers.testDispatcher) {
            registrationRequiresPurchase(true)
            authManager.createAccount().getOrThrow()
            assertFalse(credentialManager.hasSeenAccessKey())

            val requiresPurchase = authManager.completeAccessKeyStep().getOrThrow()

            assertTrue(requiresPurchase)
            assertEquals(1, accountStore.all().size)
            assertTrue(credentialManager.hasSeenAccessKey())
            coVerify(exactly = 0) { ocpAccountController.ensureCoreAccount(any()) }

            authManager.onAccountPurchased().getOrThrow()

            assertTrue(credentialManager.hasSeenAccessKey())
        }

    @Test
    fun `a failed core account setup fails the step after the key is saved`() =
        runTest(dispatchers.testDispatcher) {
            registrationRequiresPurchase(false)
            coEvery { ocpAccountController.ensureCoreAccount(any()) } returns
                Result.failure(IllegalStateException("provisioning failed"))
            authManager.createAccount().getOrThrow()

            val result = authManager.completeAccessKeyStep()

            assertTrue(result.isFailure)
            assertEquals(1, accountStore.all().size)
            assertTrue(credentialManager.hasSeenAccessKey())
        }

    // The Group Money onboarding gate is read at the same Create tap. Before sign-in staff standing
    // is unknown, so it is the shell's rule with no staff: the flag and the device beta override.
    //
    // The stubs follow the real controller: observe(flag) and observeOverride() build a new
    // StateFlow on each call that starts at its default and catches up with DataStore later. A
    // flow made at the Create tap is still at its default, so a gate that called observe() there
    // was closed on every device.
    private fun gate(groupMoneyUi: Boolean, betaOverride: Boolean): Boolean {
        val flagFlows = mutableListOf<MutableStateFlow<Boolean>>()
        val overrideFlows = mutableListOf<MutableStateFlow<Boolean>>()
        every { featureFlags.observe(FeatureFlag.GroupMoneyUi) } answers {
            MutableStateFlow(FeatureFlag.GroupMoneyUi.default).also(flagFlows::add)
        }
        every { featureFlags.observeOverride() } answers { MutableStateFlow(false).also(overrideFlows::add) }

        val authManager = newAuthManager() // app start
        // DataStore loads; only the flows made so far catch up.
        flagFlows.forEach { it.value = groupMoneyUi }
        overrideFlows.forEach { it.value = betaOverride }

        return authManager.isGroupMoneyOnboardingOpen() // the Create tap
    }

    @Test
    fun `the onboarding gate is open only with the group money flag and the beta override`() {
        assertTrue(gate(groupMoneyUi = true, betaOverride = true))
    }

    @Test
    fun `the onboarding gate is closed without either one`() {
        assertFalse(gate(groupMoneyUi = true, betaOverride = false))
        assertFalse(gate(groupMoneyUi = false, betaOverride = true))
        assertFalse(gate(groupMoneyUi = false, betaOverride = false))
    }

    // A tester turns both on in Settings, logs out and taps Create without restarting the app.
    // The held flows keep collecting DataStore, so the tap must read them then, not at start.
    @Test
    fun `the onboarding gate follows a toggle made after the app started`() {
        val flagFlow = MutableStateFlow(FeatureFlag.GroupMoneyUi.default)
        val overrideFlow = MutableStateFlow(false)
        every { featureFlags.observe(FeatureFlag.GroupMoneyUi) } returns flagFlow
        every { featureFlags.observeOverride() } returns overrideFlow

        val authManager = newAuthManager()
        assertFalse(authManager.isGroupMoneyOnboardingOpen())

        flagFlow.value = true
        overrideFlow.value = true
        assertTrue(authManager.isGroupMoneyOnboardingOpen())

        overrideFlow.value = false
        assertFalse(authManager.isGroupMoneyOnboardingOpen())
    }
}
