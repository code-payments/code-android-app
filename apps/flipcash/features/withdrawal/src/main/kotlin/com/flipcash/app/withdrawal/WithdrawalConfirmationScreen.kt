package com.flipcash.app.withdrawal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.withdrawal.internal.confirmation.WithdrawalConfirmationScreen
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.flowScopedViewModel
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun WithdrawalConfirmationScreen() {
    val navigator = LocalCodeNavigator.current
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_withdraw),
            isInModal = true,
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = { navigator.pop() },
        )
        WithdrawalConfirmationScreen(flowScopedViewModel<WithdrawalViewModel>(key = WithdrawalFlow.key))
    }
}
