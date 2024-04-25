package com.getcode.navigation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.LocalBetaFlags
import com.getcode.LocalBuyModuleAvailable
import com.getcode.TopLevelViewModel
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SheetTitle
import com.getcode.ui.components.keyboardAsState
import com.getcode.ui.utils.getActivityScopedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
internal fun Screen.ModalContainer(
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
internal fun Screen.ModalContainer(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Screen.ModalContainer(
    navigator: CodeNavigator = LocalCodeNavigator.current,
    displayLogo: Boolean = false,
    title: @Composable (Screen?) -> String? = { null },
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
            .fillMaxHeight(CodeTheme.dimens.modalHeightRatio)
    ) {
        val lastItem by remember(navigator.lastModalItem) {
            derivedStateOf { navigator.lastModalItem }
        }

        val isBackEnabled by remember(backButton, lastItem) {
            derivedStateOf { backButton(lastItem) }
        }

        val isCloseEnabled by remember(closeButton, lastItem) {
            derivedStateOf { closeButton(lastItem) }
        }

        val keyboardVisible by keyboardAsState()
        val keyboardController = LocalSoftwareKeyboardController.current
        val composeScope = rememberCoroutineScope()

        val hideSheet = { callback: () -> Unit ->
            composeScope.launch {
                if (keyboardVisible) {
                    keyboardController?.hide()
                    delay(500)
                }
                callback()
            }
        }
        SheetTitle(
            modifier = Modifier,
            title = {
                val screenName = (lastItem as? NamedScreen)?.name
                val sheetName by remember(lastItem) {
                    derivedStateOf { screenName }
                }
                val name = title(lastItem) ?: sheetName
                name.takeIf { !displayLogo && lastItem == this@ModalContainer }
            },
            displayLogo = displayLogo,
            onLogoClicked = onLogoClicked,
            // hide while transitioning to/from other destinations
            backButton = isBackEnabled,
            closeButton = isCloseEnabled,
            onBackIconClicked = onBackClicked?.let { { it() } } ?: { hideSheet { navigator.pop() } },
            onCloseIconClicked = onCloseClicked?.let { { it() } } ?: { hideSheet { navigator.hide() } }
        )
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            val tlvm = MainRoot.getActivityScopedViewModel<TopLevelViewModel>()
            val state by tlvm.state.collectAsState()
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null,
                LocalBetaFlags provides state.betaFlags,
                LocalBuyModuleAvailable provides state.buyModuleAvailable
            ) {
                screenContent()
            }
        }
    }
}

internal interface ModalContent
internal sealed interface ModalRoot : ModalContent

data object MainRoot : Screen {
    override val key: ScreenKey = uniqueScreenKey
    private fun readResolve(): Any = this

    @Composable
    override fun Content() {
        // TODO: potentially add a loading state here
        //  so app doesn't appear stuck in a dead state
        //  while we wait for auth check to complete
        Box(modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background))
    }
}