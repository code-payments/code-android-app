package com.flipcash.app.myaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.flipcash.app.myaccount.internal.blocklist.BlocklistScreenContent
import com.flipcash.app.myaccount.internal.blocklist.BlocklistViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun BlocklistScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<BlocklistViewModel>()
    val blocked = viewModel.blocked.collectAsLazyPagingItems()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = {
                AppBarDefaults.Title(text = stringResource(R.string.title_blocklist))
            },
            titleAlignment = Alignment.CenterHorizontally,
            leftIcon = { AppBarDefaults.UpNavigation { navigator.pop() } },
        )
        BlocklistScreenContent(
            blocked = blocked,
            unblocking = state.unblocking,
            onUnblock = {
                viewModel.dispatchEvent(BlocklistViewModel.Event.UnblockRequested(it))
            },
        )
    }
}
