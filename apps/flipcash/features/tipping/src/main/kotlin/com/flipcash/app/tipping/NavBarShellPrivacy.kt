package com.flipcash.app.tipping

import com.flipcash.app.core.navigation.NavBarShell

/** Which kind of group the New Group flow creates under each nav bar shell. */
internal object NavBarShellPrivacy {

    fun newGroupIsPrivate(routeIsPrivate: Boolean, shell: NavBarShell): Boolean = routeIsPrivate
}
