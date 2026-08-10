package com.flipcash.app.scanner.internal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.core.bill.BillState
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.NavigationBarState
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.LocalFeatureFlags
import com.flipcash.app.scanner.internal.ScannerDecorItem
import com.flipcash.app.session.SessionState

@Composable
internal fun ScannerNavigationBar(
    modifier: Modifier = Modifier,
    state: SessionState = SessionState(),
    billState: BillState = BillState.Default,
    isPaused: Boolean = false,
    onAction: (ScannerDecorItem) -> Unit = { }
) {
    val featureFlags = LocalFeatureFlags.current
    // v2 hoists the nav bar to the app root (AppNavigationBar); v1 keeps it in-screen.
    val newUi by featureFlags.observe(FeatureFlag.NewUi).collectAsStateWithLifecycle()
    if (newUi) return

    val navBarConfigString by featureFlags
        .getOption(FeatureFlag.NavBar)
        .collectAsStateWithLifecycle()
    val config = remember(navBarConfigString) {
        NavBarConfig.deserialize(navBarConfigString)
    }

    val navBarState = rememberNavigationBarState(
        isNewUi = false,
        config = config,
        tipUnreadCount = state.tipsUnreadCount,
        showToast = billState.showToast && billState.toast != null,
        toastText = billState.toast?.formattedAmount,
        isPaused = isPaused,
    )

    NavigationBar(
        modifier = modifier,
        state = navBarState,
        onButtonClick = { button ->
            val item = when (button) {
                NavBarButton.Give -> ScannerDecorItem.Give
                NavBarButton.Wallet -> ScannerDecorItem.Wallet
                NavBarButton.Discover -> ScannerDecorItem.Discover
                NavBarButton.Tips -> ScannerDecorItem.Tips
                NavBarButton.Chats -> null
                NavBarButton.TipCard -> null
                NavBarButton.Scanner -> null
            }
            if (item != null) {
                onAction(item)
            }
        },
    )
}
