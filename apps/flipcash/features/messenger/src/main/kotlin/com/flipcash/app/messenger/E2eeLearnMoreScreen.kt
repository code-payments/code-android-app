package com.flipcash.app.messenger

import androidx.compose.runtime.Composable
import com.flipcash.app.messenger.internal.screens.E2eeLearnMoreSheet
import com.flipcash.app.messenger.internal.screens.E2eeSheetKind
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher

/**
 * The DM or group encryption explainer, behind `AppRoute.Messaging.E2eeDmInfo` and `E2eeGroupInfo`.
 */
@Composable
fun E2eeLearnMoreScreen(forGroup: Boolean) {
    // Exit through the sheet so it animates down rather than having its scene deleted mid-frame.
    val dismissSheet = LocalBottomSheetDismissDispatcher.current
    E2eeLearnMoreSheet(
        kind = if (forGroup) E2eeSheetKind.Group else E2eeSheetKind.Dm,
        onDismiss = dismissSheet,
    )
}
