package com.flipcash.app.userflags

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flipcash.app.userflags.internal.UserFlagsEditor
import com.flipcash.app.userflags.internal.UserFlagsViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun UserFlagsScreen() {
    val navigator = LocalCodeNavigator.current
    val viewModel = hiltViewModel<UserFlagsViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_userFlags),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = navigator::pop,
            endContent = {
                AppBarDefaults.Reset { viewModel.dispatchEvent(UserFlagsViewModel.Event.Reset) }
            }
        )

        UserFlagsEditor(viewModel)
    }
}