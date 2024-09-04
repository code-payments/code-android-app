package com.getcode

import androidx.compose.material.ScaffoldState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.getcode.manager.BottomBarManager
import com.getcode.manager.ModalManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccessKeyLoginScreen
import com.getcode.navigation.screens.LoginPhoneVerificationScreen
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.NamedScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Remembers and creates an instance of [CodeAppState]
 */
@Composable
fun rememberCodeAppState(
    scaffoldState: ScaffoldState = rememberScaffoldState(),
    navigator: CodeNavigator = LocalCodeNavigator.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
) =
    remember(scaffoldState, navigator , coroutineScope) {
        CodeAppState(scaffoldState, navigator, coroutineScope)
    }

/**
 * Responsible for holding state related to [CodeApp] and containing UI-related logic.
 */
@Stable
class CodeAppState(
    val scaffoldState: ScaffoldState,
    var navigator: CodeNavigator,
    coroutineScope: CoroutineScope
) {
    init {
        coroutineScope.launch {
            TopBarManager.messages.collect { currentMessages ->
                topBarMessage.value = currentMessages.firstOrNull()
            }
        }
        coroutineScope.launch {
            BottomBarManager.messages.collect { currentMessages ->
                bottomBarMessage.value = currentMessages.firstOrNull()
            }
        }
        coroutineScope.launch {
            ModalManager.messages.collect { currentMessages ->
                modalMessage.value = currentMessages.firstOrNull()
            }
        }
    }
    // ----------------------------------------------------------
    // Navigation state source of truth
    // ----------------------------------------------------------

    val currentTitle: String
        @Composable get() {
            val lastItem = navigator.lastItem
            return (lastItem as? NamedScreen)?.name.orEmpty()
        }

    @Composable
    fun getScreen() = navigator.lastItem

    val isVisibleTopBar: Pair<Boolean, Boolean>
        @Composable get() {
            val screen = getScreen()
            val isModalVisible = navigator.isVisible
            val loginScreen = screen as? LoginScreen
            val isLoginScreen = loginScreen != null
            val isSeedInput = screen is AccessKeyLoginScreen
            val isPhoneEntry = screen is LoginPhoneVerificationScreen
            if (isModalVisible) {
                return false to false
            }
            return Pair(
                !isLoginScreen,
                isSeedInput || isPhoneEntry
            )
        }

    val topBarMessage = MutableStateFlow<TopBarManager.TopBarMessage?>(null)
    val bottomBarMessage = MutableStateFlow<BottomBarManager.BottomBarMessage?>(null)
    val modalMessage = MutableStateFlow<ModalManager.Message?>(null)

    fun upPress() {
        if (navigator.pop().not()) {
            navigator.hide()
        }
    }
}