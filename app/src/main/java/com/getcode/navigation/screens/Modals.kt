package com.getcode.navigation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.LocalBetaFlags
import com.getcode.MainRoot
import com.getcode.TopLevelViewModel
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.SheetTitle
import com.getcode.ui.components.SheetTitleDefaults
import com.getcode.ui.components.SheetTitleText
import com.getcode.ui.components.keyboardAsState
import com.getcode.ui.utils.getActivityScopedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface ModalHeightMetric {
    data class Weight(val weight: Float) : ModalHeightMetric
    data object WrapContent : ModalHeightMetric
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NamedScreen.ModalContainer(
    navigator: CodeNavigator = LocalCodeNavigator.current,
    modalColor: Color = CodeTheme.colors.background,
    modalHeightMetric: ModalHeightMetric = ModalHeightMetric.Weight(CodeTheme.dimens.modalHeightRatio),
    displayLogo: Boolean = false,
    titleString: @Composable (NamedScreen?) -> String? = { name },
    title: @Composable BoxScope.() -> Unit = { },
    backButton: @Composable () -> Unit = { SheetTitleDefaults.BackButton() },
    backButtonEnabled: (Screen?) -> Boolean = { false },
    onBackClicked: (() -> Unit)? = null,
    closeButton: @Composable () -> Unit = { SheetTitleDefaults.CloseButton() },
    closeButtonEnabled: (Screen?) -> Boolean = { false },
    onCloseClicked: (() -> Unit)? = null,
    onLogoClicked: () -> Unit = { },
    screenContent: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when (modalHeightMetric) {
                    is ModalHeightMetric.Weight -> Modifier.fillMaxHeight(modalHeightMetric.weight)
                    ModalHeightMetric.WrapContent -> Modifier.wrapContentHeight()
                }
            )
            .background(modalColor)
    ) {
        val lastItem by remember(navigator.lastModalItem) {
            derivedStateOf { navigator.lastModalItem }
        }

        val isBackEnabled by remember(backButtonEnabled, lastItem) {
            derivedStateOf { backButtonEnabled(lastItem) }
        }

        val isCloseEnabled by remember(closeButtonEnabled, lastItem) {
            derivedStateOf { closeButtonEnabled(lastItem) }
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
            color = modalColor,
            title = {
                titleString(this@ModalContainer)?.let {
                    SheetTitleText(text = it)
                } ?: title()
            },
            displayLogo = displayLogo,
            onLogoClicked = onLogoClicked,
            // hide while transitioning to/from other destinations
            backButton = backButton,
            closeButton = closeButton,
            backButtonEnabled = isBackEnabled,
            closeButtonEnabled = isCloseEnabled,
            onBackIconClicked = onBackClicked?.let { { it() } }
                ?: { hideSheet { navigator.pop() } },
            onCloseIconClicked = onCloseClicked?.let { { it() } }
                ?: { hideSheet { navigator.hide() } }
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
            ) {
                screenContent()
            }
        }
    }
}

internal interface ModalContent
internal sealed interface ModalRoot : ModalContent