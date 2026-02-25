package com.flipcash.app.transactions

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
import com.flipcash.app.transactions.internal.TransactionHistoryScreen
import com.flipcash.app.transactions.internal.TransactionHistoryViewModel
import com.flipcash.features.transactions.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ModalScreen
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class TransactionHistoryScreen(private val mint: Mint) : ModalScreen, Parcelable {

    override val key: ScreenKey
        get() = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "transaction_history_screen"

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                isInModal = true,
                title = stringResource(R.string.title_transactionHistory),
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = { navigator.pop() }
            )
            val viewModel = getViewModel<TransactionHistoryViewModel>()
            TransactionHistoryScreen(viewModel)

            LaunchedEffect(viewModel, mint) {
                viewModel.dispatchEvent(TransactionHistoryViewModel.Event.OnMintProvided(mint))
            }
        }
    }
}