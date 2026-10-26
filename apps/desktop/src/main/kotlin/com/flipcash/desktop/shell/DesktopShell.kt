package com.flipcash.desktop.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.core.ui.NavigationBar
import com.flipcash.app.core.ui.rememberNavigationBarState
import com.getcode.ui.components.glass.FloatingChrome

/**
 * The signed-in window: the mobile tabs minus Scan, as a tab bar under 600 wide and a 64-wide rail
 * from there up, around the pane for the selected tab. The width class comes from the space this is
 * given, not the window, so an off-screen render at a fixed width lays out exactly as the window does.
 */
@Composable
fun DesktopShell(
    chat: @Composable (WidthClass) -> Unit,
    money: @Composable (WidthClass) -> Unit,
    you: @Composable (WidthClass) -> Unit,
    avatar: (@Composable (Modifier) -> Unit)?,
    modifier: Modifier = Modifier,
    initialTab: NavBarButton = DesktopStartTab,
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = widthClassFor(maxWidth)
        val pane: @Composable () -> Unit = {
            when (selectedTab) {
                NavBarButton.Wallet -> money(widthClass)
                NavBarButton.TipCard -> you(widthClass)
                else -> chat(widthClass)
            }
        }
        if (widthClass == WidthClass.Compact) {
            // As on the phone, the bar floats over the pane and the pane pads its end by the bar's
            // height through LocalTabBarPadding.
            val density = LocalDensity.current
            var barHeight by remember { mutableStateOf(0.dp) }
            CompositionLocalProvider(
                LocalTabBarPadding provides PaddingValues(bottom = barHeight + TabBarBottomMargin),
            ) {
                Box(Modifier.fillMaxSize()) { pane() }
            }
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = FloatingChrome.horizontalInset)
                    .padding(bottom = TabBarBottomMargin)
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                state = rememberNavigationBarState(selectedTab = selectedTab),
                onButtonClick = { selectedTab = it },
                avatar = avatar,
                tabs = DesktopTabs,
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                DesktopRail(selectedTab = selectedTab, onTabClick = { selectedTab = it }, avatar = avatar)
                PaneDivider()
                Box(Modifier.weight(1f).fillMaxSize()) { pane() }
            }
        }
    }
}

private val TabBarBottomMargin = 16.dp
