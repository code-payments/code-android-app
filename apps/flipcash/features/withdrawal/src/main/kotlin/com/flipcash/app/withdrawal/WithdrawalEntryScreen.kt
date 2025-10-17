package com.flipcash.app.withdrawal

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
import com.flipcash.app.withdrawal.internal.entry.WithdrawalEntryScreen
import com.flipcash.core.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getStackScopedViewModel
import com.getcode.navigation.modal.ModalScreen
import com.getcode.navigation.screens.NamedScreen
import com.getcode.solana.keys.Mint
import com.getcode.ui.components.AppBarWithTitle
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Parcelize
class WithdrawalEntryScreen(
    private val selectedMint: Mint
): ModalScreen, NamedScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    override val name: String
        @Composable get() = stringResource(R.string.title_withdraw)

    @Composable
    override fun ModalContent() {
        val navigator = LocalCodeNavigator.current
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppBarWithTitle(
                title = name,
                isInModal = true,
                backButton = true,
                onBackIconClicked = { navigator.pop() },
                titleAlignment = Alignment.CenterHorizontally,
            )
            val viewModel = getStackScopedViewModel<WithdrawalViewModel>(key = WithdrawalFlow.key)
            WithdrawalEntryScreen(viewModel, selectedMint)
        }
    }
}

object WithdrawalFlow {
    internal var key: String = ""
        private set

    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        key = Uuid.random().toString()
    }
}