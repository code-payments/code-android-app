package com.flipchat.features.home

import android.os.Parcelable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.flipchat.features.home.tabs.CashTab
import com.flipchat.features.home.tabs.ChatTab
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.oct24.features.home.HomeViewModel
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.utils.withTopBorder
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

internal val tabs = listOf(ChatTab, CashTab)

@Parcelize
data object TabbedHomeScreen : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getActivityScopedViewModel<HomeViewModel>()

        val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        viewModel.openStream()
                        viewModel.requestAirdrop()
                    }
                    Lifecycle.Event.ON_STOP -> {
                        viewModel.closeStream()
                    }
                    else -> { }
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        TabNavigator(
            tab = ChatTab,
            tabDisposable = {
                TabDisposable(
                    navigator = it,
                    tabs = tabs,
                )
            }
        ) { tabNavigator ->
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .background(CodeTheme.colors.background)
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    CurrentTab()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .withTopBorder()
                ) {
                    tabs.fastForEach { tab ->
                        val backgroundColor by animateColorAsState(
                            if (tabNavigator.current.options.index == tab.options.index) CodeTheme.colors.brandSubtle else CodeTheme.colors.surface,
                            label = "selected tab color"
                        )
                        Box(
                            modifier = Modifier
                                .background(backgroundColor)
                                .navigationBarsPadding()
                                .weight(1f)
                                .clickable { tabNavigator.current = tab },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = CodeTheme.dimens.grid.x2),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    CodeTheme.dimens.grid.x1,
                                    Alignment.CenterVertically
                                )
                            ) {
                                Image(
                                    modifier = Modifier.size(16.dp),
                                    painter = tab.options.icon!!,
                                    contentDescription = null,
                                )

                                Text(
                                    text = tab.options.title,
                                    style = CodeTheme.typography.textSmall,
                                    color = White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}