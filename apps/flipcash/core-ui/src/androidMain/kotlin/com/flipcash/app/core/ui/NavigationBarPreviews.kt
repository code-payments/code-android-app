package com.flipcash.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.core.navigation.NavBarButton
import com.flipcash.app.theme.FlipcashThemeWrapper

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarPreview() {
    NavigationBar(
        state = rememberNavigationBarState(chatListUnreadCount = 100),
        onButtonClick = { }
    )
}

/**
 * The You tab's photo slot in both of its states (node 9713:664) — a flat fill stands in for the
 * photo, since a preview has no profile to read.
 */
@Preview(name = "Avatar, You tab unselected")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarAvatarUnselectedPreview() {
    NavigationBar(
        state = rememberNavigationBarState(selectedTab = NavBarButton.Wallet),
        onButtonClick = { },
        avatar = { modifier -> Box(modifier.background(Color(0xFF8E6E5B))) },
    )
}

@Preview(name = "Avatar, You tab selected")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarAvatarSelectedPreview() {
    NavigationBar(
        state = rememberNavigationBarState(selectedTab = NavBarButton.TipCard),
        onButtonClick = { },
        avatar = { modifier -> Box(modifier.background(Color(0xFF8E6E5B))) },
    )
}

/**
 * The You tab with no photo to draw (node 10000:111297) — the people-circle fallback, selected, so
 * the filled twin gets a preview of its own rather than only appearing in the screenshot test.
 */
@Preview(name = "No photo, You tab selected")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun NavigationBarNoAvatarSelectedPreview() {
    NavigationBar(
        state = rememberNavigationBarState(selectedTab = NavBarButton.TipCard),
        onButtonClick = { },
    )
}
