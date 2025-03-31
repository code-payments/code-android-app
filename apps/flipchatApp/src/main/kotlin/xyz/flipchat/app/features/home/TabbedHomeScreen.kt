package xyz.flipchat.app.features.home

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.OnLifecycleEvent
import dev.theolm.rinku.DeepLink
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import xyz.flipchat.app.features.home.components.BottomBar

@Parcelize
class TabbedHomeScreen(private val deepLink: @RawValue DeepLink?) : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getActivityScopedViewModel<HomeViewModel>()
        val router = viewModel.router
        val isLoggedIn by viewModel.isLoggedIn.collectAsState()

        val initialTab = remember(deepLink) { router.getInitialTabIndex(deepLink) }

        val navigator = LocalCodeNavigator.current
        val composeScope = rememberCoroutineScope()
        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    composeScope.launch {
                        router.checkTabs()
                    }
                }
                else -> Unit
            }
        }

        TabNavigator(
            tab = viewModel.router.rootTabs[initialTab],
            tabDisposable = {
                TabDisposable(
                    navigator = it,
                    tabs = router.rootTabs,
                )
            }
        ) { tabNavigator ->
            DisposableEffect(tabNavigator) {
                navigator.tabsNavigator = tabNavigator

                onDispose {
                    navigator.tabsNavigator = null
                }
            }

            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .background(CodeTheme.colors.background)
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    CurrentTab()
                }
                if (isLoggedIn) {
                    BottomBar(tabNavigator, router.rootTabs)
                }
            }
        }
    }
}