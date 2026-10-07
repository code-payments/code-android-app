package com.flipcash.app.messenger.internal.screens.profile

import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the status bar icons light inside a Material3 `ModalBottomSheet`.
 *
 * The sheet draws in its own dialog window, which does not inherit the activity's status bar
 * appearance and falls back to dark icons, near-invisible on the dark-only UI. Call from the
 * sheet's content so [LocalView] is the dialog's view. Mirrors what `ModalBottomSheetScene` does
 * for navigation-hosted sheets.
 */
@Composable
internal fun LightStatusBarIcons() {
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.rootView.windowInsetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
    }
}
