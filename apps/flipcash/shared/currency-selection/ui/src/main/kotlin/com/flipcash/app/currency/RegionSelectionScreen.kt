package com.flipcash.app.currency

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.flipcash.app.core.money.RegionSelectionKind
import com.flipcash.app.currency.internal.CurrencyViewModel
import com.flipcash.app.currency.internal.RegionSelectionModalContent
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class RegionSelectionScreen(
    private val kind: RegionSelectionKind
) : ModalScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey
    @IgnoredOnParcel
    override val testTag: String = "region_selection_screen"

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = stringResource(R.string.title_selectRegion),
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = {
                    navigator.pop()
                }
            )

            val viewModel = getViewModel<CurrencyViewModel>()
            RegionSelectionModalContent(viewModel)

            LaunchedEffect(viewModel, kind) {
                viewModel.dispatchEvent(CurrencyViewModel.Event.OnKindChanged(kind))
            }
        }
    }
}