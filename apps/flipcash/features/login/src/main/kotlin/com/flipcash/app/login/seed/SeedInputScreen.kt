package com.flipcash.app.login.seed

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.login.internal.SeedInputContent
import com.flipcash.features.login.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class SeedInputScreen: Screen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_enterAccessKeyWords)

    @Composable
    override fun Content() {
        val viewModel: SeedInputViewModel = getViewModel()
        val navigator = LocalCodeNavigator.current
        Column {
            AppBarWithTitle(
                backButton = true,
                onBackIconClicked = { navigator.pop() },
                title = name,
            )
            SeedInputContent(viewModel)
        }
    }
}

