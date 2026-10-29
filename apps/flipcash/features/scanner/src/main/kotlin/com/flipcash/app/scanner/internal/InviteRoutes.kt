package com.flipcash.app.scanner.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.navigation.DeeplinkType

/**
 * Where a scanned `/chat/invite` code goes: the same destination as the tapped link. A code
 * with no valid secret opens the broken-link state, not nothing. Null for any other code.
 */
internal fun DeeplinkType.Navigatable.groupInviteRoutes(): List<AppRoute>? = when (this) {
    is DeeplinkType.GroupInvite -> listOf(
        AppRoute.Tabs.Chats,
        AppRoute.Messaging.GroupInvite(secret),
    )
    is DeeplinkType.InvalidGroupInvite -> listOf(
        AppRoute.Tabs.Chats,
        AppRoute.Messaging.GroupInvite(null),
    )
    else -> null
}
