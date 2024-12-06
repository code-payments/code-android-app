package xyz.flipchat.app.features.beta

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SettingsSwitchRow
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.beta.BetaFlag
import xyz.flipchat.app.ui.LocalBetaFeatures

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
    val betaFlagsController = LocalBetaFeatures.current
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
    }
}

private val BetaFlag.title: String
    get() = when (this) {
        BetaFlag.FollowerMode -> "Follower Mode"
        BetaFlag.ReplyToMessage -> "Swipe To Reply"
    }

private val BetaFlag.message: String
    get() = when (this) {
        BetaFlag.FollowerMode -> "When enabled, joining a room will drop you in as a follower first"
        BetaFlag.ReplyToMessage -> "When enabled, you will gain the ability to swipe to reply to messages in chat"
    }