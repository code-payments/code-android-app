package com.flipcash.app.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import com.flipcash.app.core.AppRoute

/**
 * Which tab set the bar draws. [Classic] is today's four tabs. [GroupMoney] is the Group Money
 * redesign's two (frames node 13729:413 and node 13729:744): Groups, which is today's Chats tab,
 * and Money, which is today's Wallet tab. The Scanner and You routes still resolve under
 * [GroupMoney]; they just have no tab.
 */
enum class NavBarShell(val tabs: List<NavBarButton>) {
    Classic(NavBarButton.tabs),
    GroupMoney(listOf(NavBarButton.Chats, NavBarButton.Wallet)),
    ;

    companion object {
        /**
         * [GroupMoney] only when its flag is on and the user may see beta UI: staff, or a device
         * with the beta override. Anyone else keeps [Classic] whatever the stored flag says.
         */
        fun resolve(groupMoneyUi: Boolean, betaOverride: Boolean, isStaff: Boolean): NavBarShell =
            if (groupMoneyUi && (betaOverride || isStaff)) GroupMoney else Classic
    }
}

/**
 * The tab the bar highlights for [backStack]: the first route from the base that is one of this
 * shell's tab homes, or null when none is (the caller picks the fallback).
 */
fun NavBarShell.selectedTab(backStack: List<NavKey>): NavBarButton? =
    backStack.firstNotNullOfOrNull { route -> (route as? AppRoute)?.asNavBarTab()?.takeIf { it in tabs } }

/** The shell the app is drawing, for screens that lay out differently under [NavBarShell.GroupMoney]. */
val LocalNavBarShell = staticCompositionLocalOf { NavBarShell.Classic }
