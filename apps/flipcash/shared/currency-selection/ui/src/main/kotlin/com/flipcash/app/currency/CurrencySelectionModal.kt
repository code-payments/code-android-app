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
import com.flipcash.app.core.money.CurrencySelectionKind
import com.flipcash.app.currency.internal.CurrencySelectionModalContent
import com.flipcash.app.currency.internal.CurrencyViewModel
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class CurrencySelectionModal(
    private val kind: CurrencySelectionKind
) : ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_selectCurrency)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = {
                    navigator.pop()
                }
            )

            val viewModel = getViewModel<CurrencyViewModel>()
            CurrencySelectionModalContent(viewModel)

            LaunchedEffect(viewModel, kind) {
                viewModel.dispatchEvent(CurrencyViewModel.Event.OnKindChanged(kind))
            }
        }
    }
}