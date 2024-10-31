package com.flipchat.features.home.tabs

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
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.flipchat.features.chat.list.ChatListViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.oct24.R
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.extensions.getActivityScopedViewModel
import com.getcode.oct24.features.chat.openChatDirectiveBottomModal
import com.getcode.theme.Black40
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
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
        val state by viewModel.stateFlow.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatListViewModel.Event.OpenRoom>()
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Conversation(it.room.id)))
                }.launchIn(this)
        }

        Box {
            Column {
                AppBarWithTitle(
                    title = options.title,
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

            val scrimAlpha by animateFloatAsState(if (state.showFullscreenSpinner) 1f else 0f, label = "scrim visibility")

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