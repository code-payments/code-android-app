package com.flipcash.app.login.seed

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.login.internal.SeedInputContent
import com.flipcash.features.login.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AppScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class SeedInputScreen: AppScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "seed_input_screen"

    @Composable
    override fun ScreenContent() {
        val viewModel: SeedInputViewModel = getViewModel()
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
}

