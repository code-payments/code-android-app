package xyz.flipchat.features.chat

import android.content.Context
import cafe.adriel.voyager.core.registry.ScreenRegistry
import xyz.flipchat.features.chat.list.ChatListViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigator
import com.getcode.oct24.R


fun openChatDirectiveBottomModal(
    context: Context,
    viewModel: ChatListViewModel,
    navigator: CodeNavigator,
) {
    BottomBarManager.showMessage(
        BottomBarManager.BottomBarMessage(
            positiveText = context.getString(R.string.action_joinRoom),
            negativeText = context.getString(R.string.action_createNewRoom),
            negativeStyle = BottomBarManager.BottomBarButtonStyle.Filled,
            tertiaryText = context.getString(R.string.action_cancel),
            onPositive = {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Chat.Lookup.Entry))
            },
            onNegative = {
                viewModel.dispatchEvent(ChatListViewModel.Event.CreateRoom)
            },
            type = BottomBarManager.BottomBarMessageType.THEMED,
            showScrim = true,
        )
    )
}