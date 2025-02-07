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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
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
import com.getcode.ui.utils.withTopBorder
import dev.theolm.rinku.DeepLink
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

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
        OnLifecycleEvent { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    router.checkTabs()
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
                                        colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground),
                                        contentDescription = null,
                                    )

                                    Text(
                                        text = tab.options.title,
                                        style = CodeTheme.typography.textSmall,
                                        color = CodeTheme.colors.textMain,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}