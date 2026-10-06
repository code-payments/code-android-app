package com.flipcash.app.myaccount.internal

import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.myaccount.internal.settings.AccessKey
import com.flipcash.app.myaccount.internal.settings.AccountInfo
import com.flipcash.app.myaccount.internal.settings.BetaFlags
import com.flipcash.app.myaccount.internal.settings.Blocklist
import com.flipcash.app.myaccount.internal.settings.ChangeDisplayName
import com.flipcash.app.myaccount.internal.settings.ChangeUsername
import com.flipcash.app.myaccount.internal.settings.DeleteAccount
import com.flipcash.app.myaccount.internal.settings.DeviceLogs
import com.flipcash.app.myaccount.internal.settings.LogOut
import com.flipcash.app.myaccount.internal.settings.MinimumTip
import com.flipcash.app.myaccount.internal.settings.ProfilePicture
import com.flipcash.app.myaccount.internal.settings.RequireBiometrics
import com.flipcash.app.myaccount.internal.settings.SettingsViewModel
import com.flipcash.app.myaccount.internal.settings.SwitchAccount
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.core.R
import com.flipcash.services.user.UserManager
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val reduce = SettingsViewModel.updateStateForEvent

    private fun SettingsViewModel.State.items() = sections.flatMap { it.items }
    private fun SettingsViewModel.State.titles() = sections.map { it.title }

    private fun SettingsViewModel.State.after(vararg events: SettingsViewModel.Event): SettingsViewModel.State =
        events.fold(this) { state, event -> reduce(event)(state) }

    // region sections and conditional rows

    @Test
    fun `default state lists every section top to bottom with no conditional rows`() {
        val state = SettingsViewModel.State()
        assertEquals(
            listOf(
                R.string.title_settingsSectionProfile,
                R.string.title_settingsSectionSecurity,
                R.string.title_settingsSectionPrivacy,
                R.string.title_advancedFeatures,
                R.string.title_settingsSectionAccount,
            ),
            state.titles(),
        )
        assertEquals(
            listOf(
                ChangeDisplayName, ProfilePicture, MinimumTip,
                AccessKey, RequireBiometrics,
                Blocklist,
                DeviceLogs, BetaFlags,
                LogOut, DeleteAccount,
            ),
            state.items(),
        )
    }

    @Test
    fun `staff rows are absent without beta or staff`() {
        val items = SettingsViewModel.State().items()
        assertFalse(SwitchAccount in items)
        assertFalse(AccountInfo in items)
    }

    @Test
    fun `staff rows appear once beta unlocks and leave again when it locks`() {
        val unlocked = SettingsViewModel.State().after(SettingsViewModel.Event.OnBetaFeaturesUnlocked(true))
        assertTrue(SwitchAccount in unlocked.items())
        assertTrue(AccountInfo in unlocked.items())

        val relocked = unlocked.after(SettingsViewModel.Event.OnBetaFeaturesUnlocked(false))
        assertFalse(SwitchAccount in relocked.items())
        assertFalse(AccountInfo in relocked.items())
    }

    @Test
    fun `account info sits first in the account section`() {
        val state = SettingsViewModel.State().after(SettingsViewModel.Event.OnBetaFeaturesUnlocked(true))
        val account = state.sections.last { it.title == R.string.title_settingsSectionAccount }
        assertEquals(listOf(AccountInfo, LogOut, DeleteAccount), account.items)
    }

    @Test
    fun `username row is absent without a claimed handle`() {
        assertFalse(ChangeUsername in SettingsViewModel.State().items())
    }

    @Test
    fun `username row follows the claimed handle and sits after the display name`() {
        val state = SettingsViewModel.State().after(SettingsViewModel.Event.OnUsernameClaimChanged(true))
        val profile = state.sections.first().items
        assertEquals(listOf(ChangeDisplayName, ChangeUsername, ProfilePicture, MinimumTip), profile)
        assertFalse(ChangeUsername in state.after(SettingsViewModel.Event.OnUsernameClaimChanged(false)).items())
    }

    @Test
    fun `unsupported biometrics hides the row`() {
        val state = SettingsViewModel.State().after(
            SettingsViewModel.Event.OnBiometricsSettingChanged(required = false, supported = false, available = false),
        )
        assertFalse(RequireBiometrics in state.items())
        assertTrue(AccessKey in state.items())
    }

    @Test
    fun `later events keep earlier conditions`() {
        val state = SettingsViewModel.State().after(
            SettingsViewModel.Event.OnBiometricsSettingChanged(required = true, supported = false, available = false),
            SettingsViewModel.Event.OnUsernameClaimChanged(true),
            SettingsViewModel.Event.OnBetaFeaturesUnlocked(true),
        )
        assertFalse(RequireBiometrics in state.items())
        assertTrue(ChangeUsername in state.items())
        assertTrue(SwitchAccount in state.items())
    }

    @Test
    fun `unavailable biometrics carries the explanation and enrolling clears it`() {
        val blocked = SettingsViewModel.State().after(
            SettingsViewModel.Event.OnBiometricsSettingChanged(
                required = false, supported = true, available = false, description = 123,
            ),
        )
        assertEquals(123, blocked.biometricsDescription)
        assertTrue(RequireBiometrics in blocked.items())

        val enrolled = blocked.after(
            SettingsViewModel.Event.OnBiometricsSettingChanged(required = true, supported = true, available = true),
        )
        assertEquals(null, enrolled.biometricsDescription)
        assertTrue(enrolled.biometricsRequired)
    }

    @Test
    fun `navigation events leave state unchanged`() {
        val state = SettingsViewModel.State()
        listOf(
            SettingsViewModel.Event.OnBiometricsToggled,
            SettingsViewModel.Event.OnAccessKeyClicked,
            SettingsViewModel.Event.OnLogOutClicked,
            SettingsViewModel.Event.OnDeleteAccountClicked,
        ).forEach { assertEquals(state, reduce(it)(state)) }
    }

    // endregion

    // region version footer

    private val featureFlags = mockk<FeatureFlagController>(relaxed = true)

    private fun TestScope.createViewModel(): SettingsViewModel {
        val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
        val resolved = mockk<ResolvedUserFlags>(relaxed = true) {
            every { isStaff } returns ResolvedFlag(serverValue = false, override = FieldOverride.None)
        }
        every { userFlags.resolvedFlags } returns MutableStateFlow(resolved)
        every { featureFlags.observeOverride() } returns MutableStateFlow(false)
        every { featureFlags.observe() } returns MutableStateFlow(emptyList())

        val appSettings = mockk<AppSettingsCoordinator>(relaxed = true)
        every { appSettings.settings() } returns emptyFlow()

        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.state } returns MutableStateFlow(UserManager.State())

        return SettingsViewModel(
            appSettings = appSettings,
            featureFlags = featureFlags,
            userFlags = userFlags,
            userManager = userManager,
            versionInfo = VersionInfo("1.0", 1),
            releaseStageProvider = mockk<ReleaseStageProvider>(relaxed = true),
            resources = FakeResourceHelper(),
            authManager = mockk<AuthManager>(relaxed = true),
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    @Test
    fun `version taps reach the threshold and enable beta features exactly once`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()

        repeat(6) {
            vm.dispatchEvent(SettingsViewModel.Event.OnVersionInfoClicked)
            advanceUntilIdle()
        }
        verify(exactly = 0) { featureFlags.enableBetaFeatures() }

        vm.dispatchEvent(SettingsViewModel.Event.OnVersionInfoClicked)
        advanceUntilIdle()
        verify(exactly = 1) { featureFlags.enableBetaFeatures() }
        assertEquals(7, vm.stateFlow.value.versionTapCount)
    }

    @Test
    fun `already unlocked by the override means taps never re-enable`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        vm.dispatchEvent(SettingsViewModel.Event.OnBetaFeaturesUnlocked(unlocked = true, manual = true))
        advanceUntilIdle()

        repeat(10) {
            vm.dispatchEvent(SettingsViewModel.Event.OnVersionInfoClicked)
            advanceUntilIdle()
        }

        verify(exactly = 0) { featureFlags.enableBetaFeatures() }
    }

    @Test
    fun `a burst of footer taps asks for one update check once they settle`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()
        val checks = mutableListOf<SettingsViewModel.Event.CheckForUpdate>()
        val job = launch {
            vm.eventFlow.filterIsInstance<SettingsViewModel.Event.CheckForUpdate>().toList(checks)
        }

        repeat(3) {
            vm.dispatchEvent(SettingsViewModel.Event.OnVersionInfoClicked)
            advanceTimeBy(100)
        }
        assertTrue(checks.isEmpty())

        advanceTimeBy(500)
        assertEquals(1, checks.size)
        job.cancel()
    }

    // endregion
}
