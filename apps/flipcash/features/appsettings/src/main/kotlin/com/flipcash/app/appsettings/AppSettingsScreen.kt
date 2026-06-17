package com.flipcash.app.appsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flipcash.app.appsettings.internal.AppSettingsScreenContent
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun AppSettingsScreen() {
    val navigator = LocalCodeNavigator.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = stringResource(R.string.title_appSettings),
            titleAlignment = Alignment.CenterHorizontally,
            backButton = true,
            onBackIconClicked = navigator::pop
        )

        AppSettingsScreenContent()
    }
}
