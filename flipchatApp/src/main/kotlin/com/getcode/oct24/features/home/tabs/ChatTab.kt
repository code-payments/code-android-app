package com.flipchat.features.home.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.getcode.oct24.R
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.utils.unboundedClickable

internal object ChatTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = stringResource(R.string.title_chats),
            icon = painterResource(R.drawable.ic_chat)
        )

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        Column {
            AppBarWithTitle(
                title = options.title,
                endContent = {
                    Image(
                        modifier = Modifier.unboundedClickable {
                            navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.ChatByUsername))
                        },
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground)
                    )
                }
            )
            Navigator(ScreenRegistry.get(NavScreenProvider.Chat.List))
        }
    }
}