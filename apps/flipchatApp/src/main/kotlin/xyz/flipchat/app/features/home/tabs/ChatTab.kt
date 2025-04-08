package xyz.flipchat.app.features.home.tabs

import android.os.Parcelable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import com.getcode.manager.BottomBarManager
import xyz.flipchat.app.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.core.NavigationLocator
import com.getcode.navigation.core.NavigatorStub
import com.getcode.navigation.core.NavigatorWrapper
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.navigation.screens.ChildNavTab
import com.getcode.theme.Black40
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.core.addIf
import com.getcode.ui.utils.getActivity
import com.getcode.ui.core.noRippleClickable
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.unboundedClickable
import com.getcode.util.resources.LocalResources
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.chat.list.ChatListViewModel
import xyz.flipchat.app.features.chat.openChatDirectiveBottomModal
import xyz.flipchat.app.features.settings.SettingsViewModel

@Parcelize
internal class ChatTab(override val ordinal: Int) : ChildNavTab, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override var childNav: NavigationLocator = NavigatorStub

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = ordinal.toUShort(),
            title = stringResource(R.string.title_chatsTab),
            icon = painterResource(R.drawable.ic_fc_chats)
        )

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current
        val resources = LocalResources.currentOrThrow
        val viewModel = getActivityScopedViewModel<ChatListViewModel>()
        val settingsVm = getViewModel<SettingsViewModel>()
        val state by viewModel.stateFlow.collectAsState()

        Box {
            Column {
                AppBarWithTitle(
                    title = {
                        LogOutTitle(
                            state = state,
                            onTitleClicked = {
                                viewModel.dispatchEvent(ChatListViewModel.Event.OnChatsTapped)
                            },
                            onLogout = {
                                context.getActivity()?.let {
                                    settingsVm.logout(it) {
                                        navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                                    }
                                }
                            }
                        )
                    },
                    rightContents = {
                        Image(
                            modifier = Modifier
                                .background(color = CodeTheme.colors.tertiary, shape = CircleShape)
                                .padding(CodeTheme.dimens.grid.x1)
                                .unboundedClickable {
                                    openChatDirectiveBottomModal(
                                        resources = resources,
                                        createCost = state.createRoomCost,
                                        viewModel = viewModel,
                                        navigator = navigator
                                    )
                                },
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground)
                        )
                    }
                )
                Navigator(ScreenRegistry.get(NavScreenProvider.Room.List)) { navigator ->
                    childNav = NavigatorWrapper(navigator)
                    SlideTransition(navigator)
                }
            }

            val scrimAlpha by animateFloatAsState(
                if (state.showScrim) 1f else 0f,
                label = "scrim visibility"
            )

            if (state.showScrim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Black40)
                        .rememberedClickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() }) {}
                ) {
                    if (state.showFullscreenSpinner) {
                        CodeCircularProgressIndicator(
                            modifier = Modifier
                                .size(50.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LogOutTitle(
        modifier: Modifier = Modifier,
        state: ChatListViewModel.State,
        onTitleClicked: () -> Unit,
        onLogout: () -> Unit
    ) {
        val context = LocalContext.current
        AppBarDefaults.Title(
            modifier = modifier
                .addIf(!state.isLogOutEnabled) {
                    Modifier.noRippleClickable {
                        onTitleClicked()
                    }
                }
                .addIf(state.isLogOutEnabled) {
                    Modifier.unboundedClickable {
                        BottomBarManager.showMessage(
                            BottomBarManager.BottomBarMessage(
                                title = context.getString(R.string.prompt_title_logout),
                                subtitle = context
                                    .getString(R.string.prompt_description_logout),
                                positiveText = context.getString(R.string.action_logout),
                                tertiaryText = context.getString(R.string.action_cancel),
                                onPositive = onLogout
                            )
                        )
                    }
                },
            text = options.title
        )
    }
}