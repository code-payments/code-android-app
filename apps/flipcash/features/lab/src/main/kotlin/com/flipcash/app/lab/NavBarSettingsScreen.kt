package com.flipcash.app.lab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.flipcash.app.lab.internal.NavBarSettingsContent
import com.getcode.navigation.scenes.LocalBottomSheetDismissDispatcher
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

@Composable
fun NavBarSettingsScreen() {
    val dismiss = LocalBottomSheetDismissDispatcher.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBarWithTitle(
            title = "",
            titleAlignment = Alignment.CenterHorizontally,
            endContent = {
                AppBarDefaults.Close { dismiss() }
            }
        )

        NavBarSettingsContent()
    }
}
