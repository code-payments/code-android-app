package com.getcode.navigation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.sheetHeight
import com.getcode.view.components.SheetTitle

sealed interface ModalContent {

    @Composable
    fun ModalContainer(
        closeButton: (Screen?) -> Boolean = { false },
        screenContent: @Composable () -> Unit
    ) {
        ModalContainer(
            displayLogo = false,
            backButton = { false },
            onLogoClicked = {},
            closeButton = closeButton,
            screenContent = screenContent,
        )
    }

    @Composable
    fun ModalContainer(
        displayLogo: Boolean = false,
        onLogoClicked: () -> Unit = { },
        closeButton: (Screen?) -> Boolean = { false },
        screenContent: @Composable () -> Unit
    ) {
        ModalContainer(
            displayLogo = displayLogo,
            backButton = { false },
            onLogoClicked = onLogoClicked,
            closeButton = closeButton,
            screenContent = screenContent,
        )
    }

    @Composable
    fun ModalContainer(
        displayLogo: Boolean = false,
        backButton: (Screen?) -> Boolean = { false },
        closeButton: (Screen?) -> Boolean = { false },
        onLogoClicked: () -> Unit = { },
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
                title = sheetName.takeIf { !displayLogo },
                displayLogo = displayLogo,
                onLogoClicked = onLogoClicked,
                // hide while transitioning to/from other destinations
                backButton = backButton(navigator.lastItem),
                closeButton = closeButton(navigator.lastItem),
                onBackIconClicked = { navigator.pop() },
                onCloseIconClicked = { navigator.hide() }
            )
            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                screenContent()
            }
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