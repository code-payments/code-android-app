package com.flipcash.app.internal.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.extensions.navigateAll
import com.flipcash.app.core.extensions.openChatFromSearch
import com.flipcash.app.core.navigation.DeeplinkAction
import com.flipcash.app.core.navigation.DeeplinkType
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.core.navigation.destinationRoute
import com.flipcash.app.core.navigation.selectedTab
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.featureflags.FeatureTrack
import com.flipcash.app.featureflags.isShownInLabs
import com.flipcash.app.featureflags.message
import com.flipcash.app.featureflags.title
import com.flipcash.app.internal.ui.navigation.LaunchNavGraph
import com.flipcash.app.internal.ui.navigation.buildNavGraphForLaunch
import com.flipcash.app.router.Router
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.user.AuthState
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.solana.keys.Mint
import dev.theolm.rinku.DeepLink
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec for phase 1 of the Group Money redesign (frames node 13729:413 Groups, node 13729:744
 * Money): the flag, who sees it, the two-tab shell it selects, and the routes that open it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GroupMoneyShellSpecTest {

    // region flag

    @Test
    fun `the group money flag exists and is off by default`() {
        val flag = FeatureFlag.GroupMoneyUi
        assertEquals("group_money_ui_enabled", flag.key)
        assertEquals(false, flag.default)
        assertFalse(flag.launched)
        assertTrue(flag.visible)
        assertEquals(FeatureTrack.Internal, flag.minTrack)
    }

    @Test
    fun `the group money flag is listed in Labs with a title and message`() {
        assertTrue(FeatureFlag.GroupMoneyUi in FeatureFlag.entries)
        assertTrue(FeatureFlag.GroupMoneyUi in FeatureFlag.availableEntries)
        assertTrue(FeatureFlag.GroupMoneyUi.title.isNotBlank())
        assertTrue(FeatureFlag.GroupMoneyUi.message.isNotBlank())
    }

    // endregion

    // region who sees the toggle

    @Test
    fun `Labs hides the group money toggle from a user who is neither staff nor beta override`() {
        assertFalse(FeatureFlag.GroupMoneyUi.isShownInLabs(showAllFlags = false, isStaff = false, onboarding = false))
    }

    @Test
    fun `Labs shows the group money toggle to staff`() {
        assertTrue(FeatureFlag.GroupMoneyUi.isShownInLabs(showAllFlags = false, isStaff = true, onboarding = false))
    }

    @Test
    fun `Labs shows the group money toggle with the beta override`() {
        assertTrue(FeatureFlag.GroupMoneyUi.isShownInLabs(showAllFlags = true, isStaff = false, onboarding = false))
    }

    @Test
    fun `Labs keeps today's rule for every other flag`() {
        // Staff alone still sees only Production flags; the override still shows everything.
        assertFalse(FeatureFlag.LinkDesktop.isShownInLabs(showAllFlags = false, isStaff = true, onboarding = false))
        assertTrue(FeatureFlag.LinkDesktop.isShownInLabs(showAllFlags = true, isStaff = false, onboarding = false))
        assertTrue(FeatureFlag.ChatCashBill.isShownInLabs(showAllFlags = false, isStaff = false, onboarding = false))
    }

    // endregion

    // region which shell

    @Test
    fun `the shell is group money only when the flag is on and the user is staff or has the override`() {
        val cases = listOf(
            Triple(true, true, false) to NavBarShell.GroupMoney,
            Triple(true, false, true) to NavBarShell.GroupMoney,
            Triple(true, true, true) to NavBarShell.GroupMoney,
            Triple(true, false, false) to NavBarShell.Classic,
            Triple(false, true, false) to NavBarShell.Classic,
            Triple(false, false, true) to NavBarShell.Classic,
            Triple(false, true, true) to NavBarShell.Classic,
            Triple(false, false, false) to NavBarShell.Classic,
        )
        cases.forEach { (input, expected) ->
            val (on, override, staff) = input
            assertEquals(
                expected,
                NavBarShell.resolve(groupMoneyUi = on, betaOverride = override, isStaff = staff),
                "flag=$on override=$override staff=$staff",
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun shellFromHome(flagOn: Boolean, override: Boolean, staff: Boolean): NavBarShell {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val controller = mockk<FeatureFlagController>(relaxed = true)
        every { controller.observe(any<FeatureFlag<*>>()) } returns MutableStateFlow(false)
        every { controller.observe(FeatureFlag.GroupMoneyUi) } returns MutableStateFlow(flagOn)
        every { controller.observeOverride() } returns MutableStateFlow(override)
        val resolved = mockk<ResolvedUserFlags>(relaxed = true) {
            every { isStaff } returns ResolvedFlag(serverValue = staff, override = FieldOverride.None)
        }
        val userFlags = mockk<UserFlagsCoordinator>(relaxed = true) {
            every { resolvedFlags } returns MutableStateFlow(resolved)
        }
        val viewModel = HomeViewModel(
            authManager = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
            resources = mockk(relaxed = true),
            appSettingsCoordinator = mockk(relaxed = true),
            shareSheetController = mockk(relaxed = true),
            featureFlagController = controller,
            userFlags = userFlags,
            notificationPrompt = mockk(relaxed = true),
            reactionAlerts = mockk(relaxed = true),
        )
        var shell: NavBarShell? = null
        runTest(dispatcher) {
            backgroundScope.launch { viewModel.navBarShell.collect { shell = it } }
            advanceUntilIdle()
        }
        return requireNotNull(shell)
    }

    @Test
    fun `home picks the group money shell for staff with the flag on`() {
        assertEquals(NavBarShell.GroupMoney, shellFromHome(flagOn = true, override = false, staff = true))
    }

    @Test
    fun `home picks the group money shell for the beta override with the flag on`() {
        assertEquals(NavBarShell.GroupMoney, shellFromHome(flagOn = true, override = true, staff = false))
    }

    @Test
    fun `home keeps the classic shell when the flag is on but the user is neither staff nor override`() {
        assertEquals(NavBarShell.Classic, shellFromHome(flagOn = true, override = false, staff = false))
    }

    @Test
    fun `home keeps the classic shell for staff with the flag off`() {
        assertEquals(NavBarShell.Classic, shellFromHome(flagOn = false, override = true, staff = true))
    }

    // endregion

    // region tabs

    @Test
    fun `the group money shell has exactly the Groups and Money tabs`() {
        assertEquals(listOf(NavBarButton.Chats, NavBarButton.Wallet), NavBarShell.GroupMoney.tabs)
    }

    @Test
    fun `the classic shell keeps today's four tabs`() {
        assertEquals(
            listOf(NavBarButton.Scanner, NavBarButton.Chats, NavBarButton.Wallet, NavBarButton.TipCard),
            NavBarShell.Classic.tabs,
        )
        assertEquals(NavBarButton.tabs, NavBarShell.Classic.tabs)
    }

    @Test
    fun `Groups opens today's Chats tab and Money opens today's Wallet tab`() {
        val (groups, money) = NavBarShell.GroupMoney.tabs
        assertEquals(AppRoute.Tabs.Chats, groups.destinationRoute())
        assertEquals(AppRoute.Tabs.Wallet, money.destinationRoute())
    }

    @Test
    fun `the selected tab is the shell tab that owns the stack`() {
        assertEquals(NavBarButton.Chats, NavBarShell.GroupMoney.selectedTab(listOf(AppRoute.Tabs.Chats)))
        assertEquals(
            NavBarButton.Chats,
            NavBarShell.GroupMoney.selectedTab(listOf(AppRoute.Tabs.Chats, AppRoute.Messaging.Search)),
        )
        assertEquals(
            NavBarButton.Wallet,
            NavBarShell.GroupMoney.selectedTab(listOf(AppRoute.Tabs.Wallet, AppRoute.Sheets.ActivityHistory)),
        )
    }

    // endregion

    // region launch and deeplinks

    private class FakeRouter(private val action: DeeplinkAction) : Router {
        override fun dispatch(deepLink: DeepLink): DeeplinkAction = action
        override fun classify(deepLink: DeepLink): DeeplinkType? = null
    }

    private val anyLink = DeepLink("https://app.flipcash.com/anything")

    private fun launch(action: DeeplinkAction, deepLink: DeepLink?): LaunchNavGraph =
        requireNotNull(
            buildNavGraphForLaunch(
                state = AuthState.Ready,
                router = FakeRouter(action),
                deepLink = { deepLink },
            )
        )

    @Test
    fun `a cold launch opens on Groups`() {
        val stack = launch(DeeplinkAction.None, deepLink = null).resolvedBackStack()
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats), stack)
        assertEquals(NavBarButton.Chats, NavBarShell.GroupMoney.selectedTab(stack))
    }

    @Test
    fun `a wallet deeplink opens on Money`() {
        val route = AppRoute.Token.Info(Mint("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"))
        val stack = launch(DeeplinkAction.Navigate(listOf(route)), anyLink).resolvedBackStack()
        assertEquals(AppRoute.Tabs.Wallet, stack.first())
        assertEquals(NavBarButton.Wallet, NavBarShell.GroupMoney.selectedTab(stack))
    }

    @Test
    fun `the Scanner route still resolves at launch`() {
        val stack = launch(DeeplinkAction.Navigate(listOf(AppRoute.Tabs.Scanner)), anyLink).resolvedBackStack()
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Scanner), stack)
    }

    // endregion

    // region navigator

    private fun navigatorOn(vararg routes: NavKey) = CodeNavigator(
        backStack = NavBackStack(*routes),
        resultStore = EmptyCodeNavigator.resultStore,
        onRootReached = {},
    )

    private val chat = AppRoute.Messaging.Chat(
        identifier = ChatIdentifier.ByChatId(ChatId(ByteArray(32) { 1 })),
        jumpToMessageId = null,
    )

    @Test
    fun `a search chat result from Money opens in Groups`() {
        val navigator = navigatorOn(AppRoute.Tabs.Wallet, AppRoute.Messaging.Search)
        navigator.openChatFromSearch(NavBarShell.GroupMoney, chat)
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats, chat), navigator.backStack.toList())
    }

    @Test
    fun `a search chat result from Groups opens over the search`() {
        val navigator = navigatorOn(AppRoute.Tabs.Chats, AppRoute.Messaging.Search)
        navigator.openChatFromSearch(NavBarShell.GroupMoney, chat)
        assertEquals(
            listOf<NavKey>(AppRoute.Tabs.Chats, AppRoute.Messaging.Search, chat),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `the classic shell keeps today's push for a search chat result`() {
        val navigator = navigatorOn(AppRoute.Tabs.Wallet, AppRoute.Messaging.Search)
        navigator.openChatFromSearch(NavBarShell.Classic, chat)
        assertEquals(
            listOf<NavKey>(AppRoute.Tabs.Wallet, AppRoute.Messaging.Search, chat),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `the Scanner and You routes still resolve on the navigator`() {
        val navigator = navigatorOn(AppRoute.Tabs.Chats)
        navigator.navigateAll(listOf(AppRoute.Tabs.Scanner))
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Scanner), navigator.backStack.toList())

        // The Money avatar opens You the way the Wallet's OpenScreen does today: a push.
        val money = navigatorOn(AppRoute.Tabs.Wallet)
        money.push(AppRoute.Tabs.Menu)
        assertEquals(listOf<NavKey>(AppRoute.Tabs.Wallet, AppRoute.Tabs.Menu), money.backStack.toList())
    }

    // endregion
}
