package xyz.flipchat.app.features.chat

import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.getcode.manager.BottomBarManager
import com.getcode.model.Currency
import com.getcode.model.Kin
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.CodeNavigator
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.Kin
import com.getcode.utils.formatAmountString
import xyz.flipchat.app.R
import xyz.flipchat.app.features.chat.list.ChatListViewModel


fun openChatDirectiveBottomModal(
    resources: ResourceHelper,
    createCost: Kin,
    viewModel: ChatListViewModel,
    navigator: CodeNavigator,
) {
    BottomBarManager.showMessage(
        BottomBarManager.BottomBarMessage(
            positiveText = resources.getString(R.string.action_enterRoomNumber),
            negativeText = resources.getString(
                R.string.action_createNewRoomWithCost,
                formatAmountString(
                    resources = resources,
                    currency = Currency.Kin,
                    amount = createCost.quarks.toDouble(),
                    suffix = resources.getKinSuffix()
                )
            ),
            negativeStyle = BottomBarManager.BottomBarButtonStyle.Filled,
            tertiaryText = resources.getString(R.string.action_cancel),
            onPositive = {
                navigator.push(ScreenRegistry.get(NavScreenProvider.Room.Lookup.Entry))
            },
            onNegative = {
                viewModel.dispatchEvent(ChatListViewModel.Event.CreateRoomSelected)
            },
            type = BottomBarManager.BottomBarMessageType.THEMED,
            showScrim = true,
        )
    )
}