package com.flipcash.app.login.seed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.login.internal.SeedInputContent
import com.flipcash.features.login.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun SeedInputScreen() {
    val viewModel: SeedInputViewModel = hiltViewModel()
    val navigator = LocalCodeNavigator.current
    Column {
        AppBarWithTitle(
            modifier = Modifier.fillMaxWidth(),
            backButton = true,
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { navigator.pop() },
            title = stringResource(R.string.title_enterAccessKeyWords),
        )
        SeedInputContent(viewModel)
    }
}
