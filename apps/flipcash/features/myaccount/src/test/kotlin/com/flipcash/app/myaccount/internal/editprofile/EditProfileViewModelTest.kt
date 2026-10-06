package com.flipcash.app.myaccount.internal.editprofile

import com.flipcash.analytics.AddMoneySource
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.core.userprofile.UpdateProfileStep
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.core.TotalBalanceProvider
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.app.userflags.UsernameGate
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.payments.TipPaymentDelegate
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true)

    @Before
    @After
    fun clearAlerts() {
        BottomBarManager.clear()
    }

    private fun TestScope.viewModel(): EditProfileViewModel {
        val userManager = mockk<UserManager>(relaxed = true)
        every { userManager.state } returns MutableStateFlow(UserManager.State())
        val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)
        every { userFlags.resolvedFlags } returns MutableStateFlow(mockk<ResolvedUserFlags>(relaxed = true))
        val totalBalance = mockk<TotalBalanceProvider>()
        every { totalBalance.observeTotalBalance() } returns emptyFlow()
        return EditProfileViewModel(
            userManager = userManager,
            userFlags = userFlags,
            totalBalance = totalBalance,
            tipPayments = mockk<TipPaymentDelegate>(relaxed = true),
            purchaseMethodController = purchaseMethodController,
            analytics = mockk<FlipcashAnalytics>(relaxed = true),
            resources = FakeResourceHelper(),
            dispatchers = TestDispatchers(testScheduler),
        )
    }

    private fun locked() = UsernameGate.Locked(
        minimum = mockk<Fiat>(relaxed = true),
        shortfall = mockk<Fiat>(relaxed = true),
        fraction = 0.5f,
    )

    /** Seeds the claim and gate state, clicks the username row, and returns every event it produced. */
    private fun TestScope.clickUsername(
        vm: EditProfileViewModel,
        username: String?,
        gate: UsernameGate,
    ): MutableList<EditProfileViewModel.Event> {
        val events = mutableListOf<EditProfileViewModel.Event>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventFlow.collect { events += it } }
        vm.dispatchEvent(EditProfileViewModel.Event.OnProfileChanged(profile(username = username)))
        vm.dispatchEvent(EditProfileViewModel.Event.OnUsernameGateChanged(gate, "$100 USD"))
        advanceUntilIdle()
        events.clear()
        vm.dispatchEvent(EditProfileViewModel.Event.UsernameClicked)
        advanceUntilIdle()
        return events
    }

    private val openedUsernameStep = EditProfileViewModel.Event.OpenStep(UpdateProfileStep.Username)

    @Test
    fun `claiming while locked shows the balance minimum and does not open the step`() =
        runTest(mainCoroutineRule.dispatcher) {
            val events = clickUsername(viewModel(), username = null, gate = locked())

            assertTrue(events.none { it is EditProfileViewModel.Event.OpenStep })
            assertEquals(
                "error_title_usernameMinimumBalance",
                BottomBarManager.messages.value.firstOrNull()?.title,
            )
        }

    @Test
    fun `claiming while unlocked opens the username step`() = runTest(mainCoroutineRule.dispatcher) {
        val events = clickUsername(viewModel(), username = null, gate = UsernameGate.Unlocked)

        assertTrue(openedUsernameStep in events)
        assertTrue(BottomBarManager.messages.value.isEmpty())
    }

    @Test
    fun `a held username opens the step even while locked`() = runTest(mainCoroutineRule.dispatcher) {
        val events = clickUsername(viewModel(), username = "mcansh", gate = locked())

        assertTrue(openedUsernameStep in events)
        assertTrue(BottomBarManager.messages.value.isEmpty())
    }

    @Test
    fun `the Add Money action presents deposit options`() = runTest(mainCoroutineRule.dispatcher) {
        val route = mockk<AppRoute>()
        coEvery { purchaseMethodController.presentDepositOptions(popToRoot = true) } returns route
        val vm = viewModel()
        val events = clickUsername(vm, username = null, gate = locked())
        assertTrue(events.none { it is EditProfileViewModel.Event.OpenStep })

        events.clear()
        val addMoney = BottomBarManager.messages.value.first().actions
            .first { it.text.text == "action_addMoney" }
        addMoney.onClick()
        advanceUntilIdle()

        assertTrue(EditProfileViewModel.Event.PresentDepositOptions(AddMoneySource.USERNAME_SHORTFALL) in events)
        coVerify { purchaseMethodController.presentDepositOptions(popToRoot = true) }
        assertTrue(EditProfileViewModel.Event.OpenScreen(route) in events)
    }

    private val reduce = EditProfileViewModel.updateStateForEvent

    private fun profile(
        name: String = "Brandon",
        username: String? = null,
        autoAssigned: Boolean = false,
        bio: String = "",
    ) = UserProfile.Empty.copy(
        displayName = name,
        username = username,
        isUsernameAutoAssigned = autoAssigned,
        bio = bio,
    )

    private fun EditProfileViewModel.State.after(profile: UserProfile) =
        reduce(EditProfileViewModel.Event.OnProfileChanged(profile))(this)

    @Test
    fun `a profile with no username still needs the claim`() {
        assertTrue(EditProfileViewModel.State().after(profile(username = null)).usernameNeedsClaim)
        assertTrue(EditProfileViewModel.State().after(profile(username = "")).usernameNeedsClaim)
    }

    @Test
    fun `a server-assigned username still needs the claim`() {
        val state = EditProfileViewModel.State().after(profile(username = "brandon123", autoAssigned = true))
        assertTrue(state.usernameNeedsClaim)
    }

    @Test
    fun `a chosen username does not need the claim and is carried for display`() {
        val state = EditProfileViewModel.State().after(profile(username = "mcansh"))
        assertFalse(state.usernameNeedsClaim)
        assertEquals("mcansh", state.username)
    }

    @Test
    fun `name and bio follow the profile, including being cleared`() {
        val filled = EditProfileViewModel.State().after(profile(name = "Brandon", bio = "hello"))
        assertEquals("Brandon", filled.displayName)
        assertEquals("hello", filled.bio)

        val cleared = filled.after(profile(name = "", bio = ""))
        assertEquals("", cleared.displayName)
        assertEquals("", cleared.bio)
    }

    @Test
    fun `minimum to chat is unknown until it resolves`() {
        assertNull(EditProfileViewModel.State().minimumToChat)
        val state = reduce(EditProfileViewModel.Event.OnMinimumToChatChanged("$1.00"))(EditProfileViewModel.State())
        assertEquals("$1.00", state.minimumToChat)
    }
}
