package com.getcode.oct24

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.ScreenTransition
import cafe.adriel.voyager.transitions.ScreenTransitionContent
import cafe.adriel.voyager.transitions.SlideTransition
import com.flipchat.features.payments.PaymentScaffold
import com.getcode.oct24.theme.FlipchatTheme
import com.getcode.navigation.core.BottomSheetNavigator
import com.getcode.navigation.core.CombinedNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.transitions.SheetSlideTransition
import com.getcode.oct24.ui.navigation.AppScreenContent
import com.getcode.oct24.ui.navigation.MainRoot
import com.getcode.theme.LocalCodeColors
import com.getcode.ui.components.bars.BottomBarContainer
import com.getcode.ui.components.bars.TopBarContainer
import com.getcode.ui.components.bars.rememberBarManager
import com.getcode.ui.decor.ScrimSupport
import com.getcode.ui.theme.CodeScaffold
import dev.bmcreations.tipkit.TipScaffold
import dev.bmcreations.tipkit.engines.TipsEngine

@Composable
fun App(
    tipsEngine: TipsEngine,
) {
    FlipchatTheme {
        AppScreenContent {
            val barManager = rememberBarManager()
            AppNavHost {
                val codeNavigator = LocalCodeNavigator.current
                TipScaffold(tipsEngine = tipsEngine) {
                    ScrimSupport {
                        CodeScaffold { innerPaddingModifier ->
                            PaymentScaffold {
                                Navigator(
                                    screen = MainRoot,
                                ) { navigator ->
                                    LaunchedEffect(navigator.lastItem) {
                                        // update global navigator for platform access to support push/pop from a single
                                        // navigator current
                                        codeNavigator.screensNavigator = navigator
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(innerPaddingModifier)
                                    ) {
                                        SlideTransition(navigator)
                                    }
                                }
                            }
                        }
                    }
                }
                TopBarContainer(barManager.barMessages)
                BottomBarContainer(barManager.barMessages)
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AppNavHost(content: @Composable () -> Unit) {
    var combinedNavigator by remember {
        mutableStateOf<CombinedNavigator?>(null)
    }
    BottomSheetNavigator(
        modifier = Modifier.fillMaxSize(),
        sheetBackgroundColor = LocalCodeColors.current.background,
        sheetContentColor = LocalCodeColors.current.onBackground,
        sheetContent = { sheetNav ->
            combinedNavigator = combinedNavigator?.apply { sheetNavigator = sheetNav }
                ?: CombinedNavigator(sheetNav)
            combinedNavigator?.let {
                CompositionLocalProvider(LocalCodeNavigator provides it) {
                    SheetSlideTransition(navigator = it)
                }
            }

        },
        onHide = com.getcode.services.manager.ModalManager::clear
    ) { sheetNav ->
        combinedNavigator =
            combinedNavigator?.apply { sheetNavigator = sheetNav } ?: CombinedNavigator(sheetNav)
        combinedNavigator?.let {
            CompositionLocalProvider(LocalCodeNavigator provides it) {
                content()
            }
        }
    }
}

@Composable
private fun CrossfadeTransition(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: ScreenTransitionContent = { it.Content() }
) {
    ScreenTransition(
        navigator = navigator,
        modifier = modifier,
        content = content,
        transition = { fadeIn() togetherWith fadeOut() }
    )
}