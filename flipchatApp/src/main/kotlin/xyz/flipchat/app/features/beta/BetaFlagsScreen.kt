package xyz.flipchat.app.features.beta

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SettingsSwitchRow
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.ui.LocalLabs

@Parcelize
class BetaFlagsScreen : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppBarWithTitle(
                title = stringResource(R.string.title_betaFlags),
                backButton = true,
                onBackIconClicked = navigator::pop
            )
            BetaFlagsScreenContent()
        }
    }
}

@Composable
private fun BetaFlagsScreenContent() {
    val betaFlagsController = LocalLabs.current
    val betaFlags by betaFlagsController.observe().collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(betaFlags) { feature ->
            SettingsSwitchRow(
                title = feature.flag.title,
                subtitle = feature.flag.message,
                checked = feature.enabled
            ) {
                betaFlagsController.set(feature.flag, !feature.enabled)
            }
        }
        if (betaFlags.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "\uD83D\uDE2D",
                            style = CodeTheme.typography.displayMedium
                        )
                        Text(
                            text = "Nothing Cooking in the Lab Right Now",
                            style = CodeTheme.typography.textLarge,
                            color = CodeTheme.colors.textMain
                        )

                        Text(
                            text = "Check back in the next app update.",
                            style = CodeTheme.typography.textSmall,
                            color = CodeTheme.colors.textSecondary
                        )

                    }
                }
            }
        }
    }
}

private val Lab.title: String
    get() = when (this) {
        Lab.FollowerMode -> "Follower Mode"
        Lab.ReplyToMessage -> "Swipe To Reply"
        Lab.StartChatAtUnread -> "Open Conversation @ Last Unread"
        Lab.RoomNameChanges -> "Room Name Changes For Hosts"
        Lab.DeleteMessage -> "Delete Message Support"
        Lab.OpenCloseRoom -> "Open/Close Rooms"
    }

private val Lab.message: String
    get() = when (this) {
        Lab.FollowerMode -> "When enabled, you will gain the ability to watch rooms without joining first"
        Lab.ReplyToMessage -> "When enabled, you will gain the ability to swipe to reply to messages in chat"
        Lab.StartChatAtUnread -> "When enabled, conversations will resume at the last message you read"
        Lab.RoomNameChanges -> "When enabled, hosts will gain the ability to set a desired name for their room"
        Lab.DeleteMessage -> "When enabled, hosts will gain the ability to delete messages"
        Lab.OpenCloseRoom -> "When enabled, hosts will gain the ability to temporarily close (and reopen) their rooms"
    }