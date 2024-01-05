package com.getcode.navigation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.sheetHeight
import com.getcode.util.recomposeHighlighter
import com.getcode.view.components.SheetTitle
import timber.log.Timber

internal interface ModalContent {

    @Composable
    fun ModalContainer(
        closeButton: (Screen?) -> Boolean = { false },
        screenContent: @Composable () -> Unit
    ) {
        ModalContainer(
            navigator = LocalCodeNavigator.current,
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
            navigator = LocalCodeNavigator.current,
            displayLogo = displayLogo,
            backButton = { false },
            onLogoClicked = onLogoClicked,
            closeButton = closeButton,
            screenContent = screenContent,
        )
    }

    @Composable
    fun ModalContainer(
        navigator: CodeNavigator = LocalCodeNavigator.current,
        displayLogo: Boolean = false,
        backButton: (Screen?) -> Boolean = { false },
        onBackClicked: (() -> Unit)? = null,
        closeButton: (Screen?) -> Boolean = { false },
        onCloseClicked: (() -> Unit)? = null,
        onLogoClicked: () -> Unit = { },
        screenContent: @Composable () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(sheetHeight)
        ) {
            val lastItem by remember(navigator.lastModalItem) {
                derivedStateOf { navigator.lastModalItem }
            }

            Timber.d("lastItem=$lastItem")

            val isBackEnabled by remember(backButton, lastItem) {
                derivedStateOf { backButton(lastItem) }
            }

            val isCloseEnabled by remember(closeButton, lastItem) {
                derivedStateOf { closeButton(lastItem) }
            }

            SheetTitle(
                modifier = Modifier,
                title = {
                    val name = (lastItem as? NamedScreen)?.name
                    val sheetName by remember(lastItem) {
                        derivedStateOf { name }
                    }
                    sheetName.takeIf { !displayLogo }
                },
                displayLogo = displayLogo,
                onLogoClicked = onLogoClicked,
                // hide while transitioning to/from other destinations
                backButton = isBackEnabled,
                closeButton = isCloseEnabled,
                onBackIconClicked = onBackClicked?.let { { it() } } ?: { navigator.pop() },
                onCloseIconClicked = onCloseClicked?.let { { it() } } ?: { navigator.hide() }
            )
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Timber.d("render screen content")
                screenContent()
            }
        }
    }
}

internal sealed interface ModalRoot : ModalContent

data object MainRoot : Screen {
    override val key: ScreenKey = uniqueScreenKey
    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        // TODO: potentially add a loading state here
        //  so app doesn't appear stuck in a dead state
        //  while we wait for auth check to complete
    }
}