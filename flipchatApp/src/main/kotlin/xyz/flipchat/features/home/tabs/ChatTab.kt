package xyz.flipchat.features.home.tabs

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import xyz.flipchat.features.chat.list.ChatListViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.oct24.R
import xyz.flipchat.features.chat.openChatDirectiveBottomModal
import xyz.flipchat.features.settings.SettingsViewModel
import com.getcode.theme.Black40
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.addIf
import com.getcode.ui.utils.getActivity
import com.getcode.ui.utils.noRippleClickable
import com.getcode.ui.utils.rememberedClickable
import com.getcode.ui.utils.unboundedClickable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal object ChatTab : Tab {

    override val options: TabOptions
        @Composable get() = TabOptions(
            index = 1u,
            title = stringResource(R.string.title_chats),
            icon = painterResource(R.drawable.ic_fc_chats)
        )

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current
        val viewModel = getActivityScopedViewModel<ChatListViewModel>()
        val settingsVm = getViewModel<SettingsViewModel>()
        val state by viewModel.stateFlow.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatListViewModel.Event.OpenRoom>()
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(it.roomId)))
                }.launchIn(this)
        }

        Box {
            Column {
                AppBarWithTitle(
                    title = options.title,
                    startContent = {
                        HiddenLogoutButton(modifier = Modifier.padding(CodeTheme.dimens.grid.x1)) {
                            context.getActivity()?.let {
                                settingsVm.logout(it) {
                                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                                }
                            }
                        }
                    },
                    endContent = {
                        Image(
                            modifier = Modifier
                                .background(color = CodeTheme.colors.tertiary, shape = CircleShape)
                                .padding(CodeTheme.dimens.grid.x1)
                                .unboundedClickable {
                                    openChatDirectiveBottomModal(context, viewModel, navigator)
                                },
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(CodeTheme.colors.onBackground)
                        )
                    }
                )
                Navigator(ScreenRegistry.get(NavScreenProvider.Chat.List))
            }

            val scrimAlpha by animateFloatAsState(
                if (state.showFullscreenSpinner) 1f else 0f,
                label = "scrim visibility"
            )

            if (state.showFullscreenSpinner) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(scrimAlpha)
                        .background(Black40)
                        .rememberedClickable(indication = null,
                            interactionSource = remember { MutableInteractionSource() }) {}
                ) {
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
private fun HiddenLogoutButton(modifier: Modifier = Modifier, onLogout: () -> Unit) {
    val context = LocalContext.current
    var debugClickCount by remember { mutableIntStateOf(0) }
    var enabledDebug by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(debugClickCount) {
        if (debugClickCount >= 7) {
            enabledDebug = true
        }
    }

    Box(modifier = modifier
        .size(CodeTheme.dimens.staticGrid.x5)
        .addIf(!enabledDebug) {
            Modifier.noRippleClickable {
                debugClickCount++
            }
        }
        .addIf(enabledDebug) {
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
        }
    )
}