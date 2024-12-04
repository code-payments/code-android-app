package xyz.flipchat.app.features.home

import android.os.Parcelable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.ui.components.OnLifecycleEvent
import com.getcode.ui.utils.getActivity
import com.getcode.ui.utils.withTopBorder
import dev.theolm.rinku.DeepLink
import dev.theolm.rinku.compose.ext.DeepLinkListener
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import xyz.flipchat.app.features.settings.SettingsViewModel
import xyz.flipchat.app.util.DeeplinkType
import kotlin.math.log

@Parcelize
class TabbedHomeScreen(private val deeplink: @RawValue DeepLink?) : Screen, Parcelable {
    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val viewModel = getActivityScopedViewModel<HomeViewModel>()
        val settingsViewModel = getViewModel<SettingsViewModel>()
        val router = viewModel.router

        val codeNavigator = LocalCodeNavigator.current

        //We are obtaining deep link here, in case we want to allow for some amount of deep linking when not
        //authenticated. Currently we will require authentication to see anything, but can be changed in future.
        var deepLink by remember(deeplink) { mutableStateOf(deeplink) }
        var loginRequest by remember { mutableStateOf<String?>(null) }

        DeepLinkListener {
            val type = router.processType(it)
            if (type is DeeplinkType.Login) {
                loginRequest = type.entropy
                return@DeepLinkListener
            }
            deepLink = it
        }

        LaunchedEffect(loginRequest) {
            loginRequest?.let { entropy ->
                viewModel.handleLoginEntropy(
                    entropy,
                    onSwitchAccounts = {
                        loginRequest = null
                        context.getActivity()?.let {
                            settingsViewModel.logout(it) {
                                codeNavigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home(entropy)))
                            }
                        }
                    },
                    onCancel = {
                        loginRequest = null
                    }
                )
            }
        }

        val initialTab = remember(deepLink) { router.getInitialTabIndex(deepLink) }

        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.openStream()
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
            LaunchedEffect(deepLink) {
                if (deepLink != null) {
                    val screenSet = router.processDestination(deepLink)
                    tabNavigator.current = router.rootTabs[initialTab]
                    codeNavigator.replaceAll(screenSet)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .withTopBorder()
                ) {
                    router.rootTabs.fastForEach { tab ->
                        val backgroundColor by animateColorAsState(
                            if (tabNavigator.current.options.index == tab.options.index) CodeTheme.colors.brandSubtle else CodeTheme.colors.surface,
                            label = "selected tab color"
                        )
                        Box(
                            modifier = Modifier
                                .background(backgroundColor)
                                .weight(1f)
                                .clickable { tabNavigator.current = tab }
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(
                                        top = CodeTheme.dimens.grid.x2,
                                        bottom = 0.dp
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    CodeTheme.dimens.grid.x1,
                                    Alignment.CenterVertically
                                )
                            ) {
                                Image(
                                    modifier = Modifier.size(CodeTheme.dimens.staticGrid.x6),
                                    painter = tab.options.icon!!,
                                    contentDescription = null,
                                )

                                Text(
                                    text = tab.options.title,
                                    style = CodeTheme.typography.textSmall,
                                    color = White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}