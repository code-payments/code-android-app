package com.flipcash.app.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.login.internal.accounts.AccountSelectionContent
import com.flipcash.app.login.internal.accounts.AccountSelectionViewModel
import com.flipcash.features.login.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

/**
 * The logged-in switcher. Selecting a row logs out and parks the entropy; `App.kt` picks it up
 * once the logout lands and restarts onboarding with it.
 *
 * The "Enter a Different Access Key" footer is hidden here: from inside the app there is no
 * onboarding flow to fall through to, and a logged-in user reaching for a new access key is
 * logging out, which the menu already offers. That makes the back control the only way out, so
 * the bar is not optional — a Block Store read that returns nothing would otherwise leave a
 * signed-in user on a screen with nothing to press.
 */
@Composable
fun AccountSelectionScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel: AccountSelectionViewModel = hiltViewModel()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.title_selectAccount),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { navigator.pop() },
        )
        AccountSelectionContent(
            state = state,
            onSelect = { entropy ->
                viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnAccountSelected(entropy))
            },
            onRemove = { entropy ->
                viewModel.dispatchEvent(AccountSelectionViewModel.Event.OnRemoveRequested(entropy))
            },
            showEnterAccessKey = false,
        )
    }
}
