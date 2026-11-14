package com.flipcash.app.tipping

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.flipcash.analytics.GroupType
import com.flipcash.analytics.events.GroupEvents
import com.flipcash.app.analytics.LocalAnalytics
import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.LocalNavBarShell
import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.theme.FlipcashPreview
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.EmptyCodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Group Money phase 2, item 1: the Groups tab's + goes straight to New Group (frame 1-04, node
 * 13729:462). The classic shell keeps the New Chat menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatsAddGroupMoneyInviteSpec {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val analytics = RecordingAnalytics()

    private fun tapAdd(shell: NavBarShell): CodeNavigator {
        val navigator = CodeNavigator(
            backStack = NavBackStack<NavKey>(AppRoute.Tabs.Chats),
            resultStore = EmptyCodeNavigator.resultStore,
            onRootReached = {},
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalCodeNavigator provides navigator,
                LocalNavBarShell provides shell,
                LocalAnalytics provides analytics,
            ) {
                FlipcashPreview { ChatsAddAction() }
            }
        }
        composeRule.onNodeWithTag("action_add").performClick()
        composeRule.waitForIdle()
        return navigator
    }

    @Test
    fun `group money plus opens New Group straight away`() {
        val navigator = tapAdd(NavBarShell.GroupMoney)

        assertEquals(
            listOf<NavKey>(AppRoute.Tabs.Chats, AppRoute.Messaging.NewGroup(isPrivate = true)),
            navigator.backStack.toList(),
        )
    }

    @Test
    fun `group money plus records a private group open, as the New Chat menu did`() {
        tapAdd(NavBarShell.GroupMoney)

        assertEquals(listOf(GroupEvents.newOpened(GroupType.PRIVATE)), analytics.events.toList())
    }

    @Test
    fun `classic plus opens the New Chat menu`() {
        val navigator = tapAdd(NavBarShell.Classic)

        assertEquals(listOf<NavKey>(AppRoute.Tabs.Chats, AppRoute.Messaging.NewChat), navigator.backStack.toList())
        assertEquals(emptyList(), analytics.events.toList())
    }
}
