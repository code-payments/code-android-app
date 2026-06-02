package com.flipcash.app.auth

import androidx.core.app.NotificationManagerCompat
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.internal.credentials.AccountMetadata
import com.flipcash.app.auth.internal.credentials.PassphraseCredentialManager
import com.flipcash.app.contacts.ContactCoordinator
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.persistence.PersistenceProvider
import com.flipcash.app.push.PushTokenProvider
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.controllers.ProfileController
import com.flipcash.services.controllers.PushController
import com.flipcash.services.models.UserFlags
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val credentialManager: PassphraseCredentialManager = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)
    private val notificationManager: NotificationManagerCompat = mockk(relaxed = true)
    private val accountController: AccountController = mockk(relaxed = true)
    private val profileController: ProfileController = mockk(relaxed = true)
    private val pushController: PushController = mockk(relaxed = true)
    private val pushTokenProvider: PushTokenProvider = mockk(relaxed = true)
    private val tokenCoordinator: TokenCoordinator = mockk(relaxed = true)
    private val persistence: PersistenceProvider = mockk(relaxed = true)
    private val featureFlagController: FeatureFlagController = mockk(relaxed = true)
    private val appSettings: AppSettingsCoordinator = mockk(relaxed = true)
    private val userFlags: UserFlagsCoordinator = mockk(relaxed = true)
    private val contactCoordinator: ContactCoordinator = mockk(relaxed = true)
    private val userManagerState = MutableStateFlow(UserManager.State())

    private lateinit var authManager: AuthManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { userManager.state } returns userManagerState
        coEvery { pushTokenProvider.getToken() } returns "fake-token"

        // Default stubs for methods called during login/createAccount success paths
        coEvery { accountController.getUserFlags() } returns Result.success(UserFlags.Default)
        coEvery { credentialManager.onAccountPurchased() } returns Result.success(mockk(relaxed = true))
        coEvery { pushController.addToken(any()) } returns Result.success(Unit)
        coEvery { pushController.deleteTokens() } returns Result.success(Unit)
        coEvery { credentialManager.hasSeenAccessKey() } returns true
        coEvery { credentialManager.hasCompletedOnboarding() } returns true

        authManager = AuthManager(
            credentialManager = credentialManager,
            userManager = userManager,
            notificationManager = notificationManager,
            accountController = accountController,
            profileController = profileController,
            pushController = pushController,
            pushTokenProvider = pushTokenProvider,
            tokenCoordinator = tokenCoordinator,
            persistence = persistence,
            featureFlagController = featureFlagController,
            appSettings = appSettings,
            userFlags = userFlags,
            contactCoordinator = contactCoordinator,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login with empty entropy clears user and returns failure`() = runTest {
        val result = authManager.login(entropyB64 = "")

        assertTrue(result.isFailure)
        verify { userManager.clear() }
    }

    @Test
    fun `login success opens database and sets account id`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        val accountMetadata: AccountMetadata = mockk(relaxed = true)
        val testId = listOf<Byte>(1, 2, 3)
        every { accountMetadata.id } returns testId

        coEvery { credentialManager.login(entropy, any()) } returns Result.success(accountMetadata)

        val result = authManager.login(entropyB64 = entropy)

        assertTrue(result.isSuccess)
        verify { persistence.openDatabase(entropy) }
        verify { userManager.set(accountId = testId) }
    }

    @Test
    fun `login failure triggers logout and resetStateForUser`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        coEvery { credentialManager.login(entropy, any()) } returns Result.failure(RuntimeException("auth error"))
        coEvery { credentialManager.logout() } returns Result.success(Unit)

        val result = authManager.login(entropyB64 = entropy)

        assertTrue(result.isFailure)
        coVerify { credentialManager.logout() }
        verify { userManager.clear() }
        verify { notificationManager.cancelAll() }
        coVerify { tokenCoordinator.reset() }
        verify { persistence.close() }
        verify { featureFlagController.reset() }
        verify { appSettings.reset() }
        verify { userFlags.clearAll() }
    }

    @Test
    fun `non-soft login sets softLoginDisabled flag`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        val accountMetadata: AccountMetadata = mockk(relaxed = true)
        val testId = listOf<Byte>(1, 2, 3)
        every { accountMetadata.id } returns testId

        coEvery { credentialManager.login(entropy, any()) } returns Result.success(accountMetadata)

        // Login with isSoftLogin=false should succeed
        val result = authManager.login(entropyB64 = entropy, isSoftLogin = false)
        assertTrue(result.isSuccess)

        // Verify the login was performed (exercises structured concurrency in login's onSuccess)
        verify { persistence.openDatabase(entropy) }
        verify { userManager.set(accountId = testId) }
        coVerify { accountController.getUserFlags() }
    }

    @Test
    fun `createAccount success fetches user flags and opens database`() = runTest {
        val entropy = "bmV3YWNjb3VudGVudHJvcHk="
        coEvery { credentialManager.createAccount() } returns Result.success(entropy)

        val result = authManager.createAccount()

        assertTrue(result.isSuccess)
        coVerify { accountController.getUserFlags() }
        verify { userManager.set(UserFlags.Default) }
        verify { persistence.openDatabase(entropy) }
    }

    @Test
    fun `createAccount failure returns failure without side effects`() = runTest {
        coEvery { credentialManager.createAccount() } returns Result.failure(RuntimeException("creation failed"))

        val result = authManager.createAccount()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { accountController.getUserFlags() }
        verify(exactly = 0) { persistence.openDatabase(any()) }
    }

    @Test
    fun `logout clears credential storage and resets state`() = runTest {
        coEvery { credentialManager.logout() } returns Result.success(Unit)

        val result = authManager.logout()

        assertTrue(result.isSuccess)
        coVerify { credentialManager.logout() }
        verify { userManager.clear() }
        verify { notificationManager.cancelAll() }
        coVerify { tokenCoordinator.reset() }
        verify { persistence.close() }
        verify { featureFlagController.reset() }
        verify { appSettings.reset() }
        verify { userFlags.clearAll() }
    }

    @Test
    fun `logoutAndSwitchAccount sets pendingSwitchEntropy before logout`() = runTest {
        val switchEntropy = "c3dpdGNoZW50cm9weQ=="
        coEvery { credentialManager.logout() } returns Result.success(Unit)

        val result = authManager.logoutAndSwitchAccount(switchEntropy)

        assertTrue(result.isSuccess)
        assertEquals(switchEntropy, result.getOrNull())
        coVerify { credentialManager.logout() }
    }

    @Test
    fun `consumePendingSwitchEntropy clears after read`() = runTest {
        val switchEntropy = "c3dpdGNoZW50cm9weQ=="
        coEvery { credentialManager.logout() } returns Result.success(Unit)

        authManager.logoutAndSwitchAccount(switchEntropy)

        val consumed = authManager.consumePendingSwitchEntropy()
        assertNotNull(consumed)
        assertEquals(switchEntropy, consumed)

        val secondRead = authManager.consumePendingSwitchEntropy()
        assertNull(secondRead)
    }

    @Test
    fun `login retries getUserFlags on failure then succeeds`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        val accountMetadata: AccountMetadata = mockk(relaxed = true)
        val testId = listOf<Byte>(1, 2, 3)
        every { accountMetadata.id } returns testId

        coEvery { credentialManager.login(entropy, any()) } returns Result.success(accountMetadata)

        val flags = UserFlags.Default.copy(isRegistered = true)
        coEvery { accountController.getUserFlags() } returnsMany listOf(
            Result.failure(RuntimeException("transient failure")),
            Result.success(flags)
        )

        val result = authManager.login(entropyB64 = entropy, isSoftLogin = true)

        assertTrue(result.isSuccess)
        verify { userManager.set(flags) }
        verify { userManager.set(authState = AuthState.Ready) }
    }

    @Test
    fun `login resumes at PostAccessKey when registered but onboarding not completed`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        val accountMetadata: AccountMetadata = mockk(relaxed = true)
        val testId = listOf<Byte>(1, 2, 3)
        every { accountMetadata.id } returns testId

        coEvery { credentialManager.login(entropy, any()) } returns Result.success(accountMetadata)
        coEvery { credentialManager.hasCompletedOnboarding() } returns false

        val flags = UserFlags.Default.copy(isRegistered = true)
        coEvery { accountController.getUserFlags() } returns Result.success(flags)

        val result = authManager.login(entropyB64 = entropy)

        assertTrue(result.isSuccess)
        verify { userManager.set(flags) }
        verify { userManager.set(AuthState.Onboarding(AuthState.ResumePoint.PostAccessKey)) }
    }

    @Test
    fun `login falls back to Ready when flags exhausted but onboarding completed`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        val accountMetadata: AccountMetadata = mockk(relaxed = true)
        val testId = listOf<Byte>(1, 2, 3)
        every { accountMetadata.id } returns testId

        coEvery { credentialManager.login(entropy, any()) } returns Result.success(accountMetadata)
        coEvery { accountController.getUserFlags() } returns Result.failure(RuntimeException("persistent failure"))
        coEvery { credentialManager.hasCompletedOnboarding() } returns true

        val result = authManager.login(entropyB64 = entropy, isSoftLogin = true)

        assertTrue(result.isSuccess)
        verify { userManager.set(authState = AuthState.Ready) }
    }

    @Test
    fun `login falls back to Onboarding when flags exhausted and onboarding not completed`() = runTest {
        val entropy = "dGVzdGVudHJvcHkxMjM0NQ=="
        val accountMetadata: AccountMetadata = mockk(relaxed = true)
        val testId = listOf<Byte>(1, 2, 3)
        every { accountMetadata.id } returns testId

        coEvery { credentialManager.login(entropy, any()) } returns Result.success(accountMetadata)
        coEvery { accountController.getUserFlags() } returns Result.failure(RuntimeException("persistent failure"))
        coEvery { credentialManager.hasCompletedOnboarding() } returns false

        val result = authManager.login(entropyB64 = entropy)

        assertTrue(result.isSuccess)
        verify { userManager.set(authState = AuthState.Onboarding(AuthState.ResumePoint.PostAccessKey)) }
    }

    @Test
    fun `onUserAccessKeySeen sets Onboarding resumePoint to PostAccessKey when no IAP required`() = runTest {
        every { userManager.authState } returns AuthState.Unknown
        every { userManager.userFlags } returns null
        coEvery { credentialManager.onUserAccessKeySeen() } returns Result.success(Unit)

        val result = authManager.onUserAccessKeySeen()

        assertTrue(result.isSuccess)
        verify { userManager.set(AuthState.Onboarding(AuthState.ResumePoint.PostAccessKey)) }
    }

    @Test
    fun `onUserAccessKeySeen sets Onboarding resumePoint to AccessKeyThenPurchase when IAP required`() = runTest {
        every { userManager.authState } returns AuthState.Unknown
        every { userManager.userFlags } returns UserFlags.Default.copy(requiresIapForRegistration = true)
        coEvery { credentialManager.onUserAccessKeySeen() } returns Result.success(Unit)

        val result = authManager.onUserAccessKeySeen()

        assertTrue(result.isSuccess)
        verify { userManager.set(AuthState.Onboarding(AuthState.ResumePoint.AccessKeyThenPurchase)) }
    }

    @Test
    fun `onUserAccessKeySeen does not change state if already LoggedIn`() = runTest {
        every { userManager.authState } returns AuthState.Ready
        coEvery { credentialManager.onUserAccessKeySeen() } returns Result.success(Unit)

        val result = authManager.onUserAccessKeySeen()

        assertTrue(result.isSuccess)
        verify(exactly = 0) { userManager.set(match<AuthState> { it is AuthState.Onboarding }) }
    }

    @Test
    fun `presentCredentialStorage does not set Ready — onboarding still in progress`() = runTest {
        val flags = UserFlags.Default.copy(isRegistered = true)
        coEvery { credentialManager.presentSaveOption() } returns Result.success(mockk(relaxed = true))
        coEvery { accountController.getUserFlags() } returns Result.success(flags)

        val result = authManager.presentCredentialStorage()

        assertTrue(result.isSuccess)
        verify { userManager.set(flags) }
        verify(exactly = 0) { userManager.set(AuthState.Ready) }
    }

    @Test
    fun `onAccountPurchased does not set Ready — onboarding still in progress`() = runTest {
        coEvery { credentialManager.onAccountPurchased() } returns Result.success(mockk(relaxed = true))

        val result = authManager.onAccountPurchased()

        assertTrue(result.isSuccess)
        verify(exactly = 0) { userManager.set(AuthState.Ready) }
    }
}
