package com.flipcash.app.myaccount.internal

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.events.ProfileEvents
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.appsettings.AppSettingsCoordinator
import com.flipcash.app.auth.AuthManager
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.featureflags.BetaFeature
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.myaccount.internal.settings.AccessKey
import com.flipcash.app.myaccount.internal.settings.AccountInfo
import com.flipcash.app.myaccount.internal.settings.BetaFlags
import com.flipcash.app.myaccount.internal.settings.Blocklist
import com.flipcash.app.myaccount.internal.settings.CreateCurrency
import com.flipcash.app.myaccount.internal.settings.CurrencyLeaderboard
import com.flipcash.app.myaccount.internal.settings.DeleteAccount
import com.flipcash.app.myaccount.internal.settings.DeviceLogs
import com.flipcash.app.myaccount.internal.settings.LinkDesktop
import com.flipcash.app.myaccount.internal.settings.NotificationStatus
import com.flipcash.app.myaccount.internal.settings.LogOut
import com.flipcash.app.myaccount.internal.settings.RequireBiometrics
import com.flipcash.app.myaccount.internal.settings.SettingsViewModel
import com.flipcash.app.myaccount.internal.settings.SwitchAccount
import com.flipcash.app.myaccount.internal.settings.TrustedWebsites
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.core.R
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
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
                R.string.title_settingsSectionSecurity,
                R.string.title_settingsSectionPrivacy,
                R.string.title_advancedFeatures,
                R.string.title_settingsSectionAccount,
            ),
            state.titles(),
        )
        assertEquals(
            listOf(
                AccessKey, RequireBiometrics,
                Blocklist, TrustedWebsites,
                BetaFlags, DeviceLogs, NotificationStatus,
                LogOut, DeleteAccount,
            ),
            state.items(),
        )
    }

    @Test
    fun `advanced lists every row in the iOS order with link a desktop under application logs`() {
        val state = SettingsViewModel.State().after(
            SettingsViewModel.Event.OnBetaFeaturesUnlocked(true, manual = true),
            SettingsViewModel.Event.OnFeatureFlagsUpdated(listOf(BetaFeature(FeatureFlag.LinkDesktop, enabled = true))),
        )
        val advanced = state.sections.single { it.title == R.string.title_advancedFeatures }
        assertEquals(
            listOf(BetaFlags, DeviceLogs, LinkDesktop, NotificationStatus, SwitchAccount),
            advanced.items,
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
        val unlocked = SettingsViewModel.State().after(SettingsViewModel.Event.OnBetaFeaturesUnlocked(true, manual = true))
        assertTrue(SwitchAccount in unlocked.items())
        assertTrue(AccountInfo in unlocked.items())

        val relocked = unlocked.after(SettingsViewModel.Event.OnBetaFeaturesUnlocked(false, manual = false))
        assertFalse(SwitchAccount in relocked.items())
        assertFalse(AccountInfo in relocked.items())
    }

    @Test
    fun `account info sits first in the account section`() {
        val state = SettingsViewModel.State().after(SettingsViewModel.Event.OnBetaFeaturesUnlocked(true, manual = true))
        val account = state.sections.last { it.title == R.string.title_settingsSectionAccount }
        assertEquals(listOf(AccountInfo, LogOut, DeleteAccount), account.items)
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
            SettingsViewModel.Event.OnBetaFeaturesUnlocked(true, manual = true),
        )
        assertFalse(RequireBiometrics in state.items())
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
    fun `trusted websites row always sits directly under blocked`() {
        val privacy = SettingsViewModel.State().sections.single { it.title == R.string.title_settingsSectionPrivacy }
        assertEquals(listOf(Blocklist, TrustedWebsites), privacy.items)
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

    private fun TestScope.createViewModel(
        analytics: FlipcashAnalytics = RecordingAnalytics(),
        groupMoneyUi: Boolean = false,
        betaOverride: Boolean = false,
        isStaff: Boolean = false,
    ): SettingsViewModel {
        val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
        val resolved = mockk<ResolvedUserFlags>(relaxed = true) {
            every { this@mockk.isStaff } returns ResolvedFlag(serverValue = isStaff, override = FieldOverride.None)
        }
        every { userFlags.resolvedFlags } returns MutableStateFlow(resolved)
        every { featureFlags.observeOverride() } returns MutableStateFlow(betaOverride)
        every { featureFlags.observe() } returns MutableStateFlow(emptyList())
        every { featureFlags.observe(FeatureFlag.GroupMoneyUi) } returns MutableStateFlow(groupMoneyUi)

        val appSettings = mockk<AppSettingsCoordinator>(relaxed = true)
        every { appSettings.settings() } returns emptyFlow()


        return SettingsViewModel(
            appSettings = appSettings,
            featureFlags = featureFlags,
            userFlags = userFlags,
            versionInfo = VersionInfo("1.0", 1),
            releaseStageProvider = mockk<ReleaseStageProvider>(relaxed = true),
            resources = FakeResourceHelper(),
            authManager = mockk<AuthManager>(relaxed = true),
            analytics = analytics,
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

        vm.eventFlow.filterIsInstance<SettingsViewModel.Event.ShowDevModeToast>().test {
            // The toast only starts once the taps run well past the unlock threshold.
            repeat(10) {
                vm.dispatchEvent(SettingsViewModel.Event.OnVersionInfoClicked)
                advanceUntilIdle()
            }

            assertEquals(
                FakeResourceHelper().getString(R.string.toast_betaOverrideAlready),
                awaitItem().message,
            )
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 0) { featureFlags.enableBetaFeatures() }
    }

    @Test
    fun `a burst of footer taps asks for one update check once they settle`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel()

        vm.eventFlow.filterIsInstance<SettingsViewModel.Event.CheckForUpdate>().test {
            repeat(3) {
                vm.dispatchEvent(SettingsViewModel.Event.OnVersionInfoClicked)
                advanceTimeBy(100)
            }
            expectNoEvents()

            advanceTimeBy(500)
            awaitItem()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // endregion

    // region currencies

    private val currencyTitles = listOf(
        R.string.title_settingsSectionSecurity,
        R.string.title_settingsSectionPrivacy,
        R.string.title_currencies,
        R.string.title_advancedFeatures,
        R.string.title_settingsSectionAccount,
    )

    @Test
    fun `currencies is absent under the classic shell`() {
        val state = SettingsViewModel.State().after(SettingsViewModel.Event.OnNavBarShellResolved(NavBarShell.Classic))
        assertFalse(R.string.title_currencies in state.titles())
        assertFalse(CreateCurrency in state.items())
        assertFalse(CurrencyLeaderboard in state.items())
    }

    @Test
    fun `currencies sits between privacy and advanced under the group money shell`() {
        val state = SettingsViewModel.State().after(SettingsViewModel.Event.OnNavBarShellResolved(NavBarShell.GroupMoney))
        assertEquals(currencyTitles, state.titles())
        assertEquals(
            listOf(CreateCurrency, CurrencyLeaderboard),
            state.sections.single { it.title == R.string.title_currencies }.items,
        )
    }

    @Test
    fun `currencies survives later events and leaves with the shell`() {
        val state = SettingsViewModel.State().after(
            SettingsViewModel.Event.OnNavBarShellResolved(NavBarShell.GroupMoney),
            SettingsViewModel.Event.OnBetaFeaturesUnlocked(true, manual = true),
            SettingsViewModel.Event.OnFeatureFlagsUpdated(emptyList()),
            SettingsViewModel.Event.OnBiometricsSettingChanged(required = false, supported = true, available = true),
        )
        assertTrue(R.string.title_currencies in state.titles())

        val classic = state.after(SettingsViewModel.Event.OnNavBarShellResolved(NavBarShell.Classic))
        assertFalse(R.string.title_currencies in classic.titles())
    }

    @Test
    fun `currency rows open the same routes as the wallet tiles`() {
        assertEquals(SettingsViewModel.Event.OpenScreen(AppRoute.Token.CurrencyCreator), CreateCurrency.action)
        assertEquals(SettingsViewModel.Event.OpenScreen(AppRoute.Token.Discovery), CurrencyLeaderboard.action)
        assertFalse(CreateCurrency.showBetaIndicator)
        assertFalse(CurrencyLeaderboard.showBetaIndicator)
    }

    @Test
    fun `tapping a currency row emits its open screen event`() = runTest(mainCoroutineRule.dispatcher) {
        val vm = createViewModel(groupMoneyUi = true, betaOverride = true)
        advanceUntilIdle()

        vm.eventFlow.filterIsInstance<SettingsViewModel.Event.OpenScreen>().test {
            vm.dispatchEvent(CreateCurrency.action)
            assertEquals(AppRoute.Token.CurrencyCreator, awaitItem().screen)
            vm.dispatchEvent(CurrencyLeaderboard.action)
            assertEquals(AppRoute.Token.Discovery, awaitItem().screen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.shownWith(groupMoneyUi: Boolean, betaOverride: Boolean, isStaff: Boolean): Boolean {
        val vm = createViewModel(groupMoneyUi = groupMoneyUi, betaOverride = betaOverride, isStaff = isStaff)
        advanceUntilIdle()
        return R.string.title_currencies in vm.stateFlow.value.titles()
    }

    @Test
    fun `the flag alone does not show currencies without staff or the override`() = runTest(mainCoroutineRule.dispatcher) {
        assertFalse(shownWith(groupMoneyUi = true, betaOverride = false, isStaff = false))
    }

    @Test
    fun `the flag with the override shows currencies`() = runTest(mainCoroutineRule.dispatcher) {
        assertTrue(shownWith(groupMoneyUi = true, betaOverride = true, isStaff = false))
    }

    @Test
    fun `the flag with staff shows currencies`() = runTest(mainCoroutineRule.dispatcher) {
        assertTrue(shownWith(groupMoneyUi = true, betaOverride = false, isStaff = true))
    }

    @Test
    fun `staff and the override without the flag keep currencies out`() = runTest(mainCoroutineRule.dispatcher) {
        assertFalse(shownWith(groupMoneyUi = false, betaOverride = true, isStaff = true))
    }

    // endregion

    @Test
    fun `opening the screen sends Settings Viewed once`() = runTest(mainCoroutineRule.dispatcher) {
        val analytics = RecordingAnalytics()
        createViewModel(analytics)
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(ProfileEvents.settingsViewed()), analytics.events)
    }
}
