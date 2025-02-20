package xyz.flipchat.app.features.chat.info

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.model.chat.MinimalMember
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.RoomInfoArgs
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.navigation.screens.ContextSheet
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.AvatarEndAction
import com.getcode.ui.components.chat.HostableAvatar
import com.getcode.ui.components.contextmenu.ContextMenuAction
import com.getcode.ui.components.user.social.MemberNameDisplay
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.noRippleClickable
import com.getcode.ui.utils.rememberedLongClickable
import com.getcode.ui.utils.unboundedClickable
import com.getcode.ui.utils.verticalScrollStateGradient
import com.getcode.utils.base58
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.home.TabbedHomeScreen

@Parcelize
class RoomInfoScreen(
    private val info: RoomInfoArgs,
    private val isPreview: Boolean,
    private val returnToSender: Boolean
) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getViewModel<ChatInfoViewModel>()
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current

        LaunchedEffect(info) {
            viewModel.dispatchEvent(ChatInfoViewModel.Event.OnInfoChanged(info, isPreview))
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatInfoViewModel.Event.OnLeftRoom>()
                .onEach {
                    navigator.popUntil { it is TabbedHomeScreen }
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatInfoViewModel.Event.OnBecameMember>()
                .map { it.roomId }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Messages(it)))
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatInfoViewModel.Event.OnChangeMessageFee>()
                .map { it.roomId }
                .onEach {
                    navigator.push(
                        ScreenRegistry.get(NavScreenProvider.Room.ChangeCover(it)),
                        delay = 100
                    )
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatInfoViewModel.Event.ShareRoom>()
                .onEach {
                    context.startActivity(it.intent)
                }.launchIn(this)
        }

        LaunchedEffect(viewModel) {
            viewModel.eventFlow
                .filterIsInstance<ChatInfoViewModel.Event.OnChangeName>()
                .onEach {
                    navigator.push(
                        ScreenRegistry.get(
                            NavScreenProvider.Room.ChangeName(
                                it.id,
                                it.title
                            )
                        )
                    )
                }.launchIn(this)
        }

        val state by viewModel.stateFlow.collectAsState()

        val goBack = {
            if (!returnToSender) {
                navigator.pop()
            } else {
                navigator.popUntil { it is TabbedHomeScreen }
            }
        }

        BackHandler {
            goBack()
        }

        val listState = rememberLazyGridState()

        val showTitle by remember(listState) {
            derivedStateOf { listState.firstVisibleItemIndex >= 1 }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = {
                    AnimatedVisibility(
                        visible = showTitle,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        AppBarDefaults.Title(
                            text = state.roomInfo.customTitle.ifEmpty { state.roomInfo.title },
                            style = CodeTheme.typography.screenTitle.copy(fontSize = 18.sp)
                        )
                    }
                },
                leftIcon = {
                    AppBarDefaults.UpNavigation { goBack() }
                },
                rightContents = {
                    if (state.isMember) {
                        AppBarDefaults.Settings {
                            navigator.show(
                                ContextSheet(
                                    buildActions(
                                        state,
                                        viewModel::dispatchEvent
                                    )
                                )
                            )
                        }
                    }
                }
            )
            RoomInfoScreenContent(listState, state, viewModel::dispatchEvent)
        }
    }
}

