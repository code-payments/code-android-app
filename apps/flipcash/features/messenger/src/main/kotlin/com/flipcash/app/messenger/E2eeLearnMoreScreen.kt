package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import com.flipcash.app.messenger.internal.screens.E2eeLearnMoreSheet
import com.flipcash.app.messenger.internal.screens.E2eeSheetKind
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher

/**
 * The DM or group encryption explainer, behind `AppRoute.Messaging.E2eeDmInfo`, `E2eeGroupInfo` and
 * `E2eePrivateGroupInfo`.
 */
@Composable
fun E2eeLearnMoreScreen(forGroup: Boolean, isPrivate: Boolean = false) {
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    E2eeLearnMoreSheet(
        kind = when {
            !forGroup -> E2eeSheetKind.Dm
            isPrivate -> E2eeSheetKind.PrivateGroup
            else -> E2eeSheetKind.Group
        },
        onDismiss = dismissSheet,
    )
}
