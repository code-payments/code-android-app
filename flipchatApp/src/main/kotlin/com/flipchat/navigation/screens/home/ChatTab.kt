package com.flipchat.navigation.screens.home

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.flipchat.R

internal object ChatTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = stringResource(R.string.title_chats),
            icon = painterResource(R.drawable.ic_chat)
        )

    @Composable
    override fun Content() {
        Text("Chat")
    }
}