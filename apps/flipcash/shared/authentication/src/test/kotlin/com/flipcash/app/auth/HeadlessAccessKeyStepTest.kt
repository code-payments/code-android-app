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
        authManager = AuthManager(
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
            featureFlags = mockk(relaxed = true),
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
    }

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
}
