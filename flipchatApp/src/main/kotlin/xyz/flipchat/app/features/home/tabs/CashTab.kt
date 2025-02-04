package xyz.flipchat.app.features.home.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigator
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.CodeNavigatorStub
import com.getcode.navigation.core.NavigationLocator
import com.getcode.navigation.core.NavigatorWrapper
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.getActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.flipchat.app.R
import xyz.flipchat.app.features.settings.SettingsViewModel

internal object CashTab : ChildNavTab {
    override val key: ScreenKey = uniqueScreenKey

    override val ordinal: Int = 1

    override var childNav: NavigationLocator = CodeNavigatorStub

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = ordinal.toUShort(),
            title = stringResource(R.string.title_cashTab),
            icon = painterResource(R.drawable.ic_fc_balance)
        )

    @Composable
    override fun Content() {
        val viewModel = getViewModel<SettingsViewModel>()
        val context = LocalContext.current
        val composeScope = rememberCoroutineScope()
        val navigator = LocalCodeNavigator.current
        Column {
            AppBarWithTitle(
                title = options.title,
            )
            CodeScaffold(
                bottomBar = {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .padding(bottom = CodeTheme.dimens.grid.x3),
                        buttonState = ButtonState.Subtle,
                        text = stringResource(R.string.action_deleteMyAccount)
                    ) {
                        BottomBarManager.showMessage(
                            BottomBarManager.BottomBarMessage(
                                title = context.getString(R.string.prompt_title_deleteAccount),
                                subtitle = context
                                    .getString(R.string.prompt_description_deleteAccount),
                                positiveText = context.getString(R.string.action_permanentlyDeleteAccount),
                                tertiaryText = context.getString(R.string.action_cancel),
                                onPositive = {
                                    composeScope.launch {
                                        delay(150)
                                        context.getActivity()?.let {
                                            viewModel.deleteAccount(it) {
                                                navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }
                },
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    Navigator(ScreenRegistry.get(NavScreenProvider.Balance)) { navigator ->
                        childNav = NavigatorWrapper(navigator)
                        SlideTransition(navigator)
                    }
                }
            }
        }
    }
}