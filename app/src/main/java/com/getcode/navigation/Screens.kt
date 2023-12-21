package com.getcode.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.R
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle
import com.getcode.view.login.LoginHome
import com.getcode.view.login.LoginViewModel
import com.getcode.view.login.PhoneConfirm
import com.getcode.view.login.PhoneVerify
import com.getcode.view.login.PhoneVerifyViewModel
import com.getcode.view.login.SeedInput
import com.getcode.view.login.SeedInputViewModel
import com.getcode.view.main.getKin.BuyAndSellKin

sealed interface NamedScreen {

    val name: String
        @Composable get() = ""

    val hasName: Boolean
        @Composable get() = name.isNotEmpty()
}

sealed interface ModalContent {
    @Composable
    fun ModalContainer(
        backButton: (Screen?) -> Boolean = { false },
        closeButton: (Screen?) -> Boolean = { false },
        screenContent: @Composable () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val navigator = LocalCodeNavigator.current

            val name = (navigator.lastItem as? NamedScreen)?.name
            val sheetName = remember(navigator) { name }

            SheetTitle(
                modifier = Modifier.padding(horizontal = 20.dp),
                title = sheetName,
                // hide while transitioning to/from other destinations
                backButton = backButton(navigator.lastItem),
                closeButton = closeButton(navigator.lastItem),
                onBackIconClicked = { navigator.pop() },
                onCloseIconClicked = { navigator.hide() }
            )
            screenContent()
        }
    }
}
sealed interface ModalRoot: ModalContent

data object MainRoot : Screen {
    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        // TODO: potentially add a loading state here
        //  so app doesn't appear stuck in a dead state
        //  while we wait for auth check to complete
    }
}
