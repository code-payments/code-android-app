package xyz.flipchat.app.features.chat.info

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavScreenProvider
import xyz.flipchat.app.features.home.TabbedHomeScreen
import com.getcode.navigation.RoomInfoArgs
import com.getcode.navigation.core.LocalCodeNavigator
import xyz.flipchat.app.ui.room.RoomCard
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R

@Parcelize
class RoomInfoScreen(private val info: RoomInfoArgs) : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val viewModel = getViewModel<ChatInfoViewModel>()
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current

        LaunchedEffect(info) {
            viewModel.dispatchEvent(ChatInfoViewModel.Event.OnInfoChanged(info))
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
                .filterIsInstance<ChatInfoViewModel.Event.OnChangeCover>()
                .map { it.roomId }
                .onEach {
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.ChangeCover(it)))
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
                    navigator.push(ScreenRegistry.get(NavScreenProvider.Room.ChangeName(it.id, it.title)))
                }.launchIn(this)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                backButton = true,
                onBackIconClicked = { navigator.pop() },
                endContent = {
                    AppBarDefaults.Share { viewModel.dispatchEvent(ChatInfoViewModel.Event.OnShareRoomClicked) }
                }
            )
            RoomInfoScreenContent(viewModel)
        }
    }
}

@Composable
private fun RoomInfoScreenContent(viewModel: ChatInfoViewModel) {
    val state by viewModel.stateFlow.collectAsState()
    CodeScaffold(
        bottomBar = {
            if (state.roomNameChangesEnabled) {
                Actions(state = state, dispatch = viewModel::dispatchEvent)
            } else {
                LegacyActions(state = state, dispatch = viewModel::dispatchEvent)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = CodeTheme.dimens.grid.x12)
                .padding(bottom = CodeTheme.dimens.grid.x20)
        ) {
            RoomCard(
                modifier = Modifier.align(Alignment.Center),
                roomInfo = state.roomInfo
            )
        }
    }
}

@Composable
private fun LegacyActions(
    modifier: Modifier = Modifier,
    state: ChatInfoViewModel.State,
    dispatch: (ChatInfoViewModel.Event) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(bottom = CodeTheme.dimens.grid.x2)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        if (state.isHost) {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_changeCoverCharge),
            ) {
                dispatch(ChatInfoViewModel.Event.OnChangeCover(state.roomInfo.id!!))
            }
        }

        CodeButton(
            modifier = Modifier.fillMaxWidth(),
            buttonState = ButtonState.Filled,
            text = stringResource(R.string.action_leaveRoom),
            isLoading = state.requestBeingSent,
        ) {
            dispatch(ChatInfoViewModel.Event.LeaveRoom)
        }
    }
}

@Composable
private fun Actions(
    modifier: Modifier = Modifier,
    state: ChatInfoViewModel.State,
    dispatch: (ChatInfoViewModel.Event) -> Unit,
) {
    val context = LocalContext.current
    val composeScope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(bottom = CodeTheme.dimens.grid.x2)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
    ) {
        if (state.isHost) {
            CodeButton(
                modifier = Modifier.fillMaxWidth(),
                buttonState = ButtonState.Filled,
                text = stringResource(R.string.action_customize),
            ) {
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        positiveText = context.getString(R.string.action_changeRoomName),
                        negativeText = context.getString(R.string.action_changeCoverCharge),
                        negativeStyle = BottomBarManager.BottomBarButtonStyle.Filled,
                        tertiaryText = context.getString(R.string.action_cancel),
                        onPositive = {
                            composeScope.launch {
                                delay(300)
                                dispatch(ChatInfoViewModel.Event.OnChangeName(state.roomInfo.id!!, state.roomInfo.customTitle))
                            }
                        },
                        onNegative = {
                            composeScope.launch {
                                delay(300)
                                dispatch(ChatInfoViewModel.Event.OnChangeCover(state.roomInfo.id!!))
                            }
                        },
                        type = BottomBarManager.BottomBarMessageType.THEMED,
                        showScrim = true,
                    )
                )
            }
        }

        CodeButton(
            modifier = Modifier.fillMaxWidth(),
            buttonState = ButtonState.Subtle,
            text = stringResource(R.string.action_leaveRoom),
            isLoading = state.requestBeingSent,
        ) {
            dispatch(ChatInfoViewModel.Event.LeaveRoom)
        }
    }
}