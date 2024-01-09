package com.getcode

import androidx.compose.material.ScaffoldState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.MutableLiveData
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.AccessKeyScreen
import com.getcode.navigation.screens.LoginScreen
import com.getcode.navigation.screens.NamedScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

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
            val isAccessKeyScreen = screen is AccessKeyScreen

            return Pair(
                !isLoginScreen && !isModalVisible,
                !isAccessKeyScreen && loginScreen?.seed != null && !isModalVisible,
            )
        }

    val topBarMessage = MutableLiveData<TopBarManager.TopBarMessage?>()
    val bottomBarMessage = MutableLiveData<BottomBarManager.BottomBarMessage?>()


    fun upPress() {
        if (navigator.pop().not()) {
            navigator.hide()
        }
    }
}