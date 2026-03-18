package com.flipcash.app.withdrawal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.withdrawal.internal.entry.WithdrawalEntryScreen
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
fun WithdrawalEntryScreen(
    selectedMint: Mint
) {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_withdraw),
            isInModal = true,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
            titleAlignment = Alignment.CenterHorizontally,
        )
        val viewModel = flowScopedViewModel<WithdrawalViewModel>(key = WithdrawalFlow.key)
        WithdrawalEntryScreen(viewModel, selectedMint)
    }
}

object WithdrawalFlow {
    internal var key: String = ""
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        key = Uuid.random().toString()
    }
}
