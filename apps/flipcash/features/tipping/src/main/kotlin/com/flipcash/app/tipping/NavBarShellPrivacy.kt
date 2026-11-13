package com.flipcash.app.tipping

import com.flipcash.app.core.navigation.NavBarShell

/**
 * Which kind of group the New Group flow creates under each nav bar shell. Group Money's New Group
 * (frames 1-04 and 1-05) has no public variant, so every group it makes is private; the classic
 * shell keeps the kind its New Chat menu row asked for.
 */
internal object NavBarShellPrivacy {

    fun newGroupIsPrivate(routeIsPrivate: Boolean, shell: NavBarShell): Boolean = when (shell) {
        NavBarShell.GroupMoney -> true
        NavBarShell.Classic -> routeIsPrivate
    }
}
