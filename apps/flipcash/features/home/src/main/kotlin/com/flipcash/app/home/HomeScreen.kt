package com.flipcash.app.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.navigation.NavBarConfig
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeScaffold

@Composable
fun HomeScreen() {
    val navbarState = rememberNavigationBarState(
        isNewUi = false,
        config = NavBarConfig(order = NavBarButton.v2Order),
        tipUnreadCount = 0,
    )

    CodeScaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.grid.x8)
                    .navigationBarsPadding()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                state = navbarState,
                onButtonClick = { button ->
                    when (button) {
                        NavBarButton.Scanner -> TODO()
                        NavBarButton.Wallet -> TODO()
                        NavBarButton.Chats -> TODO()
                        NavBarButton.TipCard -> TODO()
                        NavBarButton.Give -> Unit
                        NavBarButton.Discover -> Unit
                        NavBarButton.Tips -> Unit
                    }
                }
            )
        }
    ) { padding ->

    }
}