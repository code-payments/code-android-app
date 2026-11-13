package com.flipcash.app.internal.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.router.Router
import com.flipcash.services.models.UserFlags
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import dev.theolm.rinku.DeepLink
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A cold launch picks the bar's shell from stored values and holds the Loading route until the bar
 * draws that shell, so a returning Group Money user never sees a four-tab frame first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LaunchShellTest {

    private object NoLinkRouter : Router {
        override fun dispatch(deepLink: DeepLink): DeeplinkAction = DeeplinkAction.None
        override fun classify(deepLink: DeepLink): DeeplinkType? = null
    }

    private fun storedFlags(groupMoneyUi: Boolean, betaOverride: Boolean): FeatureFlagController =
        mockk(relaxed = true) {
            coEvery { get(FeatureFlag.GroupMoneyUi) } returns groupMoneyUi
            coEvery { getOverride() } returns betaOverride
            // The live flows have not loaded yet: they still hold their initial false.
            every { observeOverride() } returns MutableStateFlow(false)
        }

    private fun restoredUser(flags: UserFlags?): UserManager = mockk(relaxed = true) {
        every { state } returns MutableStateFlow(UserManager.State(authState = AuthState.Ready, flags = flags))
    }

    private val staff = UserFlags.Default.copy(isStaff = true)
    private val notStaff = UserFlags.Default

    private fun loadingNavigator() = CodeNavigator(
        backStack = NavBackStack<NavKey>(AppRoute.Loading),
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = {},
    )

    private fun readyGraph() = buildNavGraphForLaunch(
        state = AuthState.Ready,
        router = NoLinkRouter,
        deepLink = { null },
    )

    private fun TestScope.route(
        navigator: CodeNavigator,
        expected: NavBarShell,
        composed: MutableStateFlow<NavBarShell>,
    ) = launch {
        navigator.routeReadyLaunch(
            expectedShell = { expected },
            composedShell = composed,
            graph = ::readyGraph,
            onPendingAction = {},
        )
    }

    // -- The stored values pick the shell --

    @Test
    fun `stored flag and cached staff bit open Group Money`() = runTest {
        val shell = storedFlags(groupMoneyUi = true, betaOverride = false).launchShell(restoredUser(staff))

        assertEquals(NavBarShell.GroupMoney, shell)
    }

    @Test
    fun `stored flag and stored override open Group Money for a non-staff user`() = runTest {
        val shell = storedFlags(groupMoneyUi = true, betaOverride = true).launchShell(restoredUser(notStaff))

        assertEquals(NavBarShell.GroupMoney, shell)
    }

    @Test
    fun `stored flag alone keeps a non-staff user without the override on Classic`() = runTest {
        val flags = storedFlags(groupMoneyUi = true, betaOverride = false)

        assertEquals(NavBarShell.Classic, flags.launchShell(restoredUser(notStaff)))
        assertEquals(NavBarShell.Classic, flags.launchShell(restoredUser(flags = null)))
    }

    @Test
    fun `flag off opens Classic for staff with the override`() = runTest {
        val shell = storedFlags(groupMoneyUi = false, betaOverride = true).launchShell(restoredUser(staff))

        assertEquals(NavBarShell.Classic, shell)
    }

    // -- The Ready route waits for the composed shell --

    @Test
    fun `Group Money launch stays on Loading until the bar draws Group Money, then opens Groups`() = runTest {
        val navigator = loadingNavigator()
        val composed = MutableStateFlow(NavBarShell.Classic)

        val routing = route(navigator, expected = NavBarShell.GroupMoney, composed = composed)
        runCurrent()
        assertEquals(listOf<NavKey>(AppRoute.Loading), navigator.backStack.toList())

        composed.value = NavBarShell.GroupMoney
        runCurrent()
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), navigator.backStack.toList())
        assertEquals(true, routing.isCompleted)
    }

    @Test
    fun `Classic launch routes at once`() = runTest {
        val navigator = loadingNavigator()

        route(navigator, expected = NavBarShell.Classic, composed = MutableStateFlow(NavBarShell.Classic))
        runCurrent()

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), navigator.backStack.toList())
    }

    @Test
    fun `launch still routes when the composed shell never matches`() = runTest {
        val navigator = loadingNavigator()

        route(navigator, expected = NavBarShell.GroupMoney, composed = MutableStateFlow(NavBarShell.Classic))
        runCurrent()
        assertEquals(listOf<NavKey>(AppRoute.Loading), navigator.backStack.toList())

        advanceTimeBy(LaunchShellTimeout)
        runCurrent()
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), navigator.backStack.toList())
    }
}
