package xyz.flipchat.app.features.profile

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.services.user.social.SocialProfile

@Parcelize
class ProfileScreen : Screen, Parcelable {
    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current

        val viewModel = getViewModel<ProfileViewModel>()
        val state by viewModel.stateFlow.collectAsState()
        Column {
            AppBarWithTitle(
                endContent = {
                    if (state.isStaff) {
                        AppBarDefaults.Settings {
                            navigator.push(ScreenRegistry.get(NavScreenProvider.Settings))
                        }
                    }
                }
            )
            ProfileContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = state,
                dispatchEvent = viewModel::dispatchEvent
            )
        }
    }
}

@Composable
private fun ProfileContent(
    modifier: Modifier = Modifier,
    state: ProfileViewModel.State,
    dispatchEvent: (ProfileViewModel.Event) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UserAvatar(
            modifier = Modifier
                .padding(
                    top = CodeTheme.dimens.grid.x7,
                    bottom = CodeTheme.dimens.grid.x4
                )
                .size(120.dp)
                .clip(CircleShape),
            data = state.imageUrl ?: state.id,
            overlay = {
                Image(
                    modifier = Modifier.size(60.dp),
                    imageVector = Icons.Default.Person,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            }
        )

        if (state.linkedSocialProfile == null) {
            Text(
                text = state.displayName,
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain
            )
            if (state.canConnectAccount) {
                CodeButton(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = CodeTheme.dimens.grid.x12)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    buttonState = ButtonState.Filled,
                    onClick = { dispatchEvent(ProfileViewModel.Event.LinkXAccount) },
                    content = {
                        Image(
                            painter = rememberVectorPainter(image = ImageVector.vectorResource(id = R.drawable.ic_twitter_x)),
                            colorFilter = ColorFilter.tint(Color.Black),
                            contentDescription = null
                        )
                        Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
                        Text(
                            text = stringResource(R.string.action_connectYourAccount),
                        )
                    }
                )
            }
        } else {
            when (state.linkedSocialProfile) {
                SocialProfile.Unknown -> Unit
                is SocialProfile.X -> {

                }
            }
        }
    }
}