@Composable
private fun RoomInfoScreenContent(
    listState: LazyGridState,
    state: ChatInfoViewModel.State,
    dispatch: (ChatInfoViewModel.Event) -> Unit
) {
    val navigator = LocalCodeNavigator.current

    CodeScaffold(
        bottomBar = {
            if (!state.isMember) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .navigationBarsPadding(),
                    buttonState = ButtonState.Filled,
                    text = stringResource(R.string.action_startListening),
                ) {
                    dispatch(ChatInfoViewModel.Event.OnListenToClicked)
                }
            }
        }
    ) { padding ->
        val speakers = remember(state.members) {
            state.members.getOrDefault(MemberType.Speaker, emptyList())
        }

        val listeners = remember(state.members) {
            state.members.getOrDefault(MemberType.Listener, emptyList())
        }

        val viewUserProfile = { member: MinimalMember ->
            navigator.push(ScreenRegistry.get(NavScreenProvider.UserProfile(member.id!!)))
        }

        LazyVerticalGrid(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding()
                .verticalScrollStateGradient(listState, color = CodeTheme.colors.background),
            state = listState,
            columns = GridCells.Adaptive(CodeTheme.dimens.grid.x16),
            contentPadding = PaddingValues(
                top = CodeTheme.dimens.grid.x6,
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
                bottom = CodeTheme.dimens.inset,
            ),
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .unboundedClickable(
                            enabled = state.isHost
                        ) {
                            dispatch(
                                ChatInfoViewModel.Event.OnChangeName(
                                    state.roomInfo.id!!,
                                    state.roomInfo.customTitle
                                )
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    HostableAvatar(
                        size = CodeTheme.dimens.grid.x20,
                        imageData = state.roomInfo.imageUrl ?: state.roomInfo.id,
                        overlay = {
                            Image(
                                modifier = Modifier.size(CodeTheme.dimens.grid.x12),
                                painter = painterResource(R.drawable.ic_fc_chats),
                                colorFilter = ColorFilter.tint(Color.White),
                                contentDescription = null,
                            )
                        },
                        endAction = AvatarEndAction.Icon(
                            icon = rememberVectorPainter(Icons.Outlined.BorderColor),
                            contentColor = Color.White,
                            backgroundColor = CodeTheme.colors.indicator
                        ).takeIf { state.isHost },
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.roomInfo.customTitle.ifEmpty { state.roomInfo.title },
                            style = CodeTheme.typography.screenTitle,
                            color = CodeTheme.colors.textMain,
                            textAlign = TextAlign.Center
                        )
                        Crossfade(state.isOpen) { open ->
                            Text(
                                text = if (open) "" else stringResource(R.string.subtitle_roomInfoRoomIsClosed),
                                style = CodeTheme.typography.caption,
                                color = CodeTheme.colors.textSecondary.copy(0.54f),
                            )
                        }
                    }
                }
            }

            if (state.isMember) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CodeButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = CodeTheme.dimens.grid.x4),
                        buttonState = ButtonState.Filled,
                        text = if (state.isHost) {
                            stringResource(R.string.action_shareRoomLinkAsHost)
                        } else {
                            stringResource(R.string.action_shareRoomLinkAsMember)
                        },
                    ) {
                        dispatch(ChatInfoViewModel.Event.OnShareRoomClicked)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x3),
                    text = pluralStringResource(
                        R.plurals.title_roomInfoSpeakerCount,
                        speakers.count(),
                        speakers.count()
                    ),
                    style = CodeTheme.typography.screenTitle,
                    color = CodeTheme.colors.textMain
                )
            }

            items(speakers, key = { it.id?.base58.orEmpty() }) { member ->
                Column(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = if (state.isHost && !member.isSelf) { _ ->
                                    dispatch(ChatInfoViewModel.Event.DemoteRequested(member))
                                } else null,
                                onTap = if (state.canViewUserProfile) { _ ->
                                    viewUserProfile(member)
                                } else {
                                    null
                                }
                            )
                        }.animateItem(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    HostableAvatar(
                        size = CodeTheme.dimens.grid.x15,
                        isHost = member.isHost,
                        imageData = member.imageData,
                    ) {
                        Image(
                            modifier = Modifier.size(CodeTheme.dimens.grid.x8),
                            imageVector = Icons.Default.Person,
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = null,
                        )
                    }
                    MemberNameDisplay(member)
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x4),
                    text = pluralStringResource(
                        R.plurals.title_roomInfoListenerCount,
                        listeners.count(),
                        listeners.count()
                    ),
                    style = CodeTheme.typography.screenTitle,
                    color = CodeTheme.colors.textMain
                )
            }

            items(listeners, key = { it.id?.base58.orEmpty() }) { member ->
                Column(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = if (state.isHost && !member.isSelf) { _ ->
                                    dispatch(ChatInfoViewModel.Event.DemoteRequested(member))
                                } else null,
                                onTap = if (state.canViewUserProfile) { _ ->
                                    viewUserProfile(member)
                                } else {
                                    null
                                }
                            )
                        }.animateItem(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)
                ) {
                    HostableAvatar(
                        size = CodeTheme.dimens.grid.x15,
                        isHost = member.isHost,
                        imageData = member.imageData
                    ) {
                        Image(
                            modifier = Modifier.size(CodeTheme.dimens.grid.x8),
                            imageVector = Icons.Default.Person,
                            colorFilter = ColorFilter.tint(Color.White),
                            contentDescription = null,
                        )
                    }
                    MemberNameDisplay(member)
                }
            }
        }
    }
}

private fun buildActions(
    state: ChatInfoViewModel.State,
    dispatch: (ChatInfoViewModel.Event) -> Unit,
): List<ContextMenuAction> {
    return buildList {
        if (state.isHost) {
            add(
                RoomControlAction.MessageFee {
                    dispatch(ChatInfoViewModel.Event.OnChangeMessageFee(state.roomInfo.id!!))
                }
            )
            if (state.isOpen) {
                add(
                    RoomControlAction.CloseRoom {
                        dispatch(ChatInfoViewModel.Event.OnOpenStateChangedRequested)
                    }
                )
            } else {
                add(
                    RoomControlAction.OpenRoom {
                        dispatch(ChatInfoViewModel.Event.OnOpenStateChangedRequested)
                    }
                )
            }
        }

        add(
            RoomControlAction.LeaveRoom {
                dispatch(ChatInfoViewModel.Event.LeaveRoom)
            }
        )
    }
